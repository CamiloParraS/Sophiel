package dev.sophiel.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnScreensTest {
    private val a = Any()
    private val b = Any()

    @Test
    fun `a full-screen resumed activity shows our screen until it pauses`() {
        val screens = OwnScreens()
        screens.update(a, front = true)
        assertTrue(screens.showing.value)

        screens.update(a, front = false)
        assertFalse(screens.showing.value)
    }

    @Test
    fun `entering split screen while resumed brings the masks back`() {
        val screens = OwnScreens()
        screens.update(a, front = true)

        screens.update(a, front = false) // onMultiWindowModeChanged(true)
        assertFalse(screens.showing.value)
    }

    @Test
    fun `a pause never counted does not cancel another activity`() {
        val screens = OwnScreens()
        screens.update(a, front = true)
        screens.update(b, front = false) // b was resumed in split screen, now paused

        assertTrue(screens.showing.value)
    }

    @Test
    fun `the next activity resuming before the last pauses keeps our screen showing`() {
        val screens = OwnScreens()
        screens.update(a, front = true)
        screens.update(b, front = true)
        screens.update(a, front = false)

        assertTrue(screens.showing.value)
    }

    @Test
    fun `repeated resumes of one activity count once`() {
        val screens = OwnScreens()
        screens.update(a, front = true)
        screens.update(a, front = true)
        screens.update(a, front = false)

        assertFalse(screens.showing.value)
    }
}
