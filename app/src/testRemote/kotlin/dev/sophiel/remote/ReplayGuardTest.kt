package dev.sophiel.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayGuardTest {
    private var now = 1_000_000L
    private val guard = ReplayGuard { now }
    private val min = 60_000L

    @Test fun freshIdAccepted() = assertEquals(true, guard.accept("a", now))

    @Test fun replayedIdRejected() {
        guard.accept("a", now)
        now += min
        assertEquals(false, guard.accept("a", now))
    }

    @Test fun staleMessageRejected() = assertEquals(false, guard.accept("a", now - 2 * min - 1))

    @Test fun twoMinutesOldStillAccepted() = assertEquals(true, guard.accept("a", now - 2 * min))

    @Test fun farFutureRejected() = assertEquals(false, guard.accept("a", now + 2 * min + 1))

    @Test fun seenSetShrinksWithTheWindow() {
        repeat(100) { guard.accept("m$it", now); now += 10_000 }
        assertEquals(true, guard.size <= 13)
    }

    @Test fun idForgottenAfterWindowButItsTimestampIsStale() {
        guard.accept("a", now)
        val old = now
        now += 2 * min + 1
        assertEquals(false, guard.accept("a", old))
    }
}
