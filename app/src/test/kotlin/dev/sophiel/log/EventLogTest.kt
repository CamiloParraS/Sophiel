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
    private var bootedAt = 0L

    private fun log() = EventLog(
        tmp.root.resolve("log.csv"), { now }, { kv[it] },
        { m -> m.forEach { (k, v) -> if (v == null) kv.remove(k) else kv[k] = v } }, { boot }, { bootedAt }, ZoneOffset.UTC,
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
        l.shareStopped()
        l.off(OffReason.USER)
        assertEquals(
            listOf("ON,BALANCED,NORMAL", "MASKED,BALANCED,3,0.88", "UNANALYZABLE,4", "SHARE_STOPPED", "OFF,USER"),
            tmp.root.resolve("log.csv").readLines().map { it.substringAfter(',') },
        )
        assertEquals(OffReason.USER, l.last()!!.fields[0])
        assertEquals(now - 5_000, l.entries()[2].at)
    }

    @Test fun `lines older than 7 days are filtered on read and drop at the next start`() {
        val l = log()
        l.masked("LIGHT", 1, 0.9f)
        now += 6 * DAY
        l.masked("LIGHT", 1, 0.9f)
        assertEquals(2, l.entries().size)
        now += 2 * DAY // the first is now 8 days old, the second 2
        assertEquals(1, l.entries().size) // filtered on read, file untouched
        assertEquals(2, tmp.root.resolve("log.csv").readLines().size)
        l.masked("LIGHT", 1, 0.9f) // an append never reads or rewrites the file
        assertEquals(3, tmp.root.resolve("log.csv").readLines().size)
        log().recoverGap(keepProtecting = true) // app start drops it
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

    @Test fun `switch off - a capture with no OFF is closed at its last heartbeat, by boot count`() {
        val l = log()
        l.capturing(true)
        now += 60_000
        l.heartbeat()
        val alive = now
        now += 500_000 // the app dies without an OFF
        log().recoverGap(keepProtecting = false)
        var last = log().last()!!
        assertEquals(listOf(alive, "OFF", OffReason.APP_CLOSED), listOf(last.at, last.kind, last.fields[0]))
        log().recoverGap(keepProtecting = false) // nothing left to close
        assertEquals(1, log().entries().size)

        l.capturing(true)
        boot = 2
        log().recoverGap(keepProtecting = false)
        last = log().last()!!
        assertEquals(OffReason.RESTARTED, last.fields[0])
    }

    @Test fun `switch on - an app stopped is an open Sin proteccion stretch from its last heartbeat`() {
        val l = log()
        l.capturing(true)
        val alive = now
        now += 300_000 // am force-stop
        val back = log().apply { recoverGap(keepProtecting = true) }
        assertEquals(Gap(alive, GapReason.APP_CLOSED), back.gap())
        assertTrue(back.entries().isEmpty()) // written when it ends
        now += 60_000 // the Parent's Start
        back.gapClosed()
        val e = back.last()!!
        assertEquals(listOf(alive, "UNPROTECTED", "360", GapReason.APP_CLOSED), listOf(e.at, e.kind) + e.fields)
        assertNull(back.gap())
        back.gapClosed() // nothing open: nothing written
        assertEquals(1, back.entries().size)
    }

    @Test fun `switch on - a restart is a stretch up to the boot, boot to first unlock is not a gap`() {
        log().capturing(true)
        val alive = now
        bootedAt = now + 90_000
        now += 30 * 60_000 // first unlock, half an hour later
        boot = 2
        val back = log().apply { recoverGap(keepProtecting = true) }
        assertNull(back.gap())
        val e = back.last()!!
        assertEquals(listOf(alive, "UNPROTECTED", "60", GapReason.RESTARTED), listOf(e.at, e.kind) + e.fields) // the night off is not a gap
        back.capturing(true)
        val again = now
        bootedAt = now + 20_000 // a quick restart
        now += 120_000
        boot = 3
        log().recoverGap(keepProtecting = true)
        assertEquals(listOf(again, "20"), log().last()!!.let { listOf(it.at, it.fields[0]) })
    }

    @Test fun `a stretch found at the first start after a boot begins at the boot, a later start at now`() {
        log().recoverGap(keepProtecting = true) // a process this boot
        bootedAt = now + 60_000
        now += 3 * 3_600_000 // accessibility off: nothing ran until Sophiel was opened, 3 h later
        boot = 2
        val first = log().apply { recoverGap(keepProtecting = true) }
        first.gapFound(GapReason.ACCESSIBILITY_OFF)
        assertEquals(Gap(bootedAt, GapReason.ACCESSIBILITY_OFF), first.gap())
        first.gapClosed()
        now += 60_000
        val later = log().apply { recoverGap(keepProtecting = true) } // killed and started again, same boot
        later.gapFound(GapReason.ACCESSIBILITY_OFF)
        assertEquals(Gap(now, GapReason.ACCESSIBILITY_OFF), later.gap())
    }

    @Test fun `a live stretch survives the process and capturing again does not re-arm a gap`() {
        val l = log()
        l.capturing(true)
        l.capturing(false) // the service went off: the session closed
        l.gapOpened(GapReason.ACCESSIBILITY_OFF)
        val at = now
        now += 10_000
        log().recoverGap(keepProtecting = true) // no heartbeat to infer from
        assertEquals(Gap(at, GapReason.ACCESSIBILITY_OFF), log().gap())
        l.gapOpened(GapReason.APP_CLOSED) // already open: the first reason and start stay
        assertEquals(Gap(at, GapReason.ACCESSIBILITY_OFF), log().gap())
    }

    @Test fun `the inferred stretch is not stamped before an entry written after the last heartbeat`() {
        val l = log()
        l.capturing(true)
        now += 5_000
        l.masked("BALANCED", 1, 0.9f)
        val wrote = now
        now += 20_000
        log().recoverGap(keepProtecting = true)
        assertEquals(wrote, log().gap()!!.at)
    }

    @Test fun `a clean stop leaves nothing to recover and a late heartbeat does not re-arm it`() {
        val l = log()
        l.capturing(true)
        l.capturing(false)
        l.off(OffReason.USER)
        l.heartbeat()
        log().recoverGap(keepProtecting = false)
        assertEquals(1, log().entries().size)
    }
    @Test fun `a tamper attempt on the same target within 60 s merges into one entry with a count`() {
        val l = log()
        l.tamper(TamperTarget.ACCESSIBILITY)
        val first = now
        now += 30_000
        l.masked("BALANCED", 1, 0.9f) // another kind in between does not split it
        l.tamper(TamperTarget.ACCESSIBILITY)
        now += 29_999
        l.tamper(TamperTarget.ACCESSIBILITY)
        assertEquals(listOf(Entry(first, "TAMPER", listOf("ACCESSIBILITY", "3"))), l.entries().filter { it.kind == "TAMPER" })
        now = first + 60_000 // 60 s after the entry's first attempt: a new entry
        l.tamper(TamperTarget.ACCESSIBILITY)
        assertEquals(listOf("3", "1"), l.entries().filter { it.kind == "TAMPER" }.map { it.fields[1] })
        assertEquals(2, l.entries().indexOfFirst { it.kind == "MASKED" } + 1) // order kept: TAMPER, MASKED, TAMPER
    }
}
