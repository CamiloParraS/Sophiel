package dev.sophiel.core.cache

import dev.sophiel.core.Verdict
import dev.sophiel.core.gate.PerceptualHash

/**
 * In-memory LRU cache of [Verdict]s keyed by [PerceptualHash]. A lookup hits
 * on any stored hash within [HIT_DISTANCE] Hamming distance, so near-
 * duplicate frames (e.g. static content re-scrolled into view) reuse a prior
 * verdict instead of re-running the classifier.
 */
class VerdictCache(private val capacity: Int = 256) {

    private val entries = object : LinkedHashMap<Long, Verdict>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Verdict>) = size > capacity
    }

    /** Returns the cached [Verdict] for a hash within [HIT_DISTANCE] of [hash], or null on a miss. */
    fun get(hash: Long): Verdict? {
        val key = entries.keys.firstOrNull { PerceptualHash.hammingDistance(it, hash) <= HIT_DISTANCE } ?: return null
        return entries[key] // re-touches the entry, refreshing LRU order
    }

    /** Stores [verdict] under [hash], evicting the least-recently-used entry if over [capacity]. */
    fun put(hash: Long, verdict: Verdict) {
        entries[hash] = verdict
    }

    /** Number of entries currently cached. */
    fun size(): Int = entries.size

    private companion object {
        const val HIT_DISTANCE = 5
    }
}
