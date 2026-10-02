package dev.sophiel.core.policy

import dev.sophiel.core.Severity
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyEngineTest {
    private val engine = PolicyEngine(explicitThreshold = 0.70f, suggestiveThreshold = 0.35f)

    @Test
    fun `scores map to severities and the mapping holds no state`() {
        repeat(2) {
            assertEquals(Severity.SAFE, engine.classify(0.1f))
            assertEquals(Severity.SUGGESTIVE, engine.classify(0.5f))
            assertEquals(Severity.EXPLICIT, engine.classify(0.9f))
        }
    }
}
