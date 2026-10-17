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

    /** [app]: the Episode app's package (D57), blank when unknown or on a line from before it. */
    class Masked(override val at: Long, val preset: String, val count: Int, val score: Float, val band: Band?, val app: String) : LogRow

    /** Back-to-back episodes in one app, newest first: shown as one line that opens into [episodes]. */
    class MaskedRun(val episodes: List<Masked>) : LogRow {
        override val at get() = episodes.first().at
        val app get() = episodes.first().app
        val since get() = episodes.last().at
        val band get() = episodes.mapNotNull { it.band }.maxOrNull()
    }

    /** [count] stretches back to back in one app, folded by [LogDay.grouped]; [since] is the oldest. */
    class Unanalyzable(override val at: Long, val seconds: Int, val app: String, val count: Int = 1, val since: Long = at) : LogRow
    class On(override val at: Long, val preset: String, val sensitivity: String) : LogRow
    class Off(override val at: Long, val reason: String) : LogRow

    /** An [Off] and the [On] after it, folded by [LogDay.grouped]: off at [at] for [seconds]. */
    class Paused(override val at: Long, val seconds: Int, val reason: String) : LogRow
    class ShareStopped(override val at: Long) : LogRow
    class Unprotected(override val at: Long, val seconds: Int, val reason: String) : LogRow
    class Tamper(override val at: Long, val target: String, val count: Int) : LogRow
    class AppBlocked(override val at: Long, val app: String, val minutes: Int) : LogRow
    class BlockedOpen(override val at: Long, val app: String, val reason: String, val count: Int) : LogRow
}

/** One local day: its counters, the colour of its highest band (null = grey), and its rows, newest first. */
class LogDay(val date: LocalDate, val today: Boolean, val rows: List<LogRow>) {
    val masked = rows.count { it is LogRow.Masked }
    val unanalyzable = rows.count { it is LogRow.Unanalyzable }
    val off = rows.count { it is LogRow.Off }
    val top: Band? = rows.mapNotNull { (it as? LogRow.Masked)?.band }.maxOrNull()

    /** The app with the most masked episodes and their count, blank apps not counted; a tie goes to the latest. */
    val topApp: Pair<String, Int>? = rows.mapNotNull { (it as? LogRow.Masked)?.app?.takeIf(String::isNotEmpty) }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.toPair()

    /**
     * [rows] with runs in the same app, each within [RUN_GAP_MS] of the last, folded: masked episodes into one
     * [LogRow.MaskedRun], unchecked stretches into one [LogRow.Unanalyzable] with their time summed. A stop and the
     * turn-on right after it become one [LogRow.Paused].
     */
    fun grouped(filter: LogFilter): List<LogRow> {
        val out = ArrayList<LogRow>()
        var run = ArrayList<LogRow.Masked>()
        fun flush() {
            if (run.size == 1) out += run[0] else if (run.size > 1) out += LogRow.MaskedRun(run)
            run = ArrayList()
        }
        for (r in rows(filter)) {
            if (r is LogRow.Masked && run.isNotEmpty() && run.last().app == r.app && run.last().at - r.at <= LogModel.RUN_GAP_MS) { run += r; continue }
            flush()
            val last = out.lastOrNull()
            when {
                r is LogRow.Masked -> run += r
                r is LogRow.Unanalyzable && last is LogRow.Unanalyzable && last.app == r.app && last.since - r.at <= LogModel.RUN_GAP_MS ->
                    out[out.lastIndex] = LogRow.Unanalyzable(last.at, last.seconds + r.seconds, r.app, last.count + 1, r.at)
                // Newest first, so the turn-on is already out when its stop comes.
                r is LogRow.Off && last is LogRow.On -> out[out.lastIndex] = LogRow.Paused(r.at, ((last.at - r.at) / 1000).toInt(), r.reason)
                else -> out += r
            }
        }
        flush()
        return out
    }

    fun rows(filter: LogFilter) = when (filter) {
        LogFilter.ALL -> rows
        LogFilter.MASKED -> rows.filter { it is LogRow.Masked }
        LogFilter.OTHERS -> rows.filter { it !is LogRow.Masked }
    }
}

/**
 * D59: what a period holds, counted from rows (so the dashboard and the report agree with the list). [intentos] are
 * tamper attempts, [bloqueos] a block placed or an open stopped; both count merged lines by their count.
 * [perApp] is masked episodes per known app, most first (blank apps are left out, as in [LogDay.topApp]).
 */
class LogCounts(val masked: Int, val unchecked: Int, val unprotected: Int, val unprotectedSeconds: Int, val intentos: Int, val bloqueos: Int, val perApp: List<Pair<String, Int>>) {
    companion object {
        fun of(days: List<LogDay>): LogCounts {
            val rows = days.flatMap { it.rows }
            val perApp = rows.mapNotNull { (it as? LogRow.Masked)?.app?.takeIf(String::isNotEmpty) }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
            return LogCounts(
                masked = rows.count { it is LogRow.Masked },
                unchecked = rows.count { it is LogRow.Unanalyzable },
                unprotected = rows.count { it is LogRow.Unprotected },
                unprotectedSeconds = rows.sumOf { (it as? LogRow.Unprotected)?.seconds ?: 0 },
                intentos = rows.sumOf { (it as? LogRow.Tamper)?.count ?: 0 },
                bloqueos = rows.sumOf { when (it) { is LogRow.AppBlocked -> 1; is LogRow.BlockedOpen -> it.count; else -> 0 } },
                perApp = perApp,
            )
        }
    }
}

/** The Log screen (D39, D44), pure: entries + now + zone -> the last 7 local days, oldest first, today last. */
class LogModel(val days: List<LogDay>, val empty: Boolean) {
    val weekMasked = days.sumOf { it.masked }

    /** "Hoy" is since local midnight (the last day); the week is every day held. */
    val today get() = LogCounts.of(days.takeLast(1))
    val week get() = LogCounts.of(days)

    companion object {
        const val PRECISE = "PRECISE"
        const val RUN_GAP_MS = 10 * 60_000L

        /** A Sin protección length in its largest units: "45 s", "12 min", "2 h 5 min" (the same in es and en). */
        fun length(seconds: Int): String {
            val h = seconds / 3_600
            val m = seconds % 3_600 / 60
            return when {
                h > 0 -> if (m > 0) "$h h $m min" else "$h h"
                m > 0 -> "$m min"
                else -> "$seconds s"
            }
        }

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
                    LogRow.Masked(e.at, preset, f.getOrNull(1)?.toIntOrNull() ?: 1, score, if (preset == PRECISE) null else Band.of(score), f.getOrElse(3) { "" })
                }
                "UNANALYZABLE" -> LogRow.Unanalyzable(e.at, f.getOrNull(0)?.toIntOrNull() ?: 0, f.getOrElse(1) { "" })
                "ON" -> LogRow.On(e.at, f.getOrElse(0) { "" }, f.getOrElse(1) { "" })
                "OFF" -> LogRow.Off(e.at, f.getOrElse(0) { "" })
                "SHARE_STOPPED" -> LogRow.ShareStopped(e.at)
                "UNPROTECTED" -> LogRow.Unprotected(e.at, f.getOrNull(0)?.toIntOrNull() ?: 0, f.getOrElse(1) { "" })
                "TAMPER" -> LogRow.Tamper(e.at, f.getOrElse(0) { "" }, f.getOrNull(1)?.toIntOrNull() ?: 1)
                "APP_BLOCKED" -> LogRow.AppBlocked(e.at, f.getOrElse(0) { "" }, f.getOrNull(1)?.toIntOrNull() ?: 0)
                "BLOCKED_OPEN" -> LogRow.BlockedOpen(e.at, f.getOrElse(0) { "" }, f.getOrElse(1) { "" }, f.getOrNull(2)?.toIntOrNull() ?: 1)
                else -> null
            }
        }
    }
}
