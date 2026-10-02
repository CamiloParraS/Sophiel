package dev.sophiel.core.policy

import dev.sophiel.core.Severity
import org.junit.Assert.assertEquals
import org.junit.Test

class PerTilePolicyTest {

    @Test
    fun `hysteresis in one tile does not affect another`() {
        val policy = PerTilePolicy { PolicyEngine(explicitThreshold = 0.7f) }

        policy.classify(0, 0.9f)
        assertEquals(Severity.EXPLICIT, policy.classify(0, 0.9f)) // tile 0 engaged
        assertEquals(Severity.SAFE, policy.classify(1, 0.0f))
        assertEquals(Severity.SUGGESTIVE, policy.classify(2, 0.9f)) // tile 2's first hit: not engaged yet
    }
}
