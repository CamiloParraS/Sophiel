package dev.sophiel.ui

import dev.sophiel.ui.theme.isReducedMotion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test
    fun animationsOffMeansReduced() {
        assertTrue(isReducedMotion(0f))
        assertFalse(isReducedMotion(1f))
        assertFalse(isReducedMotion(0.5f))
    }
}
