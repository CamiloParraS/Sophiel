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
        ).days.last()
        assertEquals(listOf(at(6, 10), at(6, 9), at(6, 8, 30), at(6, 8)), day.rows.map { it.at })
        assertEquals(1, day.rows(LogFilter.MASKED).size)
        assertEquals(3, day.rows(LogFilter.OTHERS).size)
        assertEquals(4, day.rows(LogFilter.ALL).size)
        assertEquals(1, day.unanalyzable)
        assertEquals(1, day.off)
    }

    @Test
    fun noEntriesIsEmptyAndGarbageLinesAreSkipped() {
        assertTrue(model().empty)
        val m = model(Entry(at(6, 8), "WAT", emptyList()), Entry(at(6, 9), "MASKED", listOf("BALANCED")))
        assertTrue(!m.empty)
        assertEquals(0, m.weekMasked)
    }
}
