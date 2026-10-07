package dev.sophiel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sophiel.core.DetectionPipeline
import dev.sophiel.core.Preset
import dev.sophiel.core.Sensitivity
import dev.sophiel.core.Severity.EXPLICIT
import dev.sophiel.core.TileVerdict
import dev.sophiel.core.gate.SkinGate
import dev.sophiel.core.model.NsfwClassifier
import dev.sophiel.core.policy.PolicyEngine
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

/**
 * Ticket 07 measurements, not pass/fail: run with
 * `./gradlew :app:connectedDebugAndroidTest` and read `adb logcat -s SophielBench`.
 * Frames are the Test Feed images squashed to a capture-sized portrait frame. In memory only.
 */
@RunWith(AndroidJUnit4::class)
class TileBenchmark {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val frames: List<Pair<String, Bitmap>> by lazy {
        context.assets.list(DIR).orEmpty().filter { it.endsWith(".png") }.sorted().map { name ->
            val src = context.assets.open("$DIR/$name").use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
            }!!
            name to Bitmap.createScaledBitmap(src, W, H, true).also { if (it !== src) src.recycle() }
        }
    }

    private fun pipeline(gate: Boolean, threads: Int? = null) = DetectionPipeline(
        classifier = NsfwClassifier.load(context, threads),
        policy = PolicyEngine(Sensitivity.NORMAL.threshold),
        dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
        gate = if (gate) SkinGate() else SkinGate(minRatio = 0f), // gate off = every tile classified
    )

    /** Per-tile and per-sweep cost; a fresh pipeline per run so the verdict cache starts empty. */
    @Test
    fun sweepCost() = runBlocking<Unit> {
        val warmUp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        for (preset in Preset.entries) for (gate in listOf(true, false)) {
            val runMedians = mutableListOf<Long>()
            val sweeps = mutableListOf<Long>()
            val classified = mutableListOf<Long>()
            val gated = mutableListOf<Long>()
            val firstFlag = mutableListOf<Long>()
            var gatedTiles = 0
            var allTiles = 0
            repeat(RUNS) { run ->
                val p = pipeline(gate)
                try {
                    p.analyze(warmUp, preset).toList()
                    val runSweeps = mutableListOf<Long>()
                    for ((name, frame) in frames) {
                        val tiles = p.analyze(frame, preset).toList()
                        var prev = 0L
                        for (t in tiles) {
                            if (!t.cacheHit) (if (t.gated) gated else classified) += t.latencyMs - prev
                            prev = t.latencyMs
                        }
                        runSweeps += tiles.last().latencyMs
                        tiles.firstOrNull { it.severity == EXPLICIT }?.let { firstFlag += it.latencyMs }
                        gatedTiles += tiles.count { it.gated }
                        allTiles += tiles.size
                        if (run == 0 && gate && preset == Preset.BALANCED) {
                            Log.i(TAG, "gate frame=$name gated=${tiles.count { it.gated }}/${tiles.size} ${tiles.describe()}")
                        }
                    }
                    runMedians += runSweeps.p(50)
                    sweeps += runSweeps
                } finally {
                    p.close()
                }
            }
            Log.i(
                TAG,
                "sweep preset=$preset gate=${if (gate) "on" else "off"} frames=${frames.size} runs=$RUNS " +
                    "runMedianSweepMs=$runMedians sweepMs p50=${sweeps.p(50)} p90=${sweeps.p(90)} max=${sweeps.max()} " +
                    "classifiedTileMs p50=${classified.p(50)} p90=${classified.p(90)} n=${classified.size} " +
                    "gatedTileMs p50=${gated.p(50)} n=${gated.size} gatedShare=${gatedTiles * 100 / allTiles}% " +
                    "firstFlagMs p50=${firstFlag.p(50)} n=${firstFlag.size}",
            )
        }
        warmUp.recycle()
    }

    /**
     * Classifier cost per tile by interpreter thread count (null = TFLite's default, what ships).
     * Balanced, gate off, so every tile is classified; one warm-up sweep per pipeline.
     * Default runs last: the first config runs on a cool device (B: default first read 29 ms, last 39).
     */
    @Test
    fun threadCost() = runBlocking<Unit> {
        val warmUp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        for (threads in listOf(4, 2, 1, null)) {
            val tileMs = mutableListOf<Long>()
            repeat(RUNS) {
                val p = pipeline(gate = false, threads = threads)
                try {
                    p.analyze(warmUp, Preset.BALANCED).toList()
                    for ((_, frame) in frames) {
                        var prev = 0L
                        for (t in p.analyze(frame, Preset.BALANCED).toList()) {
                            if (!t.cacheHit) tileMs += t.latencyMs - prev
                            prev = t.latencyMs
                        }
                    }
                } finally {
                    p.close()
                }
            }
            Log.i(TAG, "threads=${threads ?: "default"} classifiedTileMs p50=${tileMs.p(50)} p90=${tileMs.p(90)} max=${tileMs.max()} n=${tileMs.size}")
        }
        warmUp.recycle()
    }

    /**
     * Straddling: each image the whole frame flags is fitted into a box and placed on white:
     * one tile's size inside a tile (control), across a vertical edge (2 tiles) and on a corner
     * (4 tiles); and feed-like, full width and two rows tall, aligned to rows or offset half a row.
     * "missed" = Light flags the composite but no Balanced tile does: the safety-net case.
     */
    @Test
    fun straddling() = runBlocking<Unit> {
        val p = pipeline(gate = true)
        val tileW = W / 2f
        val tileH = H / 3f
        // label to (centre x, centre y, box w, box h)
        val placements = listOf(
            "inside" to listOf(tileW / 2, tileH * 1.5f, tileW, tileH),
            "edge" to listOf(tileW, tileH * 1.5f, tileW, tileH),
            "corner" to listOf(tileW, tileH, tileW, tileH),
            "feedAligned" to listOf(W / 2f, tileH, W.toFloat(), tileH * 2),
            "feedOffset" to listOf(W / 2f, tileH * 1.5f, W.toFloat(), tileH * 2),
        )
        val missed = placements.associate { it.first to 0 }.toMutableMap()
        val tileOnly = placements.associate { it.first to 0 }.toMutableMap()
        val wholeHits = placements.associate { it.first to 0 }.toMutableMap()
        val tileHits = placements.associate { it.first to 0 }.toMutableMap()
        try {
            val flagged = frames.filter { (_, f) -> p.analyze(f, Preset.LIGHT).single().severity == EXPLICIT }
            for ((name, src) in flagged) for ((label, box) in placements) {
                val frame = composite(src, box[0], box[1], box[2], box[3])
                val whole = p.analyze(frame, Preset.LIGHT).single()
                val tiles = p.analyze(frame, Preset.BALANCED).toList()
                frame.recycle()
                val anyTile = tiles.any { it.severity == EXPLICIT }
                if (whole.severity == EXPLICIT && !anyTile) missed[label] = missed.getValue(label) + 1
                if (whole.severity != EXPLICIT && anyTile) tileOnly[label] = tileOnly.getValue(label) + 1
                if (whole.severity == EXPLICIT) wholeHits[label] = wholeHits.getValue(label) + 1
                if (anyTile) tileHits[label] = tileHits.getValue(label) + 1
                Log.i(TAG, "straddle frame=$name at=$label whole=%.2f tiles=${tiles.describe()}".format(whole.score))
            }
            Log.i(
                TAG,
                "straddle summary flaggedImages=${flagged.size} flaggedByWhole=$wholeHits flaggedByAnyTile=$tileHits " +
                    "missedByTiles=$missed caughtOnlyByTiles=$tileOnly",
            )
        } finally {
            p.close()
        }
    }

    private fun composite(src: Bitmap, cx: Float, cy: Float, boxW: Float, boxH: Float): Bitmap {
        val scale = minOf(boxW / src.width, boxH / src.height)
        val (w, h) = src.width * scale to src.height * scale
        return Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply {
                drawColor(Color.WHITE)
                drawBitmap(src, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
    }

    private fun List<TileVerdict>.describe() =
        joinToString(" ") { "%d:%s%.2f".format(it.index, if (it.gated) "g" else "", it.score) }

    private fun List<Long>.p(pct: Int): Long = if (isEmpty()) -1 else sorted()[(size - 1) * pct / 100]

    private companion object {
        const val TAG = "SophielBench"
        const val DIR = "testfeed"
        const val RUNS = 3
        const val W = 360 // capture short side (SPEC §4.6), portrait, bars cropped
        const val H = 744
    }
}
