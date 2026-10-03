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
import dev.sophiel.AppContainer
import dev.sophiel.core.Detector
import dev.sophiel.core.DetectorFactory
import dev.sophiel.core.Preset
import dev.sophiel.core.TileVerdict
import dev.sophiel.core.tile.TileMaskTracker
import dev.sophiel.core.tile.TileState
import dev.sophiel.core.tile.TileState.CLEAR
import dev.sophiel.core.tile.TileState.MASKED
import dev.sophiel.core.tile.TileState.PROBING
import dev.sophiel.core.grid
import dev.sophiel.core.tileRect
import dev.sophiel.feed.NudeNet
import dev.sophiel.feed.SpikeModel
import dev.sophiel.feed.unsafeScore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    private val settings: AppContainer,
) {
    private val appContext = context.applicationContext

    // Spike (D24): swapped between frames when the picked model changes. Only touched from the
    // single in-flight frame job, then from close() once that job has finished.
    @Volatile
    private var judge: Judge? = null
    private val throttle = FrameThrottle(FRAME_INTERVAL_MS)
    // One lane: frame jobs and probe timeouts both touch the tile tracker, which is not thread-safe.
    private val scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + Job())
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

    // When the in-flight frame left the ImageReader; for ticket 07's frame-to-mask latency.
    @Volatile
    private var frameAvailableAt = 0L

    private fun onFrame(bitmap: Bitmap, availableAtMs: Long) {
        frameAvailableAt = availableAtMs
        inFlight = scope.launch {
            try {
                val status = if (isProtected(bitmap)) {
                    Log.d(TAG, "protected content (mostly-black frame, likely FLAG_SECURE)")
                    debugBoxes?.update(emptyList())
                    "Protected content — not analyzable"
                } else {
                    val want = settings.liveModel
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

    /**
     * Ticket 06: drives a [TileMaskTracker] from live frames and draws its masks on [debugBoxes].
     * Masked tiles are not analysed; CLEAR and PROBING ones are, each applied as it arrives.
     * Runs only on [scope]'s single lane, which keeps the tracker single-threaded.
     */
    private inner class TileLoop(private val detector: Detector) {
        private val tracker = TileMaskTracker(0, 0)
        private var grid = Triple(Preset.LIGHT, 0, 0) // preset, frame width, frame height
        private var shown: List<TileState> = emptyList()
        private val probedAt = HashMap<Int, Long>()
        private var open = true // a probe-timeout job can outlive a model switch

        suspend fun run(frame: Bitmap): String {
            val preset = settings.livePreset
            if (grid != Triple(preset, frame.width, frame.height)) { // rotation or preset change
                grid = Triple(preset, frame.width, frame.height)
                val (cols, rows) = preset.grid(frame.width, frame.height)
                tracker.reset(cols, rows)
                publish()
            }
            // Probing tiles first: the mask is off until their verdict lands, so every tile judged
            // ahead of them is exposure (D26: ~38 ms per tile on B, ~55 on A). If a probe frame
            // still shows the mask, skip the CLEAR tiles: they wait one frame, and the next (valid)
            // probe frame arrives ~165 ms sooner on Device A instead of after the 300 ms cap.
            val probes = (0 until tracker.size).filter { tracker[it] == PROBING }
            val clears = (0 until tracker.size).filter { tracker[it] == CLEAR }
            var probeStillMasked = false
            detector.analyze(frame, preset, probes).collect { v ->
                val pixels = DebugMask.sample(frame, preset.tileRect(v.index, frame.width, frame.height))
                val showsMask = DebugMask.looksMasked(pixels)
                probeStillMasked = probeStillMasked || showsMask
                Log.d(TAG, "probe frame tile=${v.index} showsMask=$showsMask ${DebugMask.describe(pixels)}")
                apply(v, showsMask)
            }
            if (!probeStillMasked) detector.analyze(frame, preset, clears).collect { apply(it, showsMask = false) }
            tracker.endFrame(now())
            publish()
            if (PROBING in shown) {
                scope.launch { // a screen that goes static once the mask is lifted sends no frame
                    delay(tracker.probeTimeoutMs)
                    tracker.expireProbes(now())
                    publish()
                }
            }
            // No latency here (it is in Logcat): the pill is captured too, and text that changes
            // every frame redraws it, which makes a static screen deliver frames forever.
            return "GantMan · $preset · ${shown.joinToString(" ") { it.name.take(1) }} · " +
                "${probes.size + if (probeStillMasked) 0 else clears.size} analysed"
        }

        private fun apply(v: TileVerdict, showsMask: Boolean) {
            if (tracker.onTile(now(), v, showsMask)) {
                Log.i(TAG, "mask episode tile=${v.index} frameToMaskMs=${now() - frameAvailableAt}")
            }
            publish()
            // cached= is here to be counted in M5: if the hit rate is ~0 the cache is dead
            // weight (MediaProjection delivers no frames at all for an unchanging screen,
            // DECISIONS.md D17) and VerdictCache should go.
            Log.d(
                TAG,
                "tile ${v.index} severity=${v.severity} score=${v.score} gated=${v.gated} " +
                    "cached=${v.cacheHit} showsMask=$showsMask latencyMs=${v.latencyMs}",
            )
        }

        fun close() {
            open = false
            debugBoxes?.updateMasks(emptyList())
            detector.close()
        }

        /** Logs state changes and probe exposure, then redraws the masks if anything moved. */
        private fun publish() {
            if (!open) return
            val states = List(tracker.size) { tracker[it] }
            if (states == shown) return
            val now = now()
            states.forEachIndexed { i, state ->
                val was = shown.getOrNull(i)
                if (state == was) return@forEachIndexed
                if (state == PROBING) probedAt[i] = now
                if (was == PROBING) {
                    probedAt.remove(i)?.let { Log.i(TAG, "probe tile=$i exposureMs=${now - it} -> $state") }
                }
                Log.i(TAG, "tile $i $was -> $state")
            }
            shown = states
            val (preset, w, h) = grid
            debugBoxes?.updateMasks(
                states.indices.filter { states[it] == MASKED }.map { i ->
                    val r = preset.tileRect(i, w, h)
                    toDisplayFraction(RectF(r.left / w.toFloat(), r.top / h.toFloat(), r.right / w.toFloat(), r.bottom / h.toFloat()))
                },
            )
        }

        private fun now() = SystemClock.elapsedRealtime()
    }

    private class Judge(val model: SpikeModel, val run: suspend (Bitmap) -> String, val close: () -> Unit)

    private fun openJudge(model: SpikeModel): Judge = when (model.asset) {
        null -> TileLoop(DetectorFactory.create(appContext)).let { Judge(model, it::run, it::close) }
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
