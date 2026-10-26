package dev.sophiel.ui

import dev.sophiel.log.Entry
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class LogReportTest {
    private val zone = ZoneId.of("America/Bogota")
    private fun at(d: Int, h: Int) = LocalDateTime.of(2026, 10, d, h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun masked(t: Long, app: String) = Entry(t, "MASKED", listOf("BALANCED", "1", "0.9", app))
    private val s = ReportStrings(
        "Informe · %1\$s", "Fecha: %1\$s", "Tapados: %1\$d", "Sin revisar: %1\$d", "Sin protección: %1\$d", "(%1\$s)",
        "Intento de apagar: %1\$d", "Apps bloqueadas: %1\$d", "Más tapados:", "- %1\$s: %2\$d", "Por día:", "%1\$s: %2\$d", "Solo cifras.",
    )
    private val model = LogModel.of(
        listOf(
            masked(at(5, 9), "a"), masked(at(6, 9), "a"), masked(at(6, 10), "a"), masked(at(6, 11), "b"), masked(at(6, 12), "c"), masked(at(6, 13), "d"),
            Entry(at(6, 14), "UNPROTECTED", listOf("125", "ACCESSIBILITY")), Entry(at(6, 15), "TAMPER", listOf("X", "2")),
            Entry(at(6, 16), "APP_BLOCKED", listOf("a", "30")), Entry(at(6, 17), "UNANALYZABLE", listOf("3", "b")),
        ),
        at(6, 20), zone,
    )
    private fun report(p: ReportPeriod) = LogReport.text(model, p, p.name, "6 oct", { it.date.dayOfMonth.toString() }, { it.uppercase() }, s)

    @Test
    fun todayHasOnlyTodaysFigures() = assertEquals(
        """
        Informe · TODAY
        Fecha: 6 oct

        Tapados: 5
        Sin revisar: 1
        Sin protección: 1 (2 min)
        Intento de apagar: 2
        Apps bloqueadas: 1

        Más tapados:
        - A: 2
        - D: 1
        - C: 1

        Solo cifras.
        """.trimIndent(),
        report(ReportPeriod.TODAY),
    )

    @Test
    fun weekCountsEveryDayAndListsThemOldestFirst() {
        val r = report(ReportPeriod.WEEK).lines()
        assertEquals("Tapados: 6", r[3])
        assertEquals("- A: 3", r[r.indexOf("Más tapados:") + 1])
        assertEquals(listOf("30: 0", "1: 0", "2: 0", "3: 0", "4: 0", "5: 1", "6: 5"), r.subList(r.indexOf("Por día:") + 1, r.size - 2))
    }
}
