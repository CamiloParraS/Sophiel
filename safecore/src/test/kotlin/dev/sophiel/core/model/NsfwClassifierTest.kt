package dev.sophiel.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NsfwClassifierTest {

    @Test
    fun `dequantize maps a raw byte to a probability using scale`() {
        assertEquals(0f, NsfwClassifier.dequantize(0, scale = 0.00390625f, zeroPoint = 0), 1e-6f)
        assertEquals(0.5f, NsfwClassifier.dequantize(128, scale = 0.00390625f, zeroPoint = 0), 1e-6f)
        assertEquals(0.99609375f, NsfwClassifier.dequantize(255, scale = 0.00390625f, zeroPoint = 0), 1e-6f)
    }

    @Test
    fun `dequantize subtracts a non-zero zero point before scaling`() {
        assertEquals(0f, NsfwClassifier.dequantize(128, scale = 0.00787402f, zeroPoint = 128), 1e-6f)
        assertEquals(-1f, NsfwClassifier.dequantize(0, scale = 0.00787401575f, zeroPoint = 127), 1e-4f)
    }
}
