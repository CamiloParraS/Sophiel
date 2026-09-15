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
    fun `isSkinPixel is false for a near-black pixel even with skin-like chroma`() {
        // Inside the Cr/Cb box, but Y=37 is below the luma floor.
        assertFalse(SkinGate.isSkinPixel(r = 60, g = 30, b = 15))
    }

    @Test
    fun `isSkinPixel still accepts dark, low-chroma pale, and dimly lit skin`() {
        assertTrue(SkinGate.isSkinPixel(r = 120, g = 80, b = 60)) // dark skin
        // Pale/rendered skin at Cr-Cb~16: the real test-feed median band (8-15) is this close to
        // neutral, so any minimum-chroma guard gates explicit images (D19).
        assertTrue(SkinGate.isSkinPixel(r = 230, g = 215, b = 205))
        assertTrue(SkinGate.isSkinPixel(r = 80, g = 55, b = 43)) // low light, Y=61
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
