package dev.safelens.core

/** Severity ladder. Ordinal order is meaningful; do not reorder. */
enum class Severity { SAFE, SUGGESTIVE, EXPLICIT }

/**
 * Outcome of analysing one frame.
 *
 * @param severity  bucketed decision after policy + hysteresis
 * @param score     raw unsafe probability in [0,1]
 * @param gated     true if the skin gate short-circuited before the classifier ran
 * @param cacheHit  true if this verdict was reused from a near-identical prior frame
 * @param latencyMs wall-clock time inside [Detector.analyze]
 */
data class Verdict(
    val severity: Severity,
    val score: Float,
    val gated: Boolean,
    val cacheHit: Boolean,
    val latencyMs: Long,
)

/** Analyses screen frames locally and returns a [Verdict] per frame. */
interface Detector {
    /**
     * Analyse a single frame.
     *
     * The caller retains ownership of [frame]; this method must not recycle it
     * and must not retain a reference past return.
     *
     * Safe to call from any thread; implementations serialise internally onto a
     * single inference thread. Concurrent callers are queued, not parallelised.
     */
    suspend fun analyze(frame: android.graphics.Bitmap): Verdict

    /** Releases the interpreter. The instance is unusable afterwards. */
    fun close()
}

/** Entry point for [Detector] construction. See SPEC.md §3.4. */
object DetectorFactory {
    /** @param threshold unsafe-probability cutoff for [Severity.EXPLICIT], in [0,1] */
    fun create(context: android.content.Context, threshold: Float = 0.70f): Detector =
        TODO("Detector implementation lands in M2 (pipeline). SPEC.md §5 M2.")
}
