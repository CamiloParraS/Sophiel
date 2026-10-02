package dev.sophiel.core

import dev.sophiel.core.model.NsfwClassifier
import dev.sophiel.core.policy.PerTilePolicy
import dev.sophiel.core.policy.PolicyEngine
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/** Severity ladder. Ordinal order is meaningful; do not reorder. */
enum class Severity { SAFE, SUGGESTIVE, EXPLICIT }

/** How many tiles a frame is split into (SPEC.md ง3.3). Light is the whole frame. */
enum class Preset(val cols: Int, val rows: Int) { LIGHT(1, 1), BALANCED(2, 3) }

/**
 * Outcome of analysing one tile.
 *
 * @param index     row * cols + col
 * @param severity  bucketed decision after policy + hysteresis
 * @param score     raw unsafe probability in [0,1]
 * @param gated     true if the skin gate short-circuited before the classifier ran
 * @param cacheHit  true if this score was reused from an identical prior tile
 * @param hash      dHash of this tile's pixels
 */
data class TileVerdict(
    val index: Int,
    val severity: Severity,
    val score: Float,
    val gated: Boolean,
    val cacheHit: Boolean,
    val hash: Long,
)

/** @param latencyMs wall-clock time inside [Detector.analyze], all tiles */
data class FrameVerdict(val tiles: List<TileVerdict>, val latencyMs: Long)

/** Analyses screen frames locally and returns a [FrameVerdict] per frame. */
interface Detector {
    /**
     * Judge the given tiles of [frame] (all if [only] is null).
     *
     * The caller retains ownership of [frame]; this method must not recycle it
     * and must not retain a reference past return.
     *
     * Safe to call from any thread; implementations serialise internally onto a
     * single inference thread. Concurrent callers are queued, not parallelised.
     */
    suspend fun analyze(frame: android.graphics.Bitmap, preset: Preset, only: Set<Int>? = null): FrameVerdict

    /** Releases the interpreter. The instance is unusable afterwards. */
    fun close()
}

/** Entry point for [Detector] construction. See SPEC.md ยง3.4. */
object DetectorFactory {
    /** @param threshold unsafe-probability cutoff for [Severity.EXPLICIT], in [0,1] */
    fun create(context: android.content.Context, threshold: Float = 0.70f): Detector =
        DetectionPipeline(
            classifier = NsfwClassifier.load(context),
            policies = PerTilePolicy { PolicyEngine(explicitThreshold = threshold) },
            dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
        )
}
