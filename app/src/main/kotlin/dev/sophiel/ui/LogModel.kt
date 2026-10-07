package dev.sophiel.ui

import dev.sophiel.log.Entry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Confidence word on a masked entry (D44). Precise entries have none (NudeNet's scale). */
enum class Band {
    LOW, MID, HIGH;

    companion object {
        fun of(score: Float) = when {
            score >= .85f -> HIGH
            score >= .70f -> MID
            else -> LOW // .55-.70 and anything below: the blue "Dudoso"
        }
    }
}

enum class LogFilter { ALL, MASKED, OTHERS }

sealed interface LogRow {
    val at: Long

    class Masked(override val at: Long, val preset: String, val count: Int, val score: Float, val band: Band?) : LogRow
    class Unanalyzable(override val at: Long, val seconds: Int) : LogRow
    class On(override val at: Long, val preset: String, val sensitivity: String) : LogRow
    class Off(override val at: Long, val reason: String) : LogRow
}

/** One local day: its counters, the colour of its highest band (null = grey), and its rows, newest first. */
class LogDay(val date: LocalDate, val today: Boolean, val rows: List<LogRow>) {
    val masked = rows.count { it is LogRow.Masked }
    val unanalyzable = rows.count { it is LogRow.Unanalyzable }
    val off = rows.count { it is LogRow.Off }
    val top: Band? = rows.mapNotNull { (it as? LogRow.Masked)?.band }.maxOrNull()

    fun rows(filter: LogFilter) = when (filter) {
        LogFilter.ALL -> rows
        LogFilter.MASKED -> rows.filter { it is LogRow.Masked }
        LogFilter.OTHERS -> rows.filter { it !is LogRow.Masked }
    }
}

/** The Log screen (D39, D44), pure: entries + now + zone -> the last 7 local days, oldest first, today last. */
class LogModel(val days: List<LogDay>, val empty: Boolean) {
    val weekMasked = days.sumOf { it.masked }

    companion object {
        const val PRECISE = "PRECISE"

        fun of(entries: List<Entry>, now: Long, zone: ZoneId): LogModel {
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val rows = entries.mapNotNull(::row).groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
            val days = (6 downTo 0L).map { back ->
                val date = today.minusDays(back)
                LogDay(date, back == 0L, rows[date].orEmpty().sortedByDescending { it.at })
            }
            return LogModel(days, empty = entries.isEmpty())
        }

        private fun row(e: Entry): LogRow? {
            val f = e.fields
            return when (e.kind) {
                "MASKED" -> {
                    val preset = f.getOrNull(0) ?: return null
                    val score = f.getOrNull(2)?.toFloatOrNull() ?: return null
                    LogRow.Masked(e.at, preset, f.getOrNull(1)?.toIntOrNull() ?: 1, score, if (preset == PRECISE) null else Band.of(score))
                }
                "UNANALYZABLE" -> LogRow.Unanalyzable(e.at, f.getOrNull(0)?.toIntOrNull() ?: 0)
                "ON" -> LogRow.On(e.at, f.getOrElse(0) { "" }, f.getOrElse(1) { "" })
                "OFF" -> LogRow.Off(e.at, f.getOrElse(0) { "" })
                else -> null
            }
        }
    }
}
