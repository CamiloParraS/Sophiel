package dev.sophiel.pin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockTest {
    private var now = 0L
    private val u = Unlock { now }

    @Test fun `locked until a pin, ends 2 min after the last gated action`() {
        assertFalse(u.unlocked())
        u.extend()
        now = 119_999
        assertTrue(u.unlocked())
        u.extend() // a gated action slides the window
        now = 119_999 + 119_999
        assertTrue(u.unlocked())
        now += 1
        assertFalse(u.unlocked())
        assertNull(u.endsAt.value)
    }

    @Test fun `stop locks, a configuration change does not, lockNow locks`() {
        u.extend()
        u.onActivityStop(changingConfigurations = true)
        assertTrue(u.unlocked())
        assertEquals(Unlock.WINDOW_MS, u.endsAt.value)
        u.onActivityStop(changingConfigurations = false)
        assertFalse(u.unlocked())
        u.extend()
        u.lockNow()
        assertFalse(u.unlocked())
    }
}
