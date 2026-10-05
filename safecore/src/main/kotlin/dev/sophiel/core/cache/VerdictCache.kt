package dev.sophiel.core.cache

import dev.sophiel.core.TileVerdict
import dev.sophiel.core.gate.PerceptualHash

/**
 * In-memory LRU cache of [TileVerdict]s keyed by [PerceptualHash], so an identical
 * frame reuses a prior verdict instead of re-running the classifier.
 *
 * **Exact hashes only.** This cache used to hit on any stored hash within a
 * Hamming distance of 5. A 64-bit dHash of a whole phone screen is a 9x8
 * grid, so one feed thumbnail covers about one cell and swapping its contents
 * flips at most two bits. A fuzzy lookup therefore returned the previous
 * SAFE verdict for precisely the frame whose only change was the image that
 * mattered: the cache's false-hit mode was "miss unsafe content", which is the
 * unrecoverable direction (SPEC.md §6.1). Exact matching gives up hits on
 * near-duplicates and keeps the ones that are sound.
 */
class VerdictCache(private val capacity: Int = 256) {

    private val entries = object : LinkedHashMap<Long, TileVerdict>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, TileVerdict>) = size > capacity
    }

    /** Returns the cached [TileVerdict] stored under exactly [hash], or null on a miss. */
    fun get(hash: Long): TileVerdict? = entries[hash] // access-order map: a read refreshes LRU recency

    /**
     * Stores [verdict] under [hash], evicting the least-recently-used entry if over [capacity].
     * Never under 0: every flat tile hashes to 0 (a black FLAG_SECURE tile too), and so does any
     * tile that never brightens left to right. A dark tile scored EXPLICIT during an app switch
     * was cached under 0, then served to every black probe tile after it (Device B, 2026-10-05).
     */
    fun put(hash: Long, verdict: TileVerdict) {
        if (hash == 0L) return
        entries[hash] = verdict
    }

    /** Number of entries currently cached. */
    fun size(): Int = entries.size
}
