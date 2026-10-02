package dev.sophiel.core

import android.graphics.Bitmap
import android.os.SystemClock
import dev.sophiel.core.cache.VerdictCache
import dev.sophiel.core.gate.PerceptualHash
import dev.sophiel.core.gate.SkinGate
import dev.sophiel.core.model.NsfwClassifier
import dev.sophiel.core.model.Preprocessor
import dev.sophiel.core.policy.PerTilePolicy
import dev.sophiel.core.policy.PolicyEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Orchestrates one frame through hash → cache lookup → skin gate → classifier
 * → policy. All work runs on [dispatcher], so concurrent [analyze] callers
 * queue rather than run in parallel (SPEC.md §3.4).
 *
 * The cache and gate only short-circuit the *score*. [PolicyEngine] runs on
 * every frame — cache hits and gated frames included — so its hysteresis
 * sees the whole frame stream. (Caching the post-policy severity would freeze
 * a static screen at whatever severity its first frame got.)
 */
class DetectionPipeline(
    private val classifier: NsfwClassifier,
    private val policies: PerTilePolicy,
    private val dispatcher: CoroutineDispatcher,
    private val gate: SkinGate = SkinGate(),
    private val cache: VerdictCache = VerdictCache(),
) : Detector {

    @Volatile private var closed = false
    private var lastPreset: Preset? = null // touched only on [dispatcher]

    override suspend fun analyze(frame: Bitmap, preset: Preset, only: Set<Int>?): FrameVerdict =
        withContext(dispatcher) {
            if (closed) throw CancellationException("Detector closed")
            val start = SystemClock.elapsedRealtime()
            if (preset != lastPreset) { // indices mean different tiles under a different grid
                policies.reset()
                lastPreset = preset
            }
            val tiles = (0 until preset.cols * preset.rows)
                .filter { only == null || it in only }
                .map { index -> judge(index, frame, preset) }
            FrameVerdict(tiles, elapsedSince(start))
        }

    private fun judge(index: Int, frame: Bitmap, preset: Preset): TileVerdict {
        val tile = crop(frame, preset, index)
        try {
            val hash = PerceptualHash.hash(tile)
            val scored = cache.get(hash)?.copy(cacheHit = true) ?: score(tile, hash).also { cache.put(hash, it) }
            return scored.copy(index = index, severity = policies.classify(index, scored.score))
        } finally {
            if (tile !== frame) tile.recycle() // the caller owns the frame itself
        }
    }

    private fun crop(frame: Bitmap, preset: Preset, index: Int): Bitmap {
        if (preset.cols * preset.rows == 1) return frame
        val r = preset.tileRect(index, frame.width, frame.height)
        return Bitmap.createBitmap(frame, r.left, r.top, r.width, r.height)
    }

    /** Gate + classifier only. Index/severity are placeholders, filled in by [judge]. */
    private fun score(tile: Bitmap, hash: Long): TileVerdict {
        val gated = !gate.shouldClassify(tile)
        val score = if (gated) 0f else classifier.classify(Preprocessor.toInputBuffer(tile))
        return TileVerdict(0, Severity.SAFE, score, gated = gated, cacheHit = false, hash = hash)
    }

    /**
     * Releases the interpreter and, if [dispatcher] owns an executor thread, shuts it down.
     * Blocks the caller for at most one in-flight inference.
     */
    override fun close() {
        // Runs on the inference thread, behind any in-flight analyze(): Interpreter is not
        // thread-safe, and close() from another thread mid-run() frees native state under it.
        runBlocking(dispatcher) {
            closed = true
            classifier.close()
        }
        (dispatcher as? java.io.Closeable)?.close()
    }

    private fun elapsedSince(startMs: Long) = SystemClock.elapsedRealtime() - startMs
}
