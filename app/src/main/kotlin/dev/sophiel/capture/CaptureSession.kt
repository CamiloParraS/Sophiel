package dev.sophiel.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import dev.sophiel.core.DetectorFactory
import dev.sophiel.core.Preset
import dev.sophiel.feed.NudeNet
import dev.sophiel.feed.SpikeModel
import dev.sophiel.feed.unsafeScore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

private const val TAG = "Sophiel"
private const val FRAME_INTERVAL_MS = 80L
private const val MIN_CAPTURE_SHORT_SIDE = 360f
private const val BLACK_PROBE_SIZE = 64

/**
 * Everything that exists only while capturing (SPEC.md §4.4 RUNNING): the [VirtualDisplay][android.hardware.display.VirtualDisplay],
 * its [FrameSource], the detector, frame backpressure, and the debug pill. Created once the
 * projection is acquired; [close] releases all of it, including the projection.
 */
class CaptureSession(
    context: Context,
    private val projection: MediaProjection,
    private var size: CaptureSize,
    private val onStatus: (String) -> Unit,
    private val liveModel: () -> SpikeModel,
) {
    private val appContext = context.applicationContext

    // Spike (D24): swapped between frames when the picked model changes. Only touched from the
    // single in-flight frame job, then from close() once that job has finished.
    @Volatile
    private var judge: Judge? = null
    private val throttle = FrameThrottle(FRAME_INTERVAL_MS)
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val debugPill = if (context.isDebuggable) DebugPillOverlay(context).also { it.show() } else null
    private val debugBoxes = if (context.isDebuggable) DebugBoxOverlay(context).also { it.show() } else null

    // Frame being analysed, if any. New frames are dropped while it runs (SPEC.md §4.5): the
    // throttle alone let frames queue on the single inference thread whenever analysis took
    // longer than 80 ms, so latency and held bitmaps grew without bound.
    @Volatile
    private var inFlight: Job? = null
    @Volatile
    private var closed = false

    private var frameSource = FrameSource(size.width, size.height, size.crop, ::wantsFrame, ::onFrame)
    private val display = checkNotNull(
        projection.createVirtualDisplay(
            "SophielCapture",
            size.width, size.height, size.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            frameSource.surface, null, null,
        ),
    ) { "createVirtualDisplay() returned null" }

    /** Rotation must `resize()` + `setSurface()`, never recreate the display (SPEC.md §4.2). */
    fun resize(newSize: CaptureSize) {
        if (newSize == size) return // theme/locale/font changes also land here; nothing to do
        val newSource = FrameSource(newSize.width, newSize.height, newSize.crop, ::wantsFrame, ::onFrame)
        display.resize(newSize.width, newSize.height, newSize.densityDpi)
        display.surface = newSource.surface
        frameSource.close()
        frameSource = newSource
        size = newSize
    }

    /** Called on the FrameSource thread before an Image is decoded; false drops it undecoded. */
    private fun wantsFrame(): Boolean =
        !closed && inFlight?.isActive != true && throttle.shouldProcess(SystemClock.elapsedRealtime())

    private fun onFrame(bitmap: Bitmap) {
        inFlight = scope.launch {
            try {
                val status = if (isProtected(bitmap)) {
                    Log.d(TAG, "protected content (mostly-black frame, likely FLAG_SECURE)")
                    debugBoxes?.update(emptyList())
                    "Protected content — not analyzable"
                } else {
                    val want = liveModel()
                    val current = judge?.takeIf { it.model == want } ?: run {
                        judge?.close?.invoke()
                        debugBoxes?.update(emptyList())
                        debugPill?.update("${want.label}: loading…")
                        openJudge(want).also { judge = it }
                    }
                    current.run(bitmap)
                }
                onStatus(status)
                debugPill?.update(status)
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun close() {
        closed = true
        debugPill?.hide()
        debugBoxes?.hide()
        display.release()
        frameSource.close()
        projection.stop()
        scope.cancel()
        // Cancel doesn't interrupt a blocking inference or model load: wait it out off the main
        // thread, then close whichever judge it left behind.
        val last = inFlight
        thread(name = "DetectorClose") {
            runBlocking { last?.join() }
            judge?.close?.invoke()
        }
    }

    /** Frame-normalised box → fraction of the whole display (the frame is the capture's [CaptureSize.crop]). */
    private fun toDisplayFraction(box: RectF): RectF {
        val (width, height, _, crop) = size
        return RectF(
            (crop.left + box.left * crop.width()) / width, (crop.top + box.top * crop.height()) / height,
            (crop.left + box.right * crop.width()) / width, (crop.top + box.bottom * crop.height()) / height,
        )
    }

    private class Judge(val model: SpikeModel, val run: suspend (Bitmap) -> String, val close: () -> Unit)

    private fun openJudge(model: SpikeModel): Judge = when (model.asset) {
        null -> DetectorFactory.create(appContext).let { detector ->
            Judge(model, { bitmap ->
                val verdict = detector.analyze(bitmap, Preset.LIGHT).single()
                Log.d(
                    TAG,
                    "severity=${verdict.severity} score=${verdict.score} gated=${verdict.gated} " +
                        "cached=${verdict.cacheHit} latencyMs=${verdict.latencyMs}",
                )
                // cached= is here to be counted in M5: if the hit rate is ~0 the cache is dead
                // weight (MediaProjection delivers no frames at all for an unchanging screen,
                // DECISIONS.md D17) and VerdictCache should go.
                "%s · %s · score=%.2f · gated=%b · cached=%b · %dms".format(
                    model.label, verdict.severity, verdict.score, verdict.gated, verdict.cacheHit, verdict.latencyMs,
                )
            }, detector::close)
        }
        else -> NudeNet.load(appContext, model.asset, model.inputSize).let { nudeNet ->
            Judge(model, { bitmap ->
                val start = SystemClock.elapsedRealtime()
                val detections = nudeNet.detect(bitmap)
                val ms = SystemClock.elapsedRealtime() - start
                debugBoxes?.update(detections.map { it.copy(box = toDisplayFraction(it.box)) })
                Log.d(TAG, "live model=${model.name} score=${detections.unsafeScore()} latencyMs=$ms")
                "%s · score=%.2f · %dms".format(model.label, detections.unsafeScore(), ms) +
                    detections.take(3).joinToString("") { "\n%s %.2f".format(it.label.lowercase(), it.score) }
            }, nudeNet::close)
        }
    }
}

/** FLAG_SECURE check on a nearest-neighbour probe, not a full-size pixel copy per frame. */
private fun isProtected(frame: Bitmap): Boolean {
    val probe = Bitmap.createScaledBitmap(frame, BLACK_PROBE_SIZE, BLACK_PROBE_SIZE, false)
    val pixels = IntArray(BLACK_PROBE_SIZE * BLACK_PROBE_SIZE)
    probe.getPixels(pixels, 0, BLACK_PROBE_SIZE, 0, 0, BLACK_PROBE_SIZE, BLACK_PROBE_SIZE)
    if (probe !== frame) probe.recycle()
    return BlackFrameDetector.isMostlyBlack(pixels)
}

data class CaptureSize(val width: Int, val height: Int, val densityDpi: Int, val crop: Rect)

/** Downscaled capture size (SPEC.md §4.6), with the system-bar crop in capture coordinates. */
fun captureSize(context: Context): CaptureSize {
    val windowManager = context.getSystemService(WindowManager::class.java)
    val metrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    windowManager.defaultDisplay.getRealMetrics(metrics)
    val scale = MIN_CAPTURE_SHORT_SIDE / minOf(metrics.widthPixels, metrics.heightPixels)
    val width = (metrics.widthPixels * scale).toInt() and 0xFFFFFFFE.toInt()
    val height = (metrics.heightPixels * scale).toInt() and 0xFFFFFFFE.toInt()

    // Crop status/navigation bars and the cutout (SPEC.md §4.6): never content, always in
    // the frame. "Ignoring visibility" keeps the crop stable when an app hides the bars,
    // so the dHash cache isn't invalidated by a fullscreen toggle.
    val crop = Rect(0, 0, width, height)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bars = windowManager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        crop.set(
            (bars.left * scale).toInt(), (bars.top * scale).toInt(),
            width - (bars.right * scale).toInt(), height - (bars.bottom * scale).toInt(),
        )
    } // ponytail: no bar crop below API 30 (no insets API from a Service); both project devices are 33+.
    Log.d(TAG, "capture ${width}x$height crop=$crop")
    return CaptureSize(width, height, metrics.densityDpi, crop)
}
