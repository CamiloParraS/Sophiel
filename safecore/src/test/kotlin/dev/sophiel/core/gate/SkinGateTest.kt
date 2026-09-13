package dev.sophiel.core.gate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinGateTest {

    @Test
    fun `isSkinPixel is true for a mid skin tone`() {
        // A typical skin-tone RGB, well inside the YCbCr Cr in[133,173], Cb in[77,127] band.
        assertTrue(SkinGate.isSkinPixel(r = 220, g = 170, b = 140))
    }

    @Test
    fun `isSkinPixel is false for a saturated blue`() {
        assertFalse(SkinGate.isSkinPixel(r = 0, g = 0, b = 255))
    }

    @Test
    fun `isSkinPixel is false for neutral gray`() {
        assertFalse(SkinGate.isSkinPixel(r = 128, g = 128, b = 128))
    }

    @Test
    fun `skinRatio counts the fraction of skin pixels`() {
        val skin = argb(220, 170, 140)
        val notSkin = argb(0, 0, 255)
        val pixels = intArrayOf(skin, skin, notSkin, notSkin) // 2 of 4 = 0.5

        assertEquals(0.5f, SkinGate.skinRatio(pixels), 1e-6f)
    }

    @Test
    fun `shouldClassify rejects a frame below minRatio`() {
        val gate = SkinGate(minRatio = 0.5f)
        val notSkin = argb(0, 0, 255)
        val pixels = IntArray(100) { notSkin }

        assertFalse(gate.shouldClassifyPixels(pixels))
    }

    @Test
    fun `shouldClassify accepts a frame at or above minRatio`() {
        val gate = SkinGate(minRatio = 0.5f)
        val skin = argb(220, 170, 140)
        val pixels = IntArray(100) { skin }

        assertTrue(gate.shouldClassifyPixels(pixels))
    }

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
