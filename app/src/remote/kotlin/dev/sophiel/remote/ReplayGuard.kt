package dev.sophiel.remote

import kotlin.math.abs

/** D79: the Child phone rejects a message older than 2 min or an id seen in the last 2 min. */
class ReplayGuard(private val clock: () -> Long) {
    private val seen = LinkedHashMap<String, Long>() // id -> time first seen; insertion order = age order

    fun accept(id: String, ts: Long): Boolean {
        val now = clock()
        val it = seen.entries.iterator()
        while (it.hasNext() && now - it.next().value > WINDOW_MS) it.remove()
        // An id older than the window was dropped above, but its ts is then stale too, so it cannot come back.
        if (abs(now - ts) > WINDOW_MS || id in seen) return false
        seen[id] = now
        return true
    }

    private companion object {
        const val WINDOW_MS = 2 * 60_000L
    }
}
