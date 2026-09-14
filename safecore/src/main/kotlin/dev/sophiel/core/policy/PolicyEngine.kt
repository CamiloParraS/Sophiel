package dev.sophiel.core.policy

import dev.sophiel.core.Severity

/**
 * Turns a raw unsafe-probability score into a [Severity].
 *
 * Whole-frame classification on scrolling content produces scores that
 * oscillate around the threshold; without smoothing, the overlay would
 * strobe. [EXPLICIT] requires [ENGAGE_FRAMES] consecutive frames at or above
 * [explicitThreshold] to engage, and [RELEASE_FRAMES] consecutive frames
 * below it to release. The asymmetry is intentional: engage fast, release
 * slow. See SPEC.md §6.2.
 *
 * Not thread-safe: call from a single sequential stream of frames (see
 * [dev.sophiel.core.DetectionPipeline]).
 */
class PolicyEngine(
    private val explicitThreshold: Float = 0.70f,
    private val suggestiveThreshold: Float = explicitThreshold / 2f,
) {
    private var consecutiveAbove = 0
    private var consecutiveBelow = 0
    private var engaged = false

    /** Classifies [score], updating internal hysteresis state. */
    fun classify(score: Float): Severity {
        if (score >= explicitThreshold) {
            consecutiveAbove++
            consecutiveBelow = 0
        } else {
            consecutiveBelow++
            consecutiveAbove = 0
        }

        if (!engaged && consecutiveAbove >= ENGAGE_FRAMES) engaged = true
        if (engaged && consecutiveBelow >= RELEASE_FRAMES) engaged = false

        return when {
            engaged -> Severity.EXPLICIT
            score >= suggestiveThreshold -> Severity.SUGGESTIVE
            else -> Severity.SAFE
        }
    }

    private companion object {
        const val ENGAGE_FRAMES = 2
        const val RELEASE_FRAMES = 3
    }
}
