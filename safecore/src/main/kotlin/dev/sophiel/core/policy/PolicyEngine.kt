package dev.sophiel.core.policy

import dev.sophiel.core.Severity

/** Stateless score → [Severity] mapping. Engage/release hysteresis lives in the tile tracker. */
class PolicyEngine(
    private val explicitThreshold: Float = 0.70f,
    private val suggestiveThreshold: Float = explicitThreshold / 2f,
) {
    fun classify(score: Float): Severity = when {
        score >= explicitThreshold -> Severity.EXPLICIT
        score >= suggestiveThreshold -> Severity.SUGGESTIVE
        else -> Severity.SAFE
    }
}
