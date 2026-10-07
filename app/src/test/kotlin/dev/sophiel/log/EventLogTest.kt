package dev.sophiel.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.ZoneOffset

private const val DAY = 24 * 3_600_000L

class EventLogTest {
    @get:Rule val tmp = TemporaryFolder()
    private val kv = HashMap<String, String>()
    private var now = 10 * DAY + 3_600_000 // 01:00 on day 10, UTC
    private var boot = 1

    private fun log() = EventLog(
        tmp.root.resolve("log.csv"), { now }, { kv[it] },
        { m -> m.forEach { (k, v) -> if (v == null) kv.remove(k) else kv[k] = v } }, { boot }, ZoneOffset.UTC,
    )

    @Test fun `episodes continue within 3 s and restart after, probes do not split`() {
        val e = Episodes()
        assertTrue(e.update(true, 0)) // first mask
        assertFalse(e.update(true, 100)) // probing counts as masked: no change, no new episode
        assertFalse(e.update(false, 1_000))
        assertFalse(e.update(true, 3_000)) // 2 s gap continues
        assertFalse(e.update(false, 5_000))
        assertTrue(e.update(true, 9_000)) // 4 s gap starts a new one
        assertTrue(Episodes().update(true, 9_000)) // a new session always does
    }

    @Test fun `a protected stretch is reported at its end with its length, under 1 s is not`() {
        val s = Stretches()
        assertNull(s.frame(true, 0))
        assertNull(s.frame(true, 1_900))
        val done = s.frame(false, 2_500)!!
        assertEquals(2, done.seconds)
        assertEquals(2_500, done.startedAgoMs)
        s.frame(true, 10_000)
        assertNull(s.frame(false, 10_800)) // 0.8 s
        s.frame(true, 20_000)
        assertEquals(3, s.end(23_000)!!.seconds) // teardown ends it too
        assertNull(s.end(30_000))
    }

    @Test fun `entries are scalars in a csv line and read back`() {
        val l = log()
        l.on("BALANCED", "NORMAL")
        l.masked("BALANCED", 3, 0.8765f)
        l.unanalyzable(now - 5_000, 4)
        l.off(OffReason.USER)
        assertEquals(
            listOf("ON,BALANCED,NORMAL", "MASKED,BALANCED,3,0.88", "UNANALYZABLE,4", "OFF,USER"),
            tmp.root.resolve("log.csv").readLines().map { it.substringAfter(',') },
        )
        assertEquals(OffReason.USER, l.last()!!.fields[0])
        assertEquals(now - 5_000, l.entries()[2].at)
    }

    @Test fun `lines older than 7 days drop on write and are filtered on read`() {
        val l = log()
        l.masked("LIGHT", 1, 0.9f)
        now += 6 * DAY
        l.masked("LIGHT", 1, 0.9f)
        assertEquals(2, l.entries().size)
        now += 2 * DAY // the first is now 8 days old, the second 2
        assertEquals(1, l.entries().size) // filtered on read, file untouched
        assertEquals(2, tmp.root.resolve("log.csv").readLines().size)
        l.masked("LIGHT", 1, 0.9f) // dropped on write
        assertEquals(2, tmp.root.resolve("log.csv").readLines().size)
    }

    @Test fun `today counts masked entries since local midnight`() {
        val l = log()
        l.masked("LIGHT", 1, 0.9f) // 01:00
        l.off(OffReason.USER)
        now += 22 * 3_600_000L // 23:00
        l.masked("LIGHT", 1, 0.9f)
        assertEquals(2, l.countToday())
        now += 2 * 3_600_000L // 01:00 next day
        assertEquals(0, l.countToday())
        l.masked("LIGHT", 1, 0.9f)
        assertEquals(1, l.countToday())
        l.clear()
        assertEquals(0, l.entries().size)
    }

    @Test fun `an ON without an OFF is closed at its last heartbeat, by boot count`() {
        val l = log()
        l.on("BALANCED", "NORMAL")
        now += 60_000
        l.heartbeat()
        val alive = now
        now += 500_000 // the app dies without an OFF
        log().recoverGap()
        var last = log().last()!!
        assertEquals(listOf(alive, "OFF", OffReason.APP_CLOSED), listOf(last.at, last.kind, last.fields[0]))
        log().recoverGap() // nothing left to close
        assertEquals(2, log().entries().size) // ON, OFF

        l.on("BALANCED", "NORMAL")
        boot = 2
        log().recoverGap()
        last = log().last()!!
        assertEquals(OffReason.RESTARTED, last.fields[0])
    }

    @Test fun `a clean OFF leaves nothing to recover and a late heartbeat does not re-arm it`() {
        val l = log()
        l.on("LIGHT", "NORMAL")
        l.off(OffReason.SCREEN_OFF)
        l.heartbeat()
        log().recoverGap()
        assertEquals(2, log().entries().size)
    }
}
