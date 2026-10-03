package dev.sophiel.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugMaskTest {
    /** What the capture sees: the mask at window alpha over [content]. */
    private fun captured(content: Int): Int {
        fun ch(c: Int, shift: Int) = c shr shift and 0xFF
        fun mix(shift: Int) =
            (ch(DebugMask.COLOR, shift) * OVERLAY_ALPHA + ch(content, shift) * (1 - OVERLAY_ALPHA)).toInt()
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    @Test fun `mask over any content reads as masked`() {
        for (content in listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFE0B090.toInt(), 0xFF00FF00.toInt())) {
            assertTrue(DebugMask.looksMasked(IntArray(64) { captured(content) }))
        }
    }

    @Test fun `bare content does not`() {
        assertFalse(DebugMask.looksMasked(IntArray(64) { 0xFFE0B090.toInt() })) // skin
        assertFalse(DebugMask.looksMasked(IntArray(64) { 0xFFFFFFFF.toInt() })) // white UI
    }

    @Test fun `a pill over part of the mask still reads as masked`() {
        assertTrue(DebugMask.looksMasked(IntArray(64) { if (it < 20) 0xFF1B1B1B.toInt() else captured(-1) }))
    }

    @Test fun `the mask is never pure black`() {
        assertTrue(DebugMask.COLOR and 0xFFFFFF != 0)
    }
}
