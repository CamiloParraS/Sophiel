package dev.sophiel.core.policy

import dev.sophiel.core.Severity

/** One [PolicyEngine] per tile index, so a flicker in one tile never moves another's hysteresis. */
class PerTilePolicy(private val newEngine: () -> PolicyEngine) {
    private val engines = HashMap<Int, PolicyEngine>()

    fun classify(index: Int, score: Float): Severity =
        engines.getOrPut(index, newEngine).classify(score)

    /** Drops all state; call when the tile grid changes shape. */
    fun reset() = engines.clear()
}
