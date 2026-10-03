package dev.sophiel.core.tile

import dev.sophiel.core.Severity
import dev.sophiel.core.TileVerdict
import dev.sophiel.core.tile.TileState.CLEAR
import dev.sophiel.core.tile.TileState.MASKED
import dev.sophiel.core.tile.TileState.PROBING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileMaskTrackerTest {
    private val unsafe = 100L // content hashes; MASK is what the capture sees over our mask
    private val mask = 999L

    private fun v(index: Int, hash: Long, flagged: Boolean = false) =
        TileVerdict(index, if (flagged) Severity.EXPLICIT else Severity.SAFE, 0f, false, false, hash)

    /** One whole frame: tile i gets hashes[i]; [flagged] and [showsMask] tiles as given. */
    private fun TileMaskTracker.frame(
        now: Long, hashes: List<Long>, flagged: Set<Int> = emptySet(), showsMask: Set<Int> = emptySet(),
    ): List<Boolean> = hashes.mapIndexed { i, h -> onTile(now, v(i, h, i in flagged), i in showsMask) }
        .also { endFrame(now) }

    /** Light tracker with its tile masked at t=100 on [unsafe]. */
    private fun maskedLight() = TileMaskTracker(1, 1).apply {
        frame(0, listOf(unsafe), flagged = setOf(0))
        frame(100, listOf(unsafe), flagged = setOf(0))
        assertEquals(MASKED, this[0])
    }

    @Test fun `engages after two flagged frames and reports one episode`() {
        val t = TileMaskTracker(1, 1)
        assertEquals(listOf(false), t.frame(0, listOf(unsafe), flagged = setOf(0)))
        assertEquals(CLEAR, t[0])
        t.frame(50, listOf(1L)) // a safe frame breaks the streak
        t.frame(100, listOf(unsafe), flagged = setOf(0))
        assertEquals(CLEAR, t[0])
        assertEquals(listOf(true), t.frame(150, listOf(unsafe), flagged = setOf(0)))
        assertEquals(MASKED, t[0])
    }

    @Test fun `masked tile ignores its captured score and has no release count`() {
        val t = maskedLight()
        repeat(5) { t.frame(200L + it * 100, listOf(mask)) } // reads SAFE, still masked
        assertEquals(MASKED, t[0])
    }

    @Test fun `light probes on the 2 s timer only when a frame arrives`() {
        val t = maskedLight()
        t.frame(2099, listOf(mask))
        assertEquals(MASKED, t[0])
        // No frames 2099..10000 (static screen): nothing can change. The next frame probes.
        t.frame(10_000, listOf(mask))
        assertEquals(PROBING, t[0])
    }

    /** Portrait 2×3, row-major: tile 0 is top-left, its neighbours are 1 (right) and 2 (below). */
    private fun maskedBalanced() = TileMaskTracker(2, 3).apply {
        val hashes = listOf(unsafe, 1L, 2L, 3L, 4L, 5L)
        frame(0, hashes, flagged = setOf(0))
        frame(100, hashes, flagged = setOf(0))
        assertEquals(MASKED, this[0])
    }

    @Test fun `balanced probes a masked tile only when its own neighbours change`() {
        val t = maskedBalanced()
        t.frame(1100, listOf(mask, 1L, 2L, 13L, 14L, 15L), showsMask = setOf(0)) // unrelated tiles 3-5 moved
        t.frame(5000, listOf(mask, 1L, 2L, 23L, 24L, 25L), showsMask = setOf(0)) // still unrelated; no timer either
        assertEquals(MASKED, t[0])
        t.frame(5100, listOf(mask, 1L, 12L, 23L, 24L, 25L), showsMask = setOf(0)) // tile 2 below: half its neighbours
        assertEquals(PROBING, t[0])
    }

    @Test fun `a masked tile with no clear neighbours falls back to the 2 s timer`() {
        val t = TileMaskTracker(2, 3)
        val hashes = listOf(unsafe, 101L, 102L, 3L, 4L, 5L)
        t.frame(0, hashes, flagged = setOf(0, 1, 2))
        t.frame(100, hashes, flagged = setOf(0, 1, 2)) // 0, 1, 2 masked: tile 0's neighbours are both masked
        t.frame(2099, listOf(mask, mask, mask, 3L, 4L, 5L), showsMask = setOf(0, 1, 2))
        assertEquals(MASKED, t[0])
        t.frame(2100, listOf(mask, mask, mask, 3L, 4L, 5L), showsMask = setOf(0, 1, 2))
        assertEquals(PROBING, t[0])
        assertEquals(MASKED, t[1]) // has CLEAR neighbour 3, unchanged: no probe
    }

    @Test fun `never probes a tile twice within a second`() {
        val t = maskedBalanced()
        t.frame(600, listOf(mask, 11L, 12L, 13L, 14L, 15L), showsMask = setOf(0)) // all moved, 500 ms in
        assertEquals(MASKED, t[0])
    }

    @Test fun `probe waits while the capture still shows the mask, then re-masks after 300 ms`() {
        val t = maskedLight()
        t.frame(2100, listOf(mask))
        assertEquals(PROBING, t[0])
        t.frame(2300, listOf(mask), showsMask = setOf(0))
        assertEquals(PROBING, t[0])
        t.frame(2400, listOf(mask), showsMask = setOf(0))
        assertEquals(MASKED, t[0])
    }

    @Test fun `expireProbes re-masks a lifted tile without a frame, and never starts a probe`() {
        val t = maskedLight()
        t.frame(2100, listOf(mask))
        t.expireProbes(2399)
        assertEquals(PROBING, t[0])
        t.expireProbes(2400) // the screen went static after the mask was lifted: no frame came
        assertEquals(MASKED, t[0])
        t.expireProbes(60_000) // masked for a minute, still no frame: no probe
        assertEquals(MASKED, t[0])
    }

    @Test fun `valid probe on the locked hash re-masks without a new episode`() {
        val t = maskedLight()
        t.frame(2100, listOf(mask))
        assertEquals(listOf(false), t.frame(2150, listOf(unsafe))) // SAFE score is irrelevant: hash locked
        assertEquals(MASKED, t[0])
    }

    @Test fun `valid probe on new content releases when safe and re-locks when flagged`() {
        val released = maskedLight()
        released.frame(2100, listOf(mask))
        released.frame(2150, listOf(7L))
        assertEquals(CLEAR, released[0])

        val relocked = maskedLight()
        relocked.frame(2100, listOf(mask))
        assertEquals(listOf(false), relocked.frame(2150, listOf(8L), flagged = setOf(0)))
        assertEquals(MASKED, relocked[0])
        relocked.frame(4150, listOf(mask)) // next probe: the lock is now 8, not the old content
        relocked.frame(4200, listOf(8L))
        assertEquals(MASKED, relocked[0])
    }

    @Test fun `pause freezes states and timers`() {
        val t = maskedLight()
        t.pause(200)
        t.frame(3000, listOf(7L)) // ignored
        assertEquals(MASKED, t[0])
        t.resume(10_000)
        t.frame(10_100, listOf(unsafe)) // the pause did not age the mask into a probe
        assertEquals(MASKED, t[0])
    }

    @Test fun `reset puts every tile of the new grid back to clear`() {
        val t = maskedBalanced()
        t.reset(2, 3)
        assertEquals(List(6) { CLEAR }, List(t.size) { t[it] })
        assertFalse(t.frame(200, listOf(unsafe, 1L, 2L, 3L, 4L, 5L), flagged = setOf(0))[0])
        assertTrue(t.frame(300, listOf(unsafe, 1L, 2L, 3L, 4L, 5L), flagged = setOf(0))[0])
    }
}
