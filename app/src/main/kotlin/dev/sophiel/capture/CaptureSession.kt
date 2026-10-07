package dev.sophiel.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import dev.sophiel.core.tile.TileState.PEEKING
import dev.sophiel.core.tile.TileState.PROBING
import dev.sophiel.core.grid
import dev.sophiel.core.tileRect
import dev.sophiel.feed.Detection
import dev.sophiel.feed.NudeNet
import dev.sophiel.feed.SpikeModel
import dev.sophiel.feed.unsafeScore
import dev.sophiel.log.Episodes
import dev.sophiel.log.Stretches
import dev.sophiel.settings.ParentPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread

private const val TAG = "Sophiel"
private const val FRAME_INTERVAL_MS = 80L
private const val MIN_CAPTURE_SHORT_SIDE = 360f
private const val BLACK_PROBE_SIZE = 64
private const val COVER_WAIT_MS = 1_000L // ticket 08: longest a cover waits to be seen
private const val SHOT_INTERVAL_MS = 400L // ticket 16: one shot per 333 ms, timed by the system: 342 ms apart still failed on B
private const val SHOT_TIMEOUT_MS = 500L // ticket 16: worst success 132 ms on B; failures took ~2 s
private const val BOX_MASK_SCORE = 0.3f // ticket 17 calibration knob: NudeNet box score that masks
private const val BOX_PAD = 0.1f // ticket 17 calibration knob: mask margin per side, fraction of the box
private const val HEARTBEAT_MS = 60_000L // D39: last-alive time for the inferred-gap OFF
private const val BOX_HOLD_MS = 2_000L // ticket 17: longest masks are held through failed shots

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
    private val container: AppContainer,
) {
    private val appContext = context.applicationContext
    private val settings get() = container.settings.value // D40: read per frame

    // Spike (D24): swapped between frames when the picked model changes. Only touched from the
    // single in-flight frame job, then from close() once that job has finished.
    @Volatile
    private var judge: Judge? = null
    private val throttle = FrameThrottle(FRAME_INTERVAL_MS)
    // One lane: frame jobs and probe timeouts both touch the tile tracker, which is not thread-safe.
    private val scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + Job())
    private val debugPill = if (context.isDebuggable) DebugPillOverlay(context).also { it.show() } else null

    /** The debug pill follows its switch (debug menu) live: shown with text while on, removed while off. */
    private fun pill(text: String) {
        val p = debugPill ?: return
        if (container.debugPill) { p.show(); p.update(text) } else p.hide()
    }

    private val overlay = OverlayController(context) { settings.showLabel }.also { it.show() }

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

    // D39: one per session, so a session always starts a new episode. Both used on the scope lane (and close()).
    private val episodes = Episodes()
    private val stretches = Stretches()

    /** D39: [anyMasked] is the tracker / box state (probing and peeking count), not what is drawn. */
    private fun reportMasked(anyMasked: Boolean, preset: String, count: Int, score: Float) {
        if (episodes.update(anyMasked, SystemClock.elapsedRealtime())) container.log.masked(preset, count, score)
    }

    private fun logStretch(done: Stretches.Done?) {
        if (done != null) container.log.unanalyzable(System.currentTimeMillis() - done.startedAgoMs, done.seconds)
    }

    // Written by TileLoop's publish (scope lane), read by resize (main thread).
    @Volatile
    private var masking = false

    /** Rotation must `resize()` + `setSurface()`, never recreate the display (SPEC.md §4.2). */
    fun resize(newSize: CaptureSize) {
        if (newSize == size) return // theme/locale/font changes also land here; nothing to do
        val newSource = FrameSource(newSize.width, newSize.height, newSize.crop, ::wantsFrame, ::onFrame)
        display.resize(newSize.width, newSize.height, newSize.densityDpi)
        display.surface = newSource.surface
        frameSource.close()
        frameSource = newSource
        size = newSize
        // Ticket 08: the old grid's masks no longer sit on the content they hid. Cover the whole
        // content area until TileLoop resets onto the new grid (its first new-size frame).
        if (masking) coverScreen()
    }

    /**
     * Called on the FrameSource thread before an Image is decoded ([held]: a kept frame offered again).
     * A frame that can't run yet is dropped, unless a probe waits: then it is held, not lost. On a still
     * screen the lifted mask brings one or two frames; dropping them timed out ~2/3 of timed-out probes
     * (2026-10-07, both devices). Held frames still keep to one frame per [FRAME_INTERVAL_MS].
     */
    private fun wantsFrame(held: Boolean): Want {
        if (closed || container.ownScreens.showing.value) return Want.SKIP
        val now = SystemClock.elapsedRealtime()
        val busy = inFlight?.isActive == true
        val since = now - (throttle.lastProcessedMs ?: now)
        val want = when {
            !busy && throttle.shouldProcess(now) -> Want.TAKE
            probing -> Want.HOLD
            else -> Want.SKIP
        }
        if (probing && (!held || want == Want.TAKE)) {
            Log.d(TAG, "probe wait: ${if (held) "held " else ""}frame ${want.name.lowercase()}${if (busy) " (busy lane)" else ""} sinceLastMs=$since")
        }
        return want
    }

    // Written by TileLoop's publish (scope lane), read by wantsFrame (FrameSource thread).
    @Volatile
    private var probing = false

    // Ticket 09: on the lane, so the tracker is only touched there. Frames are dropped meanwhile (wantsFrame).
    init {
        scope.launch {
            while (true) {
                container.log.heartbeat()
                delay(HEARTBEAT_MS)
            }
        }
        scope.launch {
            container.ownScreens.showing.collect { own ->
                overlay.setHidden(own)
                judge?.ownScreen?.invoke(own)
            }
        }
    }

    // When the in-flight frame left the ImageReader; for ticket 07's frame-to-mask latency.
    @Volatile
    private var frameAvailableAt = 0L

    private fun onFrame(bitmap: Bitmap, availableAtMs: Long) {
        frameAvailableAt = availableAtMs
        inFlight = scope.launch {
            try {
                val protectedFrame = isProtected(bitmap)
                logStretch(stretches.frame(protectedFrame, SystemClock.elapsedRealtime()))
                val status = if (protectedFrame) {
                    Log.d(TAG, "protected content (mostly-black frame, likely FLAG_SECURE)")
                    overlay.updateBoxes(emptyList())
                    judge?.protectedFrame?.invoke()
                    "Protected content — not analyzable"
                } else {
                    val want = wantedModel()
                    val current = judge?.takeIf { it.model == want } ?: run {
                        // D40: closing a judge clears its masks. Anything masked: cover the screen
                        // first, and the new judge starts under it and takes it down.
                        val covered = judge?.masked?.invoke() == true
                        if (covered) coverScreen() else overlay.uncover()
                        judge?.close?.invoke()
                        overlay.updateBoxes(emptyList())
                        pill("${want.label}: loading…")
                        openJudge(want, covered).also { judge = it }
                    }
                    current.run(bitmap)
                }
                onStatus(status)
                pill(status)
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun close() {
        closed = true
        logStretch(stretches.end(SystemClock.elapsedRealtime()))
        debugPill?.hide()
        overlay.hide()
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

    private fun coverScreen() = overlay.cover(toDisplayFraction(RectF(0f, 0f, 1f, 1f)))

    /** Frame-normalised box → fraction of the whole display (the frame is the capture's [CaptureSize.crop]). */
    private fun toDisplayFraction(box: RectF): RectF {
        val (width, height, _, crop) = size
        return RectF(
            (crop.left + box.left * crop.width()) / width, (crop.top + box.top * crop.height()) / height,
            (crop.left + box.right * crop.width()) / width, (crop.top + box.bottom * crop.height()) / height,
        )
    }

    /** Frame pixel → the screen pixel it was captured from (pixel centres), for the mask check (D30). */
    private fun frameToScreen(): (Int, Int) -> Pair<Int, Int> {
        val real = realMetrics()
        val (width, height, _, crop) = size
        return { x, y ->
            ((crop.left + x + 0.5f) * real.widthPixels / width).toInt() to
                ((crop.top + y + 0.5f) * real.heightPixels / height).toInt()
        }
    }

    /** Ticket 16: [shot] of a window at [bounds] (screen px) drawn where it sits in a [w]×[h] capture frame. */
    private fun shotToFrame(shot: Bitmap, bounds: Rect, w: Int, h: Int): Bitmap {
        val real = realMetrics()
        val (width, height, _, crop) = size
        val sx = width.toFloat() / real.widthPixels
        val sy = height.toFloat() / real.heightPixels
        val at = RectF(bounds.left * sx - crop.left, bounds.top * sy - crop.top, bounds.right * sx - crop.left, bounds.bottom * sy - crop.top)
        // Outside the window stays black; peekOnce only judges tiles whose centre is inside it.
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { Canvas(it).drawBitmap(shot, null, at, Paint(Paint.FILTER_BITMAP_FLAG)) }
    }

    /** A window shot is possible (D34): API 34+ and MaskWindowService on. Live: the service can be turned off. */
    private fun canShoot() = MaskWindowService.instance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** Ticket 16: the tile peek option is on and a window shot is possible. Off falls back to probes. */
    private fun peekMode() = settings.peekUnderMask && canShoot()

    /** D35: Precise is NudeNet 320n box masks where shots work, else Balanced tiles. */
    private fun wantedModel() = when {
        settings.preset != ParentPreset.PRECISE -> container.liveModel
        canShoot() -> SpikeModel.NUDENET_320N
        else -> SpikeModel.GANTMAN
    }

    private fun realMetrics() = DisplayMetrics().also {
        @Suppress("DEPRECATION")
        appContext.getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(it)
    }

    /**
     * Ticket 06: drives a [TileMaskTracker] from live frames and draws its masks on [overlay].
     * Masked tiles are not analysed; CLEAR and PROBING ones are, each applied as it arrives.
     * Runs only on [scope]'s single lane, which keeps the tracker single-threaded.
     */
    private inner class TileLoop(private val detector: Detector, private var coverNext: Boolean) {
        private val tracker = TileMaskTracker(0, 0, ::peekMode)
        private var grid = Grid(Preset.LIGHT, 0, 0, 0f)
        private var shown: List<TileState> = emptyList()
        private val probedAt = HashMap<Int, Long>()
        private var dueProbe: Job? = null
        private var open = true // a probe-timeout job can outlive a model switch
        private var coverSince: Long? = null // ticket 08, D40: cover up, not yet seen in a frame
        private var ownInFront = false // ticket 09
        private var peeking = false // ticket 16: a window shot or its verdicts in flight
        private var lastShotAt = 0L
        private var peak = 0f // highest raw score judged since the last report: the masked entry's score

        suspend fun run(frame: Bitmap): String {
            val preset = settings.preset.tiles
            val judging = Grid(preset, frame.width, frame.height, container.threshold())
            if (grid != judging) { // rotation, preset or threshold change (D40)
                grid = judging
                if (coverNext || shown.any { it != CLEAR }) { // coverNext: the judge before us had masks
                    coverNext = false
                    // Anything masked: every new tile will probe (D29), but not yet. The first frames
                    // after a rotation can be the system's rotation animation (a snapshot of the old
                    // screen and its masks), neither content nor our cover: judged as probe frames
                    // they released most tiles, re-masked ~400 ms later (Device B, 2026-10-04).
                    // Hold the cover and ignore frames until one shows it on every tile.
                    coverScreen() // a preset or threshold change has none yet
                    tracker.pause(now())
                    dueProbe?.cancel()
                    val since = now().also { coverSince = it }
                    scope.launch { // a static screen may never send the frame that shows it
                        delay(COVER_WAIT_MS)
                        if (coverSince == since) endCover()
                    }
                    // This frame was captured before the cover went up: if every tile was masked,
                    // it would pass the cover check without the cover ever having been on screen.
                    return "GantMan · $preset · waiting for the cover"
                } else {
                    val (cols, rows) = preset.grid(frame.width, frame.height)
                    tracker.reset(cols, rows)
                    publish()
                }
            }
            if (coverSince != null) {
                if (!coverOnEveryTile(frame, preset)) return "GantMan · $preset · waiting for the cover"
                endCover() // this frame shows the cover, so it is not a probe frame
                return "GantMan · $preset · cover seen"
            }
            // Probing tiles first: the mask is off until their verdict lands, so every tile judged
            // ahead of them is exposure (D26: ~38 ms per tile on B, ~55 on A). If a probe frame
            // still shows the mask, skip the CLEAR tiles: they wait one frame, and the next (valid)
            // probe frame arrives ~165 ms sooner on Device A instead of after the 300 ms cap.
            val probes = (0 until tracker.size).filter { tracker[it] == PROBING }
            val clears = (0 until tracker.size).filter { tracker[it] == CLEAR }
            val toScreen = frameToScreen()
            // A probe tile still showing the mask was captured before the lift: its verdict would be
            // ignored (tracker), so it isn't judged. The pixel check costs far less than a tile.
            val stillMasked = probes.filter { i ->
                val pixels = DebugMask.sample(frame, preset.tileRect(i, frame.width, frame.height), toScreen)
                DebugMask.looksMasked(pixels).also { Log.d(TAG, "probe frame tile=$i showsMask=$it ${DebugMask.describe(pixels)}") }
            }
            val judged = probes - stillMasked.toSet()
            detector.analyze(frame, preset, judged).collect { apply(it, showsMask = false) }
            // A timer can lift a mask while this frame waits between tiles (dueProbe, expireLater):
            // stop judging CLEAR tiles then, so the lift frame isn't held behind them. They wait one frame.
            var cleared = 0
            if (stillMasked.isEmpty()) {
                detector.analyze(frame, preset, clears)
                    .takeWhile { (0 until tracker.size).none { it !in probes && tracker[it] == PROBING } }
                    .collect { apply(it, showsMask = false); cleared++ }
                if (cleared < clears.size) Log.d(TAG, "clears stopped: a probe started mid-frame, judged $cleared of ${clears.size}")
            }
            tracker.endFrame(now())
            publish()
            if (PROBING in shown) expireLater() // a screen that goes static once the mask is lifted sends no frame
            scheduleDueProbe()
            // No latency here (it is in Logcat): the pill is captured too, and text that changes
            // every frame redraws it, which makes a static screen deliver frames forever.
            return "GantMan · $preset · ${shown.joinToString(" ") { it.name.take(1) }} · " +
                "${judged.size + cleared} analysed"
        }

        /** Ticket 12 (D29): a protected frame can't judge a probe, and none later will: release them. */
        fun protectedFrame() {
            tracker.releaseProbes(now())
            publish()
        }

        private fun expireLater() = scope.launch {
            delay(tracker.probeTimeoutMs)
            tracker.expireProbes(now())
            publish()
        }

        /**
         * Ticket 09: a Sophiel screen fills the display. Pauses the tracker, sharing it with the rotation
         * cover: whichever of the two ends last resumes it.
         */
        fun ownScreen(own: Boolean) {
            ownInFront = own
            if (coverSince != null) return // endCover resumes, or leaves it to us
            if (own) {
                tracker.pause(now())
                dueProbe?.cancel()
                return
            }
            tracker.resume(now())
            publish()
            scheduleDueProbe()
            if (PROBING in shown) expireLater() // as in run(): leaving may bring no valid frame
            if (PEEKING in shown) peek() // publish() only peeks on a change
        }

        /** Ticket 08: the cover is on screen in the new layout (or we gave up): probe every tile. */
        private fun endCover() {
            coverSince = null
            val (preset, w, h) = grid
            val (cols, rows) = preset.grid(w, h)
            Log.d(TAG, "cover ends, every tile probes")
            if (!ownInFront) tracker.resume(now())
            tracker.resetProbing(cols, rows, now())
            overlay.uncover()
            publish()
            // Exposure starts now: a tile already PROBING under the cover kept its older timestamp.
            shown.indices.filter { shown[it] == PROBING }.forEach { probedAt[it] = now() }
            expireLater() // as in run(): the lifted cover may bring no valid frame
        }

        private fun coverOnEveryTile(frame: Bitmap, preset: Preset): Boolean {
            val toScreen = frameToScreen()
            val (cols, rows) = preset.grid(frame.width, frame.height)
            val samples = (0 until cols * rows).map { DebugMask.sample(frame, preset.tileRect(it, frame.width, frame.height), toScreen) }
            Log.d(TAG, "cover check ${samples.joinToString(" ") { DebugMask.describe(it) }}")
            return samples.all(DebugMask::looksMasked)
        }

        /** Ticket 14: an owed probe starts on time even when the screen has gone static (no frame comes). */
        private fun scheduleDueProbe() {
            dueProbe?.cancel()
            val at = tracker.nextDueProbeAt() ?: return
            dueProbe = scope.launch {
                delay(at - now())
                tracker.startDueProbes(now())
                publish()
                delay(tracker.probeTimeoutMs) // as in run(): the lifted mask may bring no valid frame
                tracker.expireProbes(now())
                publish()
            }
        }

        /**
         * Ticket 16: judge the PEEKING tiles from a shot of the app window under them. The shot skips
         * our mask, so the mask stays up and nothing is exposed. One shot in flight at a time; a tile
         * the shot can't serve is lifted into an ordinary probe, so peeking is never worse than probing.
         */
        private fun peek() {
            if (peeking || !open) return
            peeking = true
            scope.launch {
                try {
                    peekOnce()
                } finally {
                    peeking = false
                }
                // Tiles in another window, or that started peeking meanwhile. Each round either takes
                // a shot (so the next waits out the rate limit) or resolves every PEEKING tile.
                if (open && coverSince == null && !ownInFront && (0 until tracker.size).any { tracker[it] == PEEKING }) peek()
            }
        }

        private suspend fun peekOnce() {
            delay(lastShotAt + SHOT_INTERVAL_MS - now())
            val waiting = (0 until tracker.size).filter { tracker[it] == PEEKING }
            if (waiting.isEmpty() || coverSince != null || ownInFront) return // a rotation resets every tile; leaving our screen peeks again
            val service = MaskWindowService.instance
            if (!peekMode() || service == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return lift(waiting, "peeking off")
            val at = grid
            val (preset, w, h) = at
            val toScreen = frameToScreen()
            val centre = { i: Int -> preset.tileRect(i, w, h).let { toScreen((it.left + it.right) / 2, (it.top + it.bottom) / 2) } }
            val (x, y) = centre(waiting.first())
            val start = now().also { lastShotAt = it }
            val (shot, bounds) = withTimeoutOrNull(SHOT_TIMEOUT_MS) { service.appWindowShot(x, y) }
                ?: return lift(waiting, "no shot after ${now() - start} ms")
            val inWindow = waiting.filter { i -> centre(i).let { (cx, cy) -> bounds.contains(cx, cy) } }
            Log.d(TAG, "peek tiles=$inWindow of $waiting window=$bounds shot=${shot.width}x${shot.height} shotMs=${now() - start}")
            val frame = shotToFrame(shot, bounds, w, h)
            shot.recycle()
            try {
                detector.analyze(frame, preset, inWindow).collect { v ->
                    // Rotated meanwhile, or lifted by the tracker's backstop: the verdict is stale.
                    if (grid == at && tracker[v.index] == PEEKING) apply(v, showsMask = false)
                }
                Log.d(TAG, "peek done totalMs=${now() - start}")
            } finally {
                frame.recycle()
            }
        }

        /** Ticket 16: these tiles probe the old way, mask lifted, on a fresh 300 ms. */
        private fun lift(tiles: List<Int>, why: String) {
            Log.d(TAG, "peek: lifting $tiles ($why)")
            tiles.forEach { tracker.liftPeek(it, now()) }
            publish()
            expireLater() // as in run(): the lifted mask may bring no valid frame
        }

        private fun apply(v: TileVerdict, showsMask: Boolean) {
            peak = maxOf(peak, v.score)
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

        /** Masks, probes or our cover are up: a judge switch must cover (D40). */
        fun masked() = coverSince != null || shown.any { it != CLEAR }

        fun close() { // leaves the cover to onFrame, which may keep it up for the next judge
            open = false
            coverSince = null // our 1 s timer must not take down a cover the next judge now owns
            masking = false
            probing = false
            dueProbe?.cancel()
            overlay.updateMasks(emptyList())
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
                // A peek keeps the mask up: its time is how long the verdict took, not exposure.
                if (was == PROBING || was == PEEKING) probedAt.remove(i)?.let {
                    Log.i(TAG, "${if (was == PROBING) "probe tile=$i exposureMs" else "peek tile=$i ms"}=${now - it} -> $state")
                }
                if (state == PROBING || state == PEEKING) probedAt[i] = now
                Log.i(TAG, "tile $i $was -> $state")
            }
            shown = states
            masking = states.any { it != CLEAR }
            probing = PROBING in states
            val (preset, w, h) = grid
            overlay.updateMasks(
                states.indices.filter { states[it] == MASKED || states[it] == PEEKING }.map { i ->
                    val r = preset.tileRect(i, w, h)
                    toDisplayFraction(RectF(r.left / w.toFloat(), r.top / h.toFloat(), r.right / w.toFloat(), r.bottom / h.toFloat()))
                },
            )
            // After the draw is posted: the Log write is disk I/O, and the mask must not wait for it.
            reportMasked(masking, preset.name, states.count { it != CLEAR }, peak)
            peak = 0f
            // Every path into PEEKING (neighbour rule, owed probe, rotation) publishes after it.
            if (PEEKING in states) peek()
        }

        private fun now() = SystemClock.elapsedRealtime()
    }

    private class Judge(val model: SpikeModel, val run: suspend (Bitmap) -> String, val close: () -> Unit, val masked: () -> Boolean,
        val ownScreen: (Boolean) -> Unit = {}, val protectedFrame: () -> Unit = {},
    )

    /** [covered]: the whole-screen cover is up (D40); the new judge takes it down. */
    private fun openJudge(model: SpikeModel, covered: Boolean): Judge = when (model.asset) {
        null -> TileLoop(DetectorFactory.create(appContext, container::threshold), covered)
            .let { Judge(model, it::run, it::close, it::masked, it::ownScreen, it::protectedFrame) }
        else -> BoxLoop(NudeNet.load(appContext, model.asset, model.inputSize), model, covered)
            .let { Judge(model, it::run, it::close, it::masked) }
    }

    /**
     * The Precise preset (D35), also the debug NudeNet models: NudeNet's unsafe boxes as masks. A mask
     * is captured too, so a frame can't see under it, but a window shot can (D34). Masks = boxes from
     * the last shot plus boxes found by frames since: a frame only adds (it can't tell a mask is
     * stale), a shot replaces both. No shots possible: outlines only, as in D24 (Precise then runs
     * Balanced tiles instead, see [wantedModel]).
     */
    private inner class BoxLoop(private val nudeNet: NudeNet, private val model: SpikeModel, covered: Boolean) {
        private var shotBoxes = emptyList<RectF>() // display fractions, padded
        private var frameBoxes = emptyList<RectF>()
        private var drawn = emptyList<RectF>()
        private var coverUp = covered
        private var confirmedAt = 0L // last good shot, or when masking started
        private var score = 0f // raw max NudeNet score of the last detection, for the masked entry
        private val shots = scope.launch { // same lane as frames, so NudeNet never runs twice at once
            if (covered) { // D40: a shot sees under the cover; its boxes replace it
                val start = SystemClock.elapsedRealtime()
                if (!shoot()) delay(start + COVER_WAIT_MS - SystemClock.elapsedRealtime())
                Log.d(TAG, "cover ends, box masks=${drawn.size}")
                overlay.uncover()
                coverUp = false
            }
            while (true) {
                delay(SHOT_INTERVAL_MS)
                // Nothing masked: frames see everything. Our screen in front: a shot of it would drop every mask.
                if (canShoot() && drawn.isNotEmpty() && !container.ownScreens.showing.value) shoot()
            }
        }

        fun run(frame: Bitmap): String {
            val start = SystemClock.elapsedRealtime()
            val detections = nudeNet.detect(frame)
            val ms = SystemClock.elapsedRealtime() - start
            overlay.updateBoxes(detections.map { it.copy(box = toDisplayFraction(it.box)) })
            score = detections.unsafeScore()
            if (canShoot()) {
                frameBoxes = frameBoxes + masksOf(detections)
            } else {
                shotBoxes = emptyList()
                frameBoxes = emptyList()
            }
            draw()
            Log.d(TAG, "live model=${model.name} score=${detections.unsafeScore()} latencyMs=$ms masks=${shotBoxes.size}+${frameBoxes.size}")
            return "%s · score=%.2f · %dms · %d masks".format(model.label, detections.unsafeScore(), ms, drawn.size) +
                detections.take(3).joinToString("") { "\n%s %.2f".format(it.label.lowercase(), it.score) }
        }

        /** False when no shot came (the covered start then waits out the rest of [COVER_WAIT_MS]). */
        private suspend fun shoot(): Boolean {
            val service = MaskWindowService.instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
            val real = realMetrics()
            val start = SystemClock.elapsedRealtime()
            val result = withTimeoutOrNull(SHOT_TIMEOUT_MS) { service.appWindowShot(real.widthPixels / 2, real.heightPixels / 2) }
            if (result == null) {
                // A failed shot says nothing new: hold the masks. Dropping them at once flashed the
                // content each time (run 1: 29 of 30 shots failed). After BOX_HOLD_MS without a good
                // shot, drop them anyway, or a mask could never come off; frames re-mask what remains.
                val held = start - confirmedAt < BOX_HOLD_MS
                Log.d(TAG, "box shot: none, masks ${if (held) "held" else "dropped"}")
                if (held) return false
                shotBoxes = emptyList()
                frameBoxes = emptyList()
                draw()
                return false
            }
            confirmedAt = start
            val (shot, bounds) = result
            val frame = shotToFrame(shot, bounds, size.crop.width(), size.crop.height())
            shot.recycle()
            val detections = try { nudeNet.detect(frame) } finally { frame.recycle() }
            score = detections.unsafeScore()
            shotBoxes = masksOf(detections)
            frameBoxes = emptyList()
            draw()
            Log.d(TAG, "box shot masks=${shotBoxes.size} totalMs=${SystemClock.elapsedRealtime() - start}")
            return true
        }

        private fun masksOf(detections: List<Detection>) = detections.filter { it.unsafe && it.score >= BOX_MASK_SCORE }.map {
            val b = toDisplayFraction(it.box)
            val dx = b.width() * BOX_PAD
            val dy = b.height() * BOX_PAD
            RectF(b.left - dx, b.top - dy, b.right + dx, b.bottom + dy)
        }

        // Only on change: a redraw is captured, and a static screen would then send frames forever (D18).
        private fun draw() {
            val rects = shotBoxes + frameBoxes
            reportMasked(rects.isNotEmpty(), "PRECISE", rects.size, score)
            if (rects == drawn) return
            if (drawn.isEmpty()) confirmedAt = SystemClock.elapsedRealtime() // a fresh mask starts its hold
            drawn = rects
            overlay.updateMasks(rects)
        }

        /** Boxes or our start-up cover are up: a judge switch must cover (D40). */
        fun masked() = coverUp || drawn.isNotEmpty()

        fun close() {
            shots.cancel()
            overlay.updateMasks(emptyList())
            nudeNet.close()
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

/** What tiles are judged against: a change with anything masked takes the cover path (D33, D40). */
private data class Grid(val preset: Preset, val w: Int, val h: Int, val threshold: Float)

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
