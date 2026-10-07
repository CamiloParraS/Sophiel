package dev.sophiel.pin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinFlowTest {
    @Test
    fun countdownRoundsUpToWholeSeconds() {
        assertEquals("0:30", formatCountdown(30_000))
        assertEquals("0:02", formatCountdown(1_001))
        assertEquals("0:00", formatCountdown(0))
        assertEquals("0:00", formatCountdown(-5))
        assertEquals("1:48", formatCountdown(108_000))
        assertEquals("60:00", formatCountdown(3_600_000))
    }

    @Test
    fun doorRunsAndExtendsWhenUnlockedAndRefusesWhenLocked() {
        var now = 0L
        val unlock = Unlock { now }
        var ran = 0
        assertFalse(unlock.gate { ran++ })
        assertEquals(0, ran)

        unlock.extend()
        now = 100_000
        assertTrue(unlock.gate { ran++ })
        assertEquals(1, ran)
        assertEquals(100_000 + Unlock.WINDOW_MS, unlock.endsAt.value) // extended from the action

        now = 100_000 + Unlock.WINDOW_MS
        assertFalse(unlock.gate { ran++ })
        assertEquals(1, ran)
    }

    @Test
    fun createAsksTwiceAndMismatchStaysOnConfirm() {
        var s = CreateState()
        assertEquals(CreateStep.ENTER, s.step)
        s = s.submit("1234")
        assertEquals(CreateStep.CONFIRM, s.step)
        assertNull(s.result)

        s = s.submit("1235")
        assertEquals(CreateStep.CONFIRM, s.step)
        assertTrue(s.mismatch)
        assertNull(s.result)

        s = s.submit("1234")
        assertEquals("1234", s.result)
        assertFalse(s.mismatch)
    }
}
