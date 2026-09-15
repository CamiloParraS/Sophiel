package dev.sophiel.capture

/**
 * Drops frames arriving faster than [minIntervalMs] apart (SPEC.md M3: "process at most one
 * frame per 80 ms; drop the rest"). Not thread-safe; call from a single frame-delivery thread.
 */
class FrameThrottle(private val minIntervalMs: Long = 80L) {
    private var lastProcessedMs: Long? = null

    fun shouldProcess(nowMs: Long): Boolean {
        val last = lastProcessedMs
        if (last != null && nowMs - last < minIntervalMs) return false
        lastProcessedMs = nowMs
        return true
    }
}
