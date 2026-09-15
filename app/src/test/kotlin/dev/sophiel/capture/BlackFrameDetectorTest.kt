package dev.sophiel.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlackFrameDetectorTest {

    @Test
    fun `an all-black opaque frame is detected as black`() {
        val blackOpaque = -0x1000000 // 0xFF000000: alpha 255, RGB 0
        val pixels = IntArray(64) { blackOpaque }

        assertTrue(BlackFrameDetector.isAllBlack(pixels))
    }

    @Test
    fun `a frame with a single non-black pixel is not black`() {
        val pixels = IntArray(64) { -0x1000000 }
        pixels[30] = -0x10000 // opaque red

        assertFalse(BlackFrameDetector.isAllBlack(pixels))
    }

    @Test
    fun `ordinary UI content is not black`() {
        val pixels = IntArray(64) { i -> -0x1000000 or (i * 4) } // varying blue channel

        assertFalse(BlackFrameDetector.isAllBlack(pixels))
    }

    @Test
    fun `an empty pixel array counts as black (vacuously, never occurs in practice)`() {
        assertTrue(BlackFrameDetector.isAllBlack(IntArray(0)))
    }
}
