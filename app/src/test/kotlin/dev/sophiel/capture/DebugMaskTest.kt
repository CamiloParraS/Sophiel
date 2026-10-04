package dev.sophiel.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DebugMaskTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val skin = 0xFFE0B090.toInt()
    private val grey = 0xFF808080.toInt()
    private val foliage = 0xFF4A7A3A.toInt()

    /** Random hard-edged blocks of [size] px: a stand-in for a photo's large shapes. */
    private fun blocks(seed: Int, size: Int): (Int, Int) -> Int = { x, y ->
        val v = java.util.Random(seed * 7919L + (x / size) * 104729L + y / size).nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    /**
     * One middle-left tile of a 1080x2400 screen as the 360x800 capture sees it (3x down), with
     * the mask at window alpha over [content] (screen coords), or bare content if [masked] is false.
     * Worst case for the check: each capture pixel is the raw pattern pixel, grain and all.
     */
    private fun tile(content: (Int, Int) -> Int, masked: Boolean = true, misregister: Int = 0) = DebugMask.Samples(
        screenPoints().map { (sx, sy) ->
            val mask = DebugMask.PATTERN[sy % DebugMask.SIDE * DebugMask.SIDE + sx % DebugMask.SIDE]
            val c = content(sx, sy)
            fun mix(shift: Int): Int {
                val m = mask shr shift and 0xFF
                val v = c shr shift and 0xFF
                return if (masked) (m * OVERLAY_ALPHA + v * (1 - OVERLAY_ALPHA)).toInt() else v
            }
            (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }.toIntArray(),
        screenPoints().map { (sx, sy) -> DebugMask.expectedAt(sx + misregister, sy + misregister) }.toFloatArray(),
    )

    private fun screenPoints() = DebugMask.samplePoints(180, 266).map { (x, y) ->
        ((x + 0.5f) * 3).toInt() to ((y + 267 + 0.5f) * 3).toInt()
    }

    @Test fun `mask over any content reads as masked`() {
        for (c in listOf(black, white, skin, grey, foliage, 0xFF00FF00.toInt())) {
            assertTrue("%08x".format(c), DebugMask.looksMasked(tile({ _, _ -> c })))
        }
        for (seed in 0 until 50) for (size in listOf(4, 24, 96)) {
            val s = tile(blocks(seed, size))
            assertTrue("seed=$seed size=$size ${DebugMask.describe(s)}", DebugMask.looksMasked(s))
        }
    }

    @Test fun `a pixel or two of misregistration still reads as masked`() {
        assertTrue(DebugMask.looksMasked(tile(blocks(1, 24), misregister = 2)))
    }

    @Test fun `bare content does not, whatever its colour`() {
        // Any flat colour, including the mask's own mean: the check is about the pattern, not colour.
        val maskMean = DebugMask.PATTERN.let { px ->
            fun m(shift: Int) = px.sumOf { it shr shift and 0xFF } / px.size
            (0xFF shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
        }
        for (c in listOf(skin, white, grey, foliage, maskMean)) {
            assertFalse("%08x".format(c), DebugMask.looksMasked(tile({ _, _ -> c }, masked = false)))
        }
        // Shapes of every size: unrelated to our seeded blobs, so never over the threshold here.
        for (seed in 0 until 200) for (size in listOf(4, 24, 96)) {
            val s = tile(blocks(seed, size), masked = false)
            assertFalse("seed=$seed size=$size ${DebugMask.describe(s)}", DebugMask.looksMasked(s))
        }
    }

    @Test fun `a debug pill over a few samples still reads as masked`() {
        val s = tile(blocks(3, 24))
        for (i in 0 until 6) s.pixels[i] = 0xFF1B1B1B.toInt()
        assertTrue(DebugMask.looksMasked(s))
    }

    @Test fun `the pattern holds its brightness and tiles seamlessly`() {
        for (tint in DebugMask.TINTS.values) {
            val px = DebugMask.camo(32, 128, 0xFFFFFF and tint)
            val peak = maxOf(tint shr 16 and 0xFF, tint shr 8 and 0xFF, tint and 0xFF)
            val brightest = px.sumOf { maxOf(it shr 16 and 0xFF, it shr 8 and 0xFF, it and 0xFF) } / px.size.toDouble()
            assertEquals(128.0 * peak / 255, brightest, 4.0)
        }
        fun red(x: Int, y: Int) = DebugMask.PATTERN[y * DebugMask.SIDE + x] shr 16 and 0xFF
        fun step(x0: Int, x1: Int) = (0 until DebugMask.SIDE).sumOf { abs(red(x0, it) - red(x1, it)) } / 256.0
        assertTrue(step(255, 0) < 1.5 * step(127, 128)) // the wrap edge is no seam
    }

    @Test fun `the pattern has no pure black pixel, for every tint`() {
        for (tint in DebugMask.TINTS.values) assertTrue(DebugMask.camo(32, 100, tint).none { it and 0xFFFFFF == 0 })
    }

    @Test fun `sampling skips the centre`() {
        assertTrue(DebugMask.samplePoints(120, 120).none { (x, y) -> x in 30..90 && y in 30..90 })
        assertEquals(108, DebugMask.samplePoints(120, 120).size)
    }
}
