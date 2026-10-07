package dev.sophiel.capture

/**
 * Drops frames arriving faster than [minIntervalMs] apart: a CPU floor, no longer a SPEC.md rule.
 * A waiting probe skips it (D50). Not thread-safe; call from a single frame-delivery thread.
 */
class FrameThrottle(private val minIntervalMs: Long = 80L) {
    var lastProcessedMs: Long? = null
        private set

    fun shouldProcess(nowMs: Long): Boolean {
        val last = lastProcessedMs
        if (last != null && nowMs - last < minIntervalMs) return false
        lastProcessedMs = nowMs
        return true
    }
}
