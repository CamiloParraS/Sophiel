package dev.sophiel.core.gate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualHashTest {

    @Test
    fun `hashGrayscale sets a bit when a pixel is darker than its right neighbour`() {
        // 2 rows x 3 cols (HASH_SIZE+1 wide), strictly increasing brightness left-to-right.
        val pixels = intArrayOf(
            gray(10), gray(50), gray(90),
            gray(10), gray(50), gray(90),
        )

        val hash = PerceptualHash.hashGrayscale(pixels, width = 3, height = 2)

        // Every comparison is left < right, so every bit is set: 2 rows x 2 bits = 0b1111
        assertEquals(0b1111L, hash)
    }

    @Test
    fun `hashGrayscale clears a bit when a pixel is not darker than its right neighbour`() {
        val pixels = intArrayOf(
            gray(90), gray(50), gray(10),
            gray(90), gray(50), gray(10),
        )

        val hash = PerceptualHash.hashGrayscale(pixels, width = 3, height = 2)

        assertEquals(0L, hash)
    }

    @Test
    fun `hammingDistance is zero for identical hashes`() {
        assertEquals(0, PerceptualHash.hammingDistance(0b1010L, 0b1010L))
    }

    @Test
    fun `hammingDistance counts differing bits`() {
        assertEquals(1, PerceptualHash.hammingDistance(0b1010L, 0b1000L))
        assertEquals(3, PerceptualHash.hammingDistance(0b000L, 0b111L))
    }

    @Test
    fun `hammingDistance is symmetric`() {
        assertTrue(PerceptualHash.hammingDistance(1L, 200L) == PerceptualHash.hammingDistance(200L, 1L))
    }

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
}
