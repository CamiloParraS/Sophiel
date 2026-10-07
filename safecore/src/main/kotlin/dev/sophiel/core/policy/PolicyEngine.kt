package dev.sophiel.core.policy

import dev.sophiel.core.Severity

/**
 * Stateless score → [Severity] mapping. Engage/release hysteresis lives in the tile tracker.
 * [explicitThreshold] is read on every call, so a Parent's sensitivity change applies on the next
 * tile (D40). Suggestive stays at half of it (D11).
 */
class PolicyEngine(private val explicitThreshold: () -> Float) {
    /** A fixed cutoff, for tests and benchmarks. */
    constructor(explicitThreshold: Float = 0.70f) : this({ explicitThreshold })

    fun classify(score: Float): Severity {
        val explicit = explicitThreshold()
        return when {
            score >= explicit -> Severity.EXPLICIT
            score >= explicit / 2f -> Severity.SUGGESTIVE
            else -> Severity.SAFE
        }
    }
}
