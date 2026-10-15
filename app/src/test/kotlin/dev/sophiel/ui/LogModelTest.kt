package dev.sophiel.ui

import dev.sophiel.log.Entry
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogModelTest {
    private val zone = ZoneId.of("America/Bogota")
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = at(6, 12)

    private fun masked(t: Long, score: String, preset: String = "BALANCED") = Entry(t, "MASKED", listOf(preset, "2", score))
    private fun model(vararg e: Entry) = LogModel.of(e.toList(), now, zone)

    @Test
    fun entriesGroupByLocalDayAcrossMidnight() {
        val m = model(masked(at(5, 23, 59), "0.60"), masked(at(6, 0, 0), "0.60"), Entry(at(6, 0, 1), "OFF", listOf("USER")))
        assertEquals(7, m.days.size)
        assertTrue(m.days.last().today)
        assertEquals(1, m.days[5].masked)
        assertEquals(1, m.days[6].masked)
        assertEquals(1, m.days[6].off)
        assertEquals(2, m.weekMasked)
    }

    @Test
    fun bandEdgesAreInclusive() {
        assertEquals(Band.HIGH, Band.of(.85f))
        assertEquals(Band.MID, Band.of(.8499f))
        assertEquals(Band.MID, Band.of(.70f))
        assertEquals(Band.LOW, Band.of(.6999f))
        assertEquals(Band.LOW, Band.of(.55f))
        assertEquals(Band.LOW, Band.of(.40f))
    }

    @Test
    fun dayTakesItsHighestBandAndPreciseHasNone() {
        val day = model(masked(at(6, 9), "0.60"), masked(at(6, 10), "0.86"), masked(at(6, 11), "0.99", "PRECISE")).days.last()
        assertEquals(Band.HIGH, day.top)
        assertEquals(3, day.masked)
        val onlyPrecise = model(masked(at(6, 9), "0.99", "PRECISE")).days.last()
        assertNull(onlyPrecise.top)
        assertEquals(1, onlyPrecise.masked)
        assertNull((onlyPrecise.rows.single() as LogRow.Masked).band)
        assertNull(model().days.last().top)
    }

    @Test
    fun rowsAreNewestFirstAndFiltersSplitMaskedFromOthers() {
        val day = model(
            Entry(at(6, 8), "ON", listOf("BALANCED", "NORMAL")),
            masked(at(6, 9), "0.80"),
            Entry(at(6, 8, 30), "UNANALYZABLE", listOf("4")), // written at the end, stamped at its start
            Entry(at(6, 10), "OFF", listOf("SCREEN_OFF")),
            Entry(at(6, 9, 30), "SHARE_STOPPED", emptyList()),
        ).days.last()
        assertEquals(listOf(at(6, 10), at(6, 9, 30), at(6, 9), at(6, 8, 30), at(6, 8)), day.rows.map { it.at })
        assertEquals(true, day.rows[1] is LogRow.ShareStopped)
        assertEquals(1, day.rows(LogFilter.MASKED).size)
        assertEquals(4, day.rows(LogFilter.OTHERS).size)
        assertEquals(5, day.rows(LogFilter.ALL).size)
        assertEquals(1, day.unanalyzable)
        assertEquals(1, day.off)
    }

    @Test
    fun sinProteccionReadsItsLengthAndReasonAndOldShortLinesStillParse() {
        val day = model(
            Entry(at(6, 8), "UNPROTECTED", listOf("754", "ACCESSIBILITY_OFF")),
            Entry(at(6, 9), "UNPROTECTED", emptyList()),
        ).days.last()
        val gap = day.rows[1] as LogRow.Unprotected
        assertEquals(754, gap.seconds)
        assertEquals("ACCESSIBILITY_OFF", gap.reason)
        assertEquals(0, (day.rows[0] as LogRow.Unprotected).seconds)
        assertEquals(2, day.rows(LogFilter.OTHERS).size)
    }

    @Test
    fun tamperAttemptReadsTargetAndCountUnderOthers() {
        val day = model(Entry(at(6, 8), "TAMPER", listOf("ACCESSIBILITY", "2"))).days.last()
        val row = day.rows.single() as LogRow.Tamper
        assertEquals("ACCESSIBILITY", row.target)
        assertEquals(2, row.count)
        assertEquals(1, day.rows(LogFilter.OTHERS).size)
    }

    @Test
    fun blockedOpenReadsAppReasonAndCountUnderOthers() {
        val day = model(Entry(at(6, 8), "BLOCKED_OPEN", listOf("com.instagram.android", "RULE", "3"))).days.last()
        val row = day.rows.single() as LogRow.BlockedOpen
        assertEquals(listOf("com.instagram.android", "RULE", "3"), listOf(row.app, row.reason, row.count.toString()))
        assertEquals(1, day.rows(LogFilter.OTHERS).size)
    }

    @Test
    fun episodeAppIsTheLastFieldAndOldLinesReadAsBlank() {
        val day = model(
            Entry(at(6, 8), "MASKED", listOf("BALANCED", "2", "0.80", "com.android.chrome")),
            Entry(at(6, 9), "MASKED", listOf("BALANCED", "2", "0.80")), // before D57
            Entry(at(6, 10), "UNANALYZABLE", listOf("4", "com.bank")),
            Entry(at(6, 11), "UNANALYZABLE", listOf("4")),
        ).days.last()
        assertEquals(listOf("", "com.bank", "", "com.android.chrome"), day.rows.map {
            when (it) { is LogRow.Masked -> it.app; is LogRow.Unanalyzable -> it.app; else -> error("") }
        })
    }

    @Test
    fun dayNamesItsTopMaskedAppNotBlankNotUnanalyzable() {
        fun on(t: Long, app: String) = Entry(t, "MASKED", listOf("BALANCED", "1", "0.80", app))
        val day = model(
            on(at(6, 8), "a"), on(at(6, 9), "b"), on(at(6, 10), "b"), on(at(6, 11), ""), on(at(6, 11, 5), ""), on(at(6, 11, 6), ""),
            Entry(at(6, 11, 7), "UNANALYZABLE", listOf("4", "a")), Entry(at(6, 11, 8), "UNANALYZABLE", listOf("4", "a")),
        ).days.last()
        assertEquals("b" to 2, day.topApp)
        assertNull(model(masked(at(6, 9), "0.80")).days.last().topApp)
    }

    @Test
    fun lengthsReadInTheirLargestUnits() {
        assertEquals("45 s", LogModel.length(45))
        assertEquals("12 min", LogModel.length(754))
        assertEquals("2 h 5 min", LogModel.length(7_500))
        assertEquals("3 h", LogModel.length(3 * 3_600 + 20))
    }

    @Test
    fun noEntriesIsEmptyAndGarbageLinesAreSkipped() {
        assertTrue(model().empty)
        val m = model(Entry(at(6, 8), "WAT", emptyList()), Entry(at(6, 9), "MASKED", listOf("BALANCED")))
        assertTrue(!m.empty)
        assertEquals(0, m.weekMasked)
    }
}
