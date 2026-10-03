package dev.sophiel.core

import dev.sophiel.core.model.NsfwClassifier
import dev.sophiel.core.policy.PolicyEngine
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.Executors

/** Severity ladder. Ordinal order is meaningful; do not reorder. */
enum class Severity { SAFE, SUGGESTIVE, EXPLICIT }

/**
 * How many tiles a frame is split into (SPEC.md §3.3), as portrait cols × rows.
 * Light is the whole frame. Use [grid] for the actual layout of a given frame.
 */
enum class Preset(val cols: Int, val rows: Int) { LIGHT(1, 1), BALANCED(2, 3) }

/**
 * Parent-set sensitivity (SPEC.md §3.5): the unsafe-score cutoff for [Severity.EXPLICIT].
 * Stricter flags at lower scores.
 */
// ponytail: placeholder values (Normal = the old 0.70 default); ticket 07 tunes them on devices.
enum class Sensitivity(val threshold: Float) { STRICT(0.55f), NORMAL(0.70f), RELAXED(0.85f) }

/**
 * Outcome of analysing one tile.
 *
 * @param index     row * cols + col, on the frame's [grid]
 * @param severity  stateless mapping of [score]; all timing lives in the tile tracker
 * @param score     raw unsafe probability in [0,1]
 * @param gated     true if the skin gate short-circuited before the classifier ran
 * @param cacheHit  true if this score was reused from an identical prior tile
 * @param hash      dHash of this tile's pixels
 * @param latencyMs from [Detector.analyze] start to this tile's result; the last tile's is the frame's
 */
data class TileVerdict(
    val index: Int,
    val severity: Severity,
    val score: Float,
    val gated: Boolean,
    val cacheHit: Boolean,
    val hash: Long,
    val latencyMs: Long = 0,
)

/** Analyses screen frames locally, emitting one [TileVerdict] per tile. */
interface Detector {
    /**
     * Judge the tiles of [frame] under [preset]: those in [only], in that order, or all of them
     * in index order if it is null. Each tile is emitted as soon as it is judged, so its mask can
     * go up (or a probe resolve) without waiting for the rest of the sweep.
     *
     * The caller keeps ownership of [frame] and must not recycle it until
     * collection completes.
     *
     * Safe to collect from any thread; implementations serialise internally onto a
     * single inference thread. Concurrent collectors are queued, not parallelised.
     */
    fun analyze(frame: android.graphics.Bitmap, preset: Preset, only: List<Int>? = null): Flow<TileVerdict>

    /** Releases the interpreter. The instance is unusable afterwards. */
    fun close()
}

/** Entry point for [Detector] construction. See SPEC.md §3.4. */
object DetectorFactory {
    fun create(context: android.content.Context, sensitivity: Sensitivity = Sensitivity.NORMAL): Detector =
        create(context, sensitivity.threshold)

    /** Raw cutoff for the debug menu only. @param threshold unsafe-probability cutoff for [Severity.EXPLICIT], in [0,1] */
    fun create(context: android.content.Context, threshold: Float): Detector =
        DetectionPipeline(
            classifier = NsfwClassifier.load(context),
            policy = PolicyEngine(explicitThreshold = threshold),
            dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
        )
}
