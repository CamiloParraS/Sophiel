package dev.sophiel.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameThrottleTest {

    @Test
    fun `the first frame is always processed`() {
        val throttle = FrameThrottle(minIntervalMs = 80)

        assertTrue(throttle.shouldProcess(nowMs = 0))
    }

    @Test
    fun `a frame arriving before the interval elapses is dropped`() {
        val throttle = FrameThrottle(minIntervalMs = 80)
        throttle.shouldProcess(nowMs = 0)

        assertFalse(throttle.shouldProcess(nowMs = 79))
    }

    @Test
    fun `a frame arriving once the interval elapses is processed`() {
        val throttle = FrameThrottle(minIntervalMs = 80)
        throttle.shouldProcess(nowMs = 0)

        assertTrue(throttle.shouldProcess(nowMs = 80))
    }

    @Test
    fun `a dropped frame does not reset the interval`() {
        val throttle = FrameThrottle(minIntervalMs = 80)
        throttle.shouldProcess(nowMs = 0)
        throttle.shouldProcess(nowMs = 40) // dropped

        assertFalse(throttle.shouldProcess(nowMs = 79))
        assertTrue(throttle.shouldProcess(nowMs = 80))
    }
}
