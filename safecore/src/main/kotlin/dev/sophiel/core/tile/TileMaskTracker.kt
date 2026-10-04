package dev.sophiel.core.tile

import dev.sophiel.core.Severity
import dev.sophiel.core.TileVerdict
import dev.sophiel.core.tile.TileState.CLEAR
import dev.sophiel.core.tile.TileState.MASKED
import dev.sophiel.core.tile.TileState.PROBING

enum class TileState { CLEAR, MASKED, PROBING }

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
        var wastedProbes = 0 // probes in a row that ended re-masked; backs off the next one (ticket 14)
        var probeDue = false // the neighbour rule fired inside the gap: probe once it ends (ticket 14)
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
                verdict.hash == tile.lockedHash || flagged -> tile.remask(verdict.hash, now)
                else -> {
                    tile.enter(CLEAR, now)
                    tile.wastedProbes = 0
                }
            }
            MASKED -> Unit // the capture sees our mask: its score means nothing
        }
        return false
    }

    /** Time-based transitions and probe triggers. Call once per frame, after its tiles. */
    fun endFrame(now: Long) {
        if (pausedAt != null) return
        for ((index, tile) in tiles.withIndex()) {
            val age = now - tile.enteredAt
            when (tile.state) {
                // The timer path only says yes past the gap, so only a neighbour change is ever owed.
                MASKED -> if (tile.probeDue || neighboursSayProbe(index, age)) {
                    if (age >= tile.probeGap()) tile.enter(PROBING, now) else tile.probeDue = true
                }
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
     * counted per tile since it was masked or last probed, and never shorter than its probe gap.
     */
    private fun neighboursSayProbe(index: Int, age: Long): Boolean {
        val clear = listOfNotNull(
            (index - cols).takeIf { it >= 0 },
            (index + cols).takeIf { it < tiles.size },
            (index - 1).takeIf { index % cols > 0 },
            (index + 1).takeIf { index % cols < cols - 1 },
        ).map { tiles[it] }.filter { it.state == CLEAR }
        val timer = maxOf(PROBE_TIMER_MS, tiles[index].probeGap())
        return if (clear.isEmpty()) age >= timer else clear.count { it.hashChanged } * 2 >= clear.size
    }

    /**
     * Re-masks every probe with no valid frame within 300 ms. [endFrame] does this too; call it
     * on a timer while a tile is PROBING, because a screen that goes static after the mask is
     * lifted delivers no frame. Never starts a probe.
     */
    fun expireProbes(now: Long) {
        if (pausedAt != null) return
        for (tile in tiles) if (tile.state == PROBING && now - tile.enteredAt >= PROBE_VALID_MS) tile.remask(tile.lockedHash, now)
    }

    /**
     * When the earliest owed probe falls due, or null. A neighbour change inside a tile's gap is
     * owed a probe when the gap ends, but the screen may have gone static by then and no frame
     * would start it (the tile stuck masked, seen on Device B): call [startDueProbes] at this time.
     * Lifting the mask changes the screen, so the probe frame does arrive.
     */
    fun nextDueProbeAt(): Long? =
        if (pausedAt != null) null
        else tiles.filter { it.state == MASKED && it.probeDue }.minOfOrNull { it.enteredAt + it.probeGap() }

    /** Starts every owed probe whose gap has ended; never one that is not owed. */
    fun startDueProbes(now: Long) {
        if (pausedAt != null) return
        for (tile in tiles) {
            if (tile.state == MASKED && tile.probeDue && now - tile.enteredAt >= tile.probeGap()) tile.enter(PROBING, now)
        }
    }

    /** How long a probe may wait for a captured frame without our mask before re-masking. */
    val probeTimeoutMs get() = PROBE_VALID_MS

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
        probeDue = false
    }

    private fun Tile.mask(hash: Long, now: Long) {
        enter(MASKED, now)
        lockedHash = hash
    }

    /** A probe that bought nothing: the tile is flagged again, or no frame could judge it. */
    private fun Tile.remask(hash: Long, now: Long) {
        mask(hash, now)
        wastedProbes++
    }

    /**
     * Ticket 14: a playing video next to a masked tile fires the neighbour rule on every frame, so
     * a fixed 1 s gap blinks the mask about once a second. Each wasted probe doubles the gap:
     * 1, 2, 4, then 8 s. Cost: a backed-off tile stays masked up to 8 s after its content
     * leaves (over-masking, the safe direction).
     */
    private fun Tile.probeGap() = MIN_PROBE_GAP_MS shl minOf(wastedProbes, 3)

    private companion object {
        const val ENGAGE_FRAMES = 2 // the old PolicyEngine value; tuned in ticket 07
        const val MIN_PROBE_GAP_MS = 1_000L
        const val PROBE_TIMER_MS = 2_000L
        const val PROBE_VALID_MS = 300L
    }
}
