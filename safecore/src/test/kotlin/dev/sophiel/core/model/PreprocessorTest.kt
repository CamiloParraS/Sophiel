package dev.sophiel.core.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PreprocessorTest {

    @Test
    fun `packRgb emits RGB floats in 0-1 in row order`() {
        val pixels = intArrayOf(
            argb(0, 51, 255),
            argb(102, 204, 153),
        )

        val buffer = Preprocessor.packRgb(pixels)
        val floats = FloatArray(buffer.remaining() / Float.SIZE_BYTES) { buffer.getFloat() }

        assertArrayEquals(floatArrayOf(0f, 0.2f, 1f, 0.4f, 0.8f, 0.6f), floats, 1e-6f)
    }

    @Test
    fun `packRgb drops the alpha channel`() {
        val pixels = intArrayOf((0x12 shl 24) or argb(255, 0, 255))

        val buffer = Preprocessor.packRgb(pixels)

        assertEquals(3 * Float.SIZE_BYTES, buffer.remaining())
    }

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
