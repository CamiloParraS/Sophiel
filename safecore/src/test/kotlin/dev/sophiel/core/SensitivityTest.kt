package dev.sophiel.core

import dev.sophiel.core.policy.PolicyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitivityTest {
    @Test fun `stricter levels flag at lower scores`() {
        assertTrue(Sensitivity.STRICT.threshold < Sensitivity.NORMAL.threshold)
        assertTrue(Sensitivity.NORMAL.threshold < Sensitivity.RELAXED.threshold)
        assertEquals(0.70f, Sensitivity.NORMAL.threshold) // the old default
    }

    @Test fun `suggestive stays at half the explicit threshold (D11)`() {
        for (level in Sensitivity.entries) {
            val policy = PolicyEngine(level.threshold)
            assertEquals(Severity.SUGGESTIVE, policy.classify(level.threshold / 2))
            assertEquals(Severity.SAFE, policy.classify(level.threshold / 2 - 0.01f))
        }
    }
}
