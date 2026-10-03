package dev.sophiel.core.tile

import dev.sophiel.core.Severity
import dev.sophiel.core.TileVerdict
import dev.sophiel.core.tile.TileState.CLEAR
import dev.sophiel.core.tile.TileState.MASKED
import dev.sophiel.core.tile.TileState.PROBING
import dev.sophiel.core.tile.TileState.REVEALED

enum class TileState { CLEAR, MASKED, PROBING, REVEALED }

/**
 * Per-tile mask state machine (SPEC.md §3.4). Pure logic: time is passed in
 * (ms, any monotonic clock) and pixels are never seen.
 *
 * Our mask is itself captured, so a masked tile's score is meaningless. The
 * tracker therefore ignores it, locks the hash of the content it masked, and
 * only uncovers a tile through a probe. It owns all timing; [Severity] comes
 * from the stateless policy.
 *
 * Per frame: [onTile] for each tile as its verdict arrives, then [endFrame].
 * Not thread-safe: drive it from one frame stream.
 */
class TileMaskTracker(private var cols: Int, rows: Int) {

    private class Tile {
        var state = CLEAR
        var enteredAt = 0L // when [state] began; shifted on resume so pauses don't count
        var flaggedStreak = 0
        var lockedHash = 0L
        var lastHash: Long? = null
        var hashChanged = false // since the previous frame
    }

    private var tiles = List(cols * rows) { Tile() } // row-major on the frame's grid
    private var pausedAt: Long? = null

    val size get() = tiles.size

    operator fun get(index: Int): TileState = tiles[index].state

    /**
     * @param showsMask the captured tile still shows our mask (a pixel check by the caller)
     * @return true when this starts a new masking episode (CLEAR → MASKED), which is logged
     *         once; a re-mask after a probe returns false
     */
    fun onTile(now: Long, verdict: TileVerdict, showsMask: Boolean): Boolean {
        if (pausedAt != null) return false
        val tile = tiles[verdict.index]
        val flagged = verdict.severity == Severity.EXPLICIT
        tile.hashChanged = tile.lastHash != verdict.hash
        tile.lastHash = verdict.hash
        when (tile.state) {
            CLEAR -> {
                tile.flaggedStreak = if (flagged) tile.flaggedStreak + 1 else 0
                if (tile.flaggedStreak >= ENGAGE_FRAMES) {
                    tile.mask(verdict.hash, now)
                    return true
                }
            }
            // A frame still showing the mask is not a probe frame; endFrame re-masks it on timeout.
            // ponytail: an unchanged hash still costs a classification (cache hit when within its LRU);
            // pass the locked hashes to the Detector if probes show up in the per-tile cost.
            PROBING -> when {
                showsMask -> Unit
                verdict.hash == tile.lockedHash || flagged -> tile.mask(verdict.hash, now)
                else -> tile.enter(CLEAR, now)
            }
            MASKED, REVEALED -> Unit // the capture sees our mask (or a revealed tile): its score means nothing
        }
        return false
    }

    /** Time-based transitions and probe triggers. Call once per frame, after its tiles. */
    fun endFrame(now: Long) {
        if (pausedAt != null) return
        for ((index, tile) in tiles.withIndex()) {
            val age = now - tile.enteredAt
            when (tile.state) {
                MASKED -> if (age >= MIN_PROBE_GAP_MS && neighboursSayProbe(index, age)) tile.enter(PROBING, now)
                REVEALED -> if (age >= REVEAL_MS) tile.enter(PROBING, now)
                CLEAR, PROBING -> Unit
            }
        }
        for (tile in tiles) tile.hashChanged = false // after the loop: neighbours read these flags
        expireProbes(now)
    }

    /**
     * D25: a masked tile probes only when at least half of its CLEAR neighbours (4-adjacent)
     * changed this frame, so scrolling elsewhere on screen never uncovers it. With no CLEAR
     * neighbour (Light, or every neighbour masked) there is nothing to watch: the 2 s timer,
     * counted per tile since it was masked or last probed.
     */
    private fun neighboursSayProbe(index: Int, age: Long): Boolean {
        val clear = listOfNotNull(
            (index - cols).takeIf { it >= 0 },
            (index + cols).takeIf { it < tiles.size },
            (index - 1).takeIf { index % cols > 0 },
            (index + 1).takeIf { index % cols < cols - 1 },
        ).map { tiles[it] }.filter { it.state == CLEAR }
        return if (clear.isEmpty()) age >= PROBE_TIMER_MS else clear.count { it.hashChanged } * 2 >= clear.size
    }

    /**
     * Re-masks every probe with no valid frame within 300 ms. [endFrame] does this too; call it
     * on a timer while a tile is PROBING, because a screen that goes static after the mask is
     * lifted delivers no frame. Never starts a probe.
     */
    fun expireProbes(now: Long) {
        if (pausedAt != null) return
        for (tile in tiles) if (tile.state == PROBING && now - tile.enteredAt >= PROBE_VALID_MS) tile.enter(MASKED, now)
    }

    /** How long a probe may wait for a captured frame without our mask before re-masking. */
    val probeTimeoutMs get() = PROBE_VALID_MS

    /** Parent reveal (the caller checks the PIN): every MASKED tile is uncovered for 5 s. */
    fun reveal(now: Long) {
        // While paused (the Parent is on our screen) the 5 s start at resume.
        for (tile in tiles) if (tile.state == MASKED) tile.enter(REVEALED, pausedAt ?: now)
    }

    /** A Sophiel screen is in front: freeze states and timers, ignore frames until [resume]. */
    fun pause(now: Long) {
        if (pausedAt == null) pausedAt = now
    }

    fun resume(now: Long) {
        val since = pausedAt ?: return
        for (tile in tiles) tile.enteredAt += now - since
        pausedAt = null
    }

    /** The grid changed (rotation or preset): every tile starts over CLEAR. */
    fun reset(cols: Int, rows: Int) {
        this.cols = cols
        tiles = List(cols * rows) { Tile() }
    }

    private fun Tile.enter(next: TileState, now: Long) {
        state = next
        enteredAt = now
        flaggedStreak = 0
    }

    private fun Tile.mask(hash: Long, now: Long) {
        enter(MASKED, now)
        lockedHash = hash
    }

    private companion object {
        const val ENGAGE_FRAMES = 2 // the old PolicyEngine value; tuned in ticket 07
        const val MIN_PROBE_GAP_MS = 1_000L
        const val PROBE_TIMER_MS = 2_000L
        const val PROBE_VALID_MS = 300L
        const val REVEAL_MS = 5_000L
    }
}
