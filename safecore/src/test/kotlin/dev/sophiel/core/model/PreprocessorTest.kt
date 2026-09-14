package dev.sophiel.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PreprocessorTest {

    @Test
    fun `packRgb extracts RGB bytes from ARGB pixels in row order`() {
        val pixels = intArrayOf(
            argb(10, 20, 30),
            argb(40, 50, 60),
        )

        val buffer = Preprocessor.packRgb(pixels)
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        assertEquals(listOf(10, 20, 30, 40, 50, 60), bytes.map { it.toInt() and 0xFF })
    }

    @Test
    fun `packRgb drops the alpha channel`() {
        val pixels = intArrayOf((0x12 shl 24) or argb(1, 2, 3))

        val buffer = Preprocessor.packRgb(pixels)
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        assertEquals(listOf(1, 2, 3), bytes.map { it.toInt() and 0xFF })
    }

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
