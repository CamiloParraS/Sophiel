package dev.sophiel.core.cache

import dev.sophiel.core.Severity
import dev.sophiel.core.TileVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerdictCacheTest {

    private val verdict = TileVerdict(0, Severity.SAFE, score = 0.1f, gated = false, cacheHit = false, hash = 0)

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
    fun `a flat tile's hash 0 is never cached`() {
        val cache = VerdictCache()
        cache.put(0L, verdict.copy(severity = Severity.EXPLICIT, score = 0.86f))

        assertNull(cache.get(0L))
    }

    @Test
    fun `get misses on a near-duplicate hash`() {
        val cache = VerdictCache()
        cache.put(0b0001_0000L, verdict)

        // One flipped bit: the signature of a single feed thumbnail changing contents.
        // It must re-run the classifier, not reuse the neighbour's verdict.
        assertNull(cache.get(0b0001_0001L))
        assertNull(cache.get(0b0001_1111L))
    }

    @Test
    fun `put evicts the least recently used entry once over capacity`() {
        val cache = VerdictCache(capacity = 2)

        cache.put(0xF0F0_0000_0000_0000UL.toLong(), verdict)
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
