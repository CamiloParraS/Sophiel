package dev.sophiel.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NsfwClassifierTest {

    @Test
    fun `unsafeScore sums hentai, porn and sexy`() {
        // drawings, hentai, neutral, porn, sexy
        assertEquals(0.8f, NsfwClassifier.unsafeScore(floatArrayOf(0.1f, 0.2f, 0.1f, 0.3f, 0.3f)), 1e-6f)
    }

    @Test
    fun `unsafeScore ignores drawings and neutral`() {
        assertEquals(0f, NsfwClassifier.unsafeScore(floatArrayOf(0.6f, 0f, 0.4f, 0f, 0f)), 1e-6f)
    }
}
