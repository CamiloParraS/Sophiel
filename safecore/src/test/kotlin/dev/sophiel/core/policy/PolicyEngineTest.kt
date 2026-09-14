package dev.sophiel.core.policy

import dev.sophiel.core.Severity
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyEngineTest {

    @Test
    fun `a low score classifies as SAFE`() {
        val engine = PolicyEngine(explicitThreshold = 0.70f, suggestiveThreshold = 0.35f)

        assertEquals(Severity.SAFE, engine.classify(0.1f))
    }

    @Test
    fun `a mid score classifies as SUGGESTIVE`() {
        val engine = PolicyEngine(explicitThreshold = 0.70f, suggestiveThreshold = 0.35f)

        assertEquals(Severity.SUGGESTIVE, engine.classify(0.5f))
    }

    @Test
    fun `a single frame above the explicit threshold is not yet EXPLICIT`() {
        val engine = PolicyEngine(explicitThreshold = 0.70f, suggestiveThreshold = 0.35f)

        assertEquals(Severity.SUGGESTIVE, engine.classify(0.9f))
    }

    @Test
    fun `two consecutive frames above the explicit threshold engage EXPLICIT`() {
        val engine = PolicyEngine(explicitThreshold = 0.70f, suggestiveThreshold = 0.35f)

        engine.classify(0.9f)
        assertEquals(Severity.EXPLICIT, engine.classify(0.9f))
    }

    @Test
    fun `a single low frame after engaging does not release EXPLICIT`() {
        val engine = PolicyEngine(explicitThreshold = 0.70f, suggestiveThreshold = 0.35f)

        engine.classify(0.9f)
        engine.classify(0.9f) // engaged
        assertEquals(Severity.EXPLICIT, engine.classify(0.1f))
    }

    @Test
    fun `three consecutive low frames release EXPLICIT`() {
        val engine = PolicyEngine(explicitThreshold = 0.70f, suggestiveThreshold = 0.35f)

        engine.classify(0.9f)
        engine.classify(0.9f) // engaged
        engine.classify(0.1f)
        engine.classify(0.1f)
        assertEquals(Severity.SAFE, engine.classify(0.1f))
    }

    @Test
    fun `an above-threshold frame in the middle of a release resets the below-streak`() {
        val engine = PolicyEngine(explicitThreshold = 0.70f, suggestiveThreshold = 0.35f)

        engine.classify(0.9f)
        engine.classify(0.9f) // engaged
        engine.classify(0.1f)
        engine.classify(0.9f) // resets the below-streak; still engaged
        engine.classify(0.1f)
        assertEquals(Severity.EXPLICIT, engine.classify(0.1f)) // only 2 consecutive low frames, not 3
    }
}
