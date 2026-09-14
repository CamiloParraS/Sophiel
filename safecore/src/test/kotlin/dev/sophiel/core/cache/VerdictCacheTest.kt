package dev.sophiel.core.cache

import dev.sophiel.core.Severity
import dev.sophiel.core.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerdictCacheTest {

    private val verdict = Verdict(Severity.SAFE, score = 0.1f, gated = false, cacheHit = false, latencyMs = 5)

    @Test
    fun `get on an empty cache is a miss`() {
        val cache = VerdictCache()

        assertNull(cache.get(hash = 0x00L))
    }

    @Test
    fun `get returns the verdict for an exact hash match`() {
        val cache = VerdictCache()

        cache.put(0x1234L, verdict)

        assertEquals(verdict, cache.get(0x1234L))
    }

    @Test
    fun `get hits when the hash is within the Hamming distance threshold`() {
        val cache = VerdictCache()
        cache.put(0b0000_0000L, verdict)

        // 4 bits differ; within the <=5 distance threshold.
        assertEquals(verdict, cache.get(0b0000_1111L))
    }

    @Test
    fun `get misses when the hash is beyond the Hamming distance threshold`() {
        val cache = VerdictCache()
        cache.put(0b0000_0000L, verdict)

        // 6 bits differ; beyond the <=5 distance threshold.
        assertNull(cache.get(0b0011_1111L))
    }

    @Test
    fun `put evicts the least recently used entry once over capacity`() {
        val cache = VerdictCache(capacity = 2)

        cache.put(0xF0F0_0000_0000_0000UL.toLong(), verdict) // far apart hashes so lookups don't fuzzy-match each other
        cache.put(0x0F0F_0000_0000_0000UL.toLong(), verdict)
        cache.put(0x00FF_0000_0000_0000UL.toLong(), verdict)

        assertNull(cache.get(0xF0F0_0000_0000_0000UL.toLong()))
        assertEquals(2, cache.size())
    }

    @Test
    fun `get refreshes an entry's recency`() {
        val cache = VerdictCache(capacity = 2)
        val a = 0xF0F0_0000_0000_0000UL.toLong()
        val b = 0x0F0F_0000_0000_0000UL.toLong()
        val c = 0x00FF_0000_0000_0000UL.toLong()

        cache.put(a, verdict)
        cache.put(b, verdict)
        cache.get(a) // touch a, making b the least recently used
        cache.put(c, verdict)

        assertNull(cache.get(b))
        assertEquals(verdict, cache.get(a))
    }
}
