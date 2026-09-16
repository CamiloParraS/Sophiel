package dev.sophiel.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlackFrameDetectorTest {

    @Test
    fun `an all-black opaque frame is detected as black`() {
        val blackOpaque = -0x1000000 // 0xFF000000: alpha 255, RGB 0
        val pixels = IntArray(64) { blackOpaque }

        assertTrue(BlackFrameDetector.isMostlyBlack(pixels))
    }

    @Test
    fun `a frame with one non-black pixel is still counted as black (tolerates the status bar)`() {
        val pixels = IntArray(64) { -0x1000000 }
        pixels[30] = -0x10000 // opaque red

        assertTrue(BlackFrameDetector.isMostlyBlack(pixels))
    }

    @Test
    fun `a frame that is mostly non-black is not black`() {
        // 20 of 64 pixels black (31%) - well under the 90% threshold.
        val pixels = IntArray(64) { i -> if (i < 20) -0x1000000 else -0x10000 }

        assertFalse(BlackFrameDetector.isMostlyBlack(pixels))
    }

    @Test
    fun `ordinary UI content is not black`() {
        val pixels = IntArray(64) { i -> -0x1000000 or (i * 4) } // varying blue channel

        assertFalse(BlackFrameDetector.isMostlyBlack(pixels))
    }

    @Test
    fun `a FLAG_SECURE frame with a thin non-black status bar strip is still black`() {
        // ~95% black content below a status bar strip that's never black.
        val pixels = IntArray(200) { i -> if (i < 10) -0x10000 else -0x1000000 }

        assertTrue(BlackFrameDetector.isMostlyBlack(pixels))
    }

    @Test
    fun `an empty pixel array counts as black (vacuously, never occurs in practice)`() {
        assertTrue(BlackFrameDetector.isMostlyBlack(IntArray(0)))
    }
}
