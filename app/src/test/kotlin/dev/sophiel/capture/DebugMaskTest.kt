package dev.sophiel.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugMaskTest {
    /**
     * What the capture sees at 48 sample points: the noise at window alpha over [content].
     * Worst case: real grain, no averaging from the capture's downscale.
     */
    private fun captured(content: Int) = IntArray(48) { k ->
        val mask = DebugMask.NOISE[k * 37 % DebugMask.NOISE.size]
        fun ch(c: Int, shift: Int) = c shr shift and 0xFF
        fun mix(shift: Int) = (ch(mask, shift) * OVERLAY_ALPHA + ch(content, shift) * (1 - OVERLAY_ALPHA)).toInt()
        (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val skin = 0xFFE0B090.toInt()
    private val grey = 0xFF808080.toInt()
    private val foliage = 0xFF4A7A3A.toInt()

    @Test fun `mask over any content reads as masked`() {
        for (content in listOf(black, white, skin, grey, foliage, 0xFF00FF00.toInt())) {
            assertTrue("%08x".format(content), DebugMask.looksMasked(captured(content)))
        }
    }

    @Test fun `bare content does not, grey included`() {
        // 0xFF6B5E5F: the closest bare tile in the 2026-10-03 device run (d=73)
        for (content in listOf(skin, white, grey, foliage, 0xFF9E9E9E.toInt(), 0xFF606060.toInt(), 0xFF6B5E5F.toInt())) {
            assertFalse("%08x".format(content), DebugMask.looksMasked(IntArray(48) { content }))
        }
    }

    @Test fun `a debug pill over a few samples still reads as masked`() {
        val pixels = captured(white)
        for (i in 0 until 6) pixels[i] = 0xFF1B1B1B.toInt()
        assertTrue(DebugMask.looksMasked(pixels))
    }

    @Test fun `the noise is tinted well off grey`() {
        val (r, g, b) = DebugMask.MEAN
        assertTrue(minOf(r, b) - g > 60)
    }

    @Test fun `the noise has no pure black pixel`() {
        assertTrue(DebugMask.NOISE.none { it and 0xFFFFFF == 0 })
    }

    @Test fun `sampling skips the centre`() {
        assertTrue(DebugMask.samplePoints(100, 100).none { (x, y) -> x in 30..70 && y in 30..70 })
        assertTrue(DebugMask.samplePoints(100, 100).size == 48)
    }
}
