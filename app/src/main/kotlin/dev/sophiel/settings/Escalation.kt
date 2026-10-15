package dev.sophiel.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * D58, pure: [HITS] masked episodes with the same app within a rolling [WINDOW_MS] block it for [BLOCK_MS]. The caller
 * filters what never counts (blank, trusted, excluded apps, unanalyzable stretches). Hits are in memory; block end
 * times are wall clock, handed to [write] and read back by [read] so they survive a reboot.
 */
class Escalation(private val wall: () -> Long, read: () -> Map<String, Long>, private val write: (Map<String, Long>) -> Unit) {
    private val hits = mutableMapOf<String, List<Long>>()
    private val _blocks = MutableStateFlow(read())

    /** package -> end time, may hold expired ones: compare with the clock ([until]). For Compose to recompose on a change. */
    val blocks: StateFlow<Map<String, Long>> = _blocks.asStateFlow()

    /** A masked episode of [pkg]. True when it makes the block start (the count restarts). */
    @Synchronized fun hit(pkg: String): Boolean {
        val now = wall()
        if (until(pkg) != null) return false
        val recent = hits[pkg].orEmpty().filter { now - it < WINDOW_MS } + now
        if (recent.size < HITS) {
            hits[pkg] = recent
            return false
        }
        hits.remove(pkg)
        set(_blocks.value + (pkg to now + BLOCK_MS))
        return true
    }

    /** The block's end time if [pkg] is blocked now. */
    fun until(pkg: String): Long? = _blocks.value[pkg]?.takeIf { it > wall() }

    /** The PIN on the block screen: the block ends and the count restarts. */
    @Synchronized fun end(pkg: String) {
        hits.remove(pkg)
        set(_blocks.value - pkg)
    }

    /** A PIN Stop, or the switch turned off. */
    @Synchronized fun clear() {
        hits.clear()
        set(emptyMap())
    }

    private fun set(blocks: Map<String, Long>) {
        val live = blocks.filterValues { it > wall() } // expired ones leave the file with the next change
        _blocks.value = live
        write(live)
    }

    companion object {
        const val HITS = 3
        const val WINDOW_MS = 10 * 60_000L
        const val BLOCK_MS = 15 * 60_000L
    }
}
