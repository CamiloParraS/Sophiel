package dev.sophiel.core.tile

import dev.sophiel.core.Severity
import dev.sophiel.core.TileVerdict
import dev.sophiel.core.tile.TileState.CLEAR
import dev.sophiel.core.tile.TileState.MASKED
import dev.sophiel.core.tile.TileState.PROBING
import dev.sophiel.core.tile.TileState.REVEALED
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
    private fun maskedLight() = TileMaskTracker(1).apply {
        frame(0, listOf(unsafe), flagged = setOf(0))
        frame(100, listOf(unsafe), flagged = setOf(0))
        assertEquals(MASKED, this[0])
    }

    @Test fun `engages after two flagged frames and reports one episode`() {
        val t = TileMaskTracker(1)
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

    private fun maskedBalanced() = TileMaskTracker(6).apply {
        val hashes = listOf(unsafe, 1L, 2L, 3L, 4L, 5L)
        frame(0, hashes, flagged = setOf(0))
        frame(100, hashes, flagged = setOf(0))
        assertEquals(MASKED, this[0])
    }

    @Test fun `balanced probes when half the clear tiles change`() {
        val t = maskedBalanced()
        t.frame(1100, listOf(mask, 11L, 12L, 3L, 4L, 5L), showsMask = setOf(0)) // 2 of 5 changed
        assertEquals(MASKED, t[0])
        t.frame(1200, listOf(mask, 11L, 12L, 13L, 4L, 5L), showsMask = setOf(0)) // 1 of 5 since last frame
        assertEquals(MASKED, t[0])
        t.frame(1300, listOf(mask, 21L, 22L, 23L, 4L, 5L), showsMask = setOf(0)) // 3 of 5
        assertEquals(PROBING, t[0])
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

    @Test fun `reveal uncovers masked tiles for 5 s, then probes`() {
        val t = maskedLight()
        t.reveal(200)
        assertEquals(REVEALED, t[0])
        t.frame(5199, listOf(unsafe), flagged = setOf(0)) // score ignored while revealed
        assertEquals(REVEALED, t[0])
        t.frame(5200, listOf(unsafe), flagged = setOf(0))
        assertEquals(PROBING, t[0])
    }

    @Test fun `pause freezes states and timers, and a reveal made while paused lasts 5 s after resume`() {
        val t = maskedLight()
        t.pause(200)
        t.frame(3000, listOf(7L)) // ignored
        assertEquals(MASKED, t[0])
        t.reveal(9000) // Parent reveals from a Sophiel screen
        t.resume(10_000)
        t.frame(14_999, listOf(unsafe))
        assertEquals(REVEALED, t[0])
        t.frame(15_000, listOf(unsafe))
        assertEquals(PROBING, t[0])
    }

    @Test fun `reset puts every tile of the new grid back to clear`() {
        val t = maskedBalanced()
        t.reset(6)
        assertEquals(List(6) { CLEAR }, List(t.size) { t[it] })
        assertFalse(t.frame(200, listOf(unsafe, 1L, 2L, 3L, 4L, 5L), flagged = setOf(0))[0])
        assertTrue(t.frame(300, listOf(unsafe, 1L, 2L, 3L, 4L, 5L), flagged = setOf(0))[0])
    }
}
