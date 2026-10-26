package dev.sophiel.ui

enum class ReportPeriod { TODAY, WEEK }

/** The report's format strings, read from resources by the screen (so the text follows the phone's language) and filled here. */
class ReportStrings(
    val title: String, val date: String, val masked: String, val unchecked: String, val gap: String, val gapTotal: String,
    val attempts: String, val blocked: String, val topHeader: String, val appLine: String, val daysHeader: String, val dayLine: String, val footer: String,
)

/** D59: the Informe, plain text from the same [LogCounts] the dashboard shows. Figures only: no image data, nothing sent by Sophiel. */
object LogReport {
    fun text(
        model: LogModel, period: ReportPeriod, periodLabel: String, date: String,
        dayLabel: (LogDay) -> String, appLabel: (String) -> String, s: ReportStrings,
    ): String {
        val c = if (period == ReportPeriod.TODAY) model.today else model.week
        val lines = mutableListOf(
            s.title.format(periodLabel), s.date.format(date), "",
            s.masked.format(c.masked), s.unchecked.format(c.unchecked),
            s.gap.format(c.unprotected) + if (c.unprotected > 0) " " + s.gapTotal.format(LogModel.length(c.unprotectedSeconds)) else "",
            s.attempts.format(c.intentos), s.blocked.format(c.bloqueoApps),
        )
        if (c.perApp.isNotEmpty()) lines += listOf("", s.topHeader) + c.perApp.take(3).map { (app, n) -> s.appLine.format(appLabel(app), n) }
        if (period == ReportPeriod.WEEK) lines += listOf("", s.daysHeader) + model.days.map { s.dayLine.format(dayLabel(it), it.masked) }
        return (lines + listOf("", s.footer)).joinToString("\n")
    }
}
