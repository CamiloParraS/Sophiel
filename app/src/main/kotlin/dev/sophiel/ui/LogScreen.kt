package dev.sophiel.ui

import android.text.format.DateFormat
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.rememberTextMeasurer
import dev.sophiel.R
import dev.sophiel.capture.isDebuggable
import dev.sophiel.log.EventLog
import dev.sophiel.log.GapReason
import dev.sophiel.log.OffReason
import dev.sophiel.log.BlockReason
import dev.sophiel.settings.appLabel
import dev.sophiel.log.TamperTarget
import dev.sophiel.ui.theme.Ease
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.lift
import dev.sophiel.ui.theme.motion
import dev.sophiel.ui.theme.reducedMotion
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Log rows shown per page (and added per "Mostrar más"). */
private const val PAGE = 40 // a normal week fits in one page, so "Mostrar más" is the exception

private fun Band?.color() = when (this) {
    Band.HIGH -> Palette.Red
    Band.MID -> Palette.Orange
    Band.LOW -> Palette.Blue
    null -> Palette.Fg.copy(alpha = .2f)
}

@StringRes
private fun presetName(preset: String) = when (preset) {
    "LIGHT" -> R.string.preset_light
    "THOROUGH" -> R.string.preset_thorough
    "PRECISE" -> R.string.preset_precise
    else -> R.string.preset_balanced
}

@StringRes
private fun careName(sensitivity: String) = when (sensitivity) {
    "STRICT" -> R.string.care_strict
    "RELAXED" -> R.string.care_relaxed
    else -> R.string.care_normal
}

@StringRes
private fun offText(reason: String) = when (reason) {
    OffReason.USER -> R.string.log_off_user
    OffReason.SCREEN_OFF -> R.string.log_off_screen
    OffReason.RESTARTED -> R.string.log_off_restarted
    OffReason.APP_CLOSED -> R.string.log_off_app
    else -> R.string.log_off_system
}

@StringRes
private fun gapText(reason: String) = when (reason) {
    GapReason.APP_CLOSED -> R.string.log_off_app
    GapReason.RESTARTED -> R.string.log_off_restarted
    else -> R.string.log_gap_accessibility
}

class LogRead(val model: LogModel, val labels: Map<String, String>)

/** The Log's read off the main thread, kept for the next open. Scalars only (rule 3); lives and dies with the process. */
object LogReads {
    @Volatile var last: LogRead? = null
    private val names = java.util.concurrent.ConcurrentHashMap<String, String>() // PackageManager lookups are the slow part on a budget phone

    suspend fun load(log: EventLog, ctx: Context, zone: ZoneId): LogRead = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val m = LogModel.of(log.entries(), System.currentTimeMillis(), zone)
        // D57: labels at read time, in the phone's language; an uninstalled app shows its package.
        val apps = m.days.flatMap { it.rows }.mapNotNull {
            when (it) {
                is LogRow.Masked -> it.app
                is LogRow.Unanalyzable -> it.app
                is LogRow.BlockedOpen -> it.app
                is LogRow.AppBlocked -> it.app
                else -> null
            }
        }
        // The Recents view is drawn by the home launcher, so covers over other apps' thumbnails are filed under it: say what it is.
        val home = names.getOrPut("") {
            pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName.orEmpty()
        }
        LogRead(m, apps.filter(String::isNotEmpty).toSet().associateWith { pkg ->
            if (pkg == home) ctx.getString(R.string.log_app_recents) else names.getOrPut(pkg) { appLabel(pm, pkg) }
        }).also { last = it }
    }
}

/** Historial (D39, D44): draws a [LogModel] in Status's language, a dark tile hero with the week's count and a chalk sheet over it. */
@Composable
fun LogScreen(log: EventLog, door: Door, onBack: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    var version by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    // Stale-while-revalidate: reopening paints the last read at once, then the fresh one replaces it.
    val read by produceState<LogRead?>(LogReads.last, version) { value = LogReads.load(log, ctx, zone) }
    val model = read?.model
    val labels = read?.labels.orEmpty()
    // The screen slides in over ~340 ms; composing the list in the middle of that drops frames. Hero and sheet go first, the list lands after.
    val still = reducedMotion()
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!still) delay(360); settled = true }
    var confirm by remember { mutableStateOf(false) }
    var report by rememberSaveable { mutableStateOf(false) }
    // The by-app screen rides inside the Log: Back goes app -> list -> Log (LogAppsScreen owns the first two steps).
    var byApp by rememberSaveable { mutableStateOf(false) }
    var pick by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Light icons on the dark hero; back to dark ones when leaving.
    if (!byApp) LightStatusIcons() // the by-app page has the light header
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var heroPx by remember { mutableIntStateOf(0) }
    var barPx by remember { mutableIntStateOf(0) }
    val lip = with(density) { 20.dp.roundToPx() }
    // The top bar turns solid once the sheet's edge reaches it, so the back button never floats over chalk.
    val solid by remember { derivedStateOf { heroPx > 0 && heroPx - lip - scroll.value <= barPx } }
    val barFill by animateColorAsState(if (solid) Moss else Color.Transparent, motion(tween(160)), label = "bar")
    if (byApp && model != null) {
        LogAppsScreen(model, labels, pick, { pick = it }, door, onBack = { byApp = false })
        return
    }
    Box(Modifier.fillMaxSize().background(Palette.Chalk)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            val m = model
            LogHero(m?.week, m?.days.orEmpty(), Modifier.onSizeChanged { heroPx = it.height })
            // The sheet rides 20 dp over the hero, as on Status; layout, not offset, so it leaves no gap at the bottom.
            Column(
                Modifier.fillMaxWidth().layout { mm, c ->
                    val p = mm.measure(c)
                    layout(p.width, p.height - lip) { p.place(0, -lip) }
                }.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Palette.Chalk)
                    // Tall enough to cover the rest of the hero's tiles before the list has landed (else they peek out below a short sheet).
                    .heightIn(min = (LocalConfiguration.current.screenHeightDp.dp - with(density) { heroPx.toDp() } + 64.dp).coerceAtLeast(0.dp))
                    .navigationBarsPadding().cutoutPadding().pageWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CompositionLocalProvider(LocalRowMin provides 48.dp) {
                    when {
                        m == null -> LogSkeleton()
                        m.empty -> AnimatedVisibility(true, enter = fadeIn(motion(tween(180, easing = Ease)))) {
                            StatusPage(R.drawable.ic_list, stringResource(R.string.log_empty), stringResource(R.string.log_empty_text), Tone.Off, Modifier.padding(vertical = 24.dp))
                        }
                        !settled -> LogSkeleton()
                        else -> AnimatedVisibility(true, enter = fadeIn(motion(tween(180, easing = Ease)))) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { Content(m, labels, scroll, barPx, onClear = { confirm = true }, onByApp = { byApp = true }) }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(barFill).onSizeChanged { barPx = it.height }.statusBarsPadding().cutoutPadding().padding(start = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SButton(null, onBack, style = BtnStyle.OnDark, icon = R.drawable.ic_back, label = stringResource(R.string.back))
            Text(stringResource(R.string.log_title), Modifier.asHeading().weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.titleMedium, color = Text0)
            if (model != null && !model.empty) SButton(stringResource(R.string.report_open), { report = true }, style = BtnStyle.OnDark)
            UnlockedChip(door, onDark = true)
        }
        if (model != null) ReportSheet(report, model, labels) { report = false }
    }
    if (confirm) ConfirmDialog(
        stringResource(R.string.log_clear_title), stringResource(R.string.log_clear_text),
        confirmLabel = stringResource(R.string.log_clear_confirm), cancelLabel = stringResource(R.string.pin_cancel),
        onConfirm = { confirm = false; scope.launch(Dispatchers.IO) { log.clear(); version++ } }, onDismiss = { confirm = false },
    )
}

/** D59: the Informe in a sheet: Hoy / 7 días, the text as it will be sent, and the system share sheet. Sophiel sends nothing itself. */
@Composable
private fun BoxScope.ReportSheet(visible: Boolean, model: LogModel, labels: Map<String, String>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val locale = appLocale()
    var period by rememberSaveable { mutableStateOf(ReportPeriod.TODAY) }
    val title = stringResource(R.string.report_open)
    val periods = listOf(R.string.log_today, R.string.report_week).map { stringResource(it) }
    val strings = ReportStrings(
        stringResource(R.string.report_title), stringResource(R.string.report_date), stringResource(R.string.report_masked), stringResource(R.string.report_unchecked),
        stringResource(R.string.report_gap), stringResource(R.string.report_gap_total), stringResource(R.string.report_attempts), stringResource(R.string.report_blocked),
        stringResource(R.string.report_top), stringResource(R.string.report_app_line), stringResource(R.string.report_days), stringResource(R.string.report_day_line), stringResource(R.string.report_footer),
    )
    val today = stringResource(R.string.log_today)
    val text = run {
        val short = DateFormat.getBestDateTimePattern(locale, "EEEdMMM").let { java.time.format.DateTimeFormatter.ofPattern(it, locale) }
        LogReport.text(
            model, period, periods[period.ordinal], java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)),
            { if (it.today) today else it.date.format(short) }, { labels[it] ?: it }, strings,
        )
    }
    BottomSheet(visible, title, onDismiss) {
        SegmentedControl(periods, period.ordinal, { period = ReportPeriod.entries[it] }, Modifier.fillMaxWidth())
        Text(text, Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
        SButton(stringResource(R.string.report_share), {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            ctx.startActivity(Intent.createChooser(send, null))
        }, Modifier.fillMaxWidth(), style = BtnStyle.Suggested)
    }
}

/** What the sheet shows while the Log is read and the slide finishes: the three shapes it will hold, still: a loading cover moves for 200 ms at most. */
@Composable
private fun LogSkeleton() {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(150.dp).clip(shape).background(Palette.Btn))
        Box(Modifier.fillMaxWidth().height(220.dp).clip(shape).background(Palette.Btn))
    }
}

/** The week at a glance: the count as the one headline, a bar per day under it, on Status's tile grid. */
@Composable
private fun LogHero(week: LogCounts?, days: List<LogDay>, modifier: Modifier) {
    Box(modifier.fillMaxWidth().background(Moss)) {
        Canvas(Modifier.matchParentSize()) { drawGrid(Page.OFF, Palette.HeroOff, 0L, -100_000L, true, emptyList()) }
        // Count left, the week's bars right; the verdict under both, full width (beside the bars it wrapped to three lines).
        Column(Modifier.fillMaxWidth().statusBarsPadding().cutoutPadding().padding(start = 22.dp, end = 22.dp, top = 76.dp, bottom = 60.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(week?.masked?.toString().orEmpty(), style = MaterialTheme.typography.displayLarge, color = Text0)
                    Text(stringResource(R.string.log_hero_text), style = MaterialTheme.typography.bodyMedium, color = Text1)
                }
                if (days.isNotEmpty()) DayBars(days, Modifier.width(168.dp))
            }
            // The verdict rides with the count; it is the week's only summary (D73 dropped the figure cards under it).
            Column(Modifier.padding(top = 4.dp)) {
                if (week != null) Text(
                    stringResource(if (week.look) R.string.log_verdict_look else R.string.log_verdict_ok), Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.titleMedium, color = if (week.look) Text0 else Text1,
                )
                // "Para revisar" names its cause: the gap time if there was one, else the attempts.
                if (week != null && week.look) Text(
                    if (week.unprotected > 0) stringResource(R.string.log_cause_gap, LogModel.length(week.unprotectedSeconds))
                    else pluralStringResource(R.plurals.log_cause_attempts, week.intentos, week.intentos),
                    style = MaterialTheme.typography.bodyMedium, color = Text1,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.Content(model: LogModel, labels: Map<String, String>, scroll: ScrollState, barPx: Int, onClear: () -> Unit, onByApp: () -> Unit) {
    val locale = appLocale()
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    val week = model.week
    val label = { app: String -> labels[app] ?: app }
    var filterTop by remember { mutableIntStateOf(0) }
    // Covered is what the hero counts, so its breakdown comes first; the verdict is the hero's and each day says the rest. The last row is the way to every app.
    if (week.perApp.isNotEmpty()) PreferenceGroup(title = stringResource(R.string.log_by_app)) {
        item { AppBars(week.perApp.take(4), label) }
        item {
            // A link under the data, not another headline: body weight, so the bars above stay the loudest thing in the card.
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onByApp).heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (week.perApp.size > 4) stringResource(R.string.log_apps_see_all, week.perApp.size) else stringResource(R.string.log_apps_see),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Palette.Fg,
                )
                SIcon(R.drawable.ic_chev, Palette.Dim, 16.dp)
            }
        }
    }
    val stack = LocalDensity.current.fontScale >= 1.5f
    // Pinned under the top bar once the page scrolls to it; it bleeds past the sheet's padding so rows never show at its sides.
    SegmentedControl(
        listOf(R.string.log_filter_all, R.string.log_filter_masked, R.string.log_filter_others).map { stringResource(it) },
        filter.ordinal, { filter = LogFilter.entries[it] },
        Modifier.zIndex(1f)
            .onGloballyPositioned { filterTop = it.positionInRoot().y.toInt() + scroll.value }
            .graphicsLayer { if (!stack) translationY = (scroll.value + barPx - filterTop).coerceAtLeast(0).toFloat() } // stacked at large text it is a third of the screen: let it scroll
            .layout { m, c ->
                val e = 16.dp.roundToPx()
                val p = m.measure(c.copy(minWidth = c.maxWidth + 2 * e, maxWidth = c.maxWidth + 2 * e))
                layout(c.maxWidth, p.height) { p.place(-e, 0) }
            }
            .background(Palette.Chalk).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp), // the band is taller than the pill: a row never shows half-cut under it
    )
    // A busy week has hundreds of rows, all composed at once in this scroll: show a page, add a page per tap.
    var shown by rememberSaveable(filter) { mutableIntStateOf(PAGE) }
    val days = model.days.asReversed().map { it to it.grouped(filter) }.filter { it.second.isNotEmpty() }
    val total = days.sumOf { it.second.size }
    if (days.isEmpty()) Text(
        stringResource(if (filter == LogFilter.MASKED) R.string.log_nothing_masked else R.string.log_nothing),
        Modifier.fillMaxWidth().padding(vertical = 18.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim, textAlign = TextAlign.Center,
    )
    // A filter swaps the whole list: it fades in rather than teleporting (opacity only; the old list is already gone).
    key(filter) {
        AnimatedVisibility(true, enter = fadeIn(motion(tween(160, easing = Ease)))) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                var left = shown
                for ((d, rows) in days) {
                    if (left <= 0) break
                    val day = rows.take(left)
                    left -= day.size
                    // Each day opens with its story in a sentence; the rows under it are the evidence.
                    Column(Modifier.padding(top = 10.dp)) {
                        Column(Modifier.padding(horizontal = 4.dp).semantics(mergeDescendants = true) {}) {
                            Text(dayTitle(d, locale), Modifier.asHeading(), style = MaterialTheme.typography.titleMedium)
                            Text(daySummary(d, label), Modifier.padding(top = 2.dp, bottom = 8.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
                        }
                        PreferenceGroup { day.forEach { r -> item { EntryRow(r, labels) } } }
                    }
                }
            }
        }
    }
    // Said once under the list, not on every row: what "No se pudo revisar" means for the Parent.
    if (week.unchecked > 0 && filter != LogFilter.MASKED) Text(
        stringResource(R.string.log_unchecked_note), Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Dim,
    )
    if (total > shown) SButton(
        stringResource(R.string.log_more, total - shown), { shown += PAGE }, Modifier.align(Alignment.CenterHorizontally),
    )
    SButton(stringResource(R.string.log_clear), onClear, Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp), tint = Palette.RedFg, icon = R.drawable.ic_trash) // neutral pill, red only in the ink: the red fill shouted louder than the list
}

/**
 * A day in one plain sentence: what was covered and where most of it was, then time without protection (a minute or more) and protected
 * settings opened. "12 tapados, casi todo en Galería · 7 h 18 min sin protección".
 */
@Composable
private fun daySummary(d: LogDay, label: (String) -> String): String {
    val gap = d.rows.sumOf { (it as? LogRow.Unprotected)?.seconds ?: 0 }
    val opened = d.rows.sumOf { (it as? LogRow.Tamper)?.count ?: 0 }
    val covered = if (d.masked == 0) stringResource(R.string.log_day_none) else pluralStringResource(R.plurals.log_day_covered, d.masked, d.masked).let { n ->
        val top = d.topApp?.takeIf { it.second * 2 > d.masked } // "most" only when it is more than half
        if (top == null) n else "$n, " + stringResource(R.string.log_day_top, label(top.first))
    }
    return listOfNotNull(
        covered,
        if (gap >= LogModel.BRIEF_SECONDS) stringResource(R.string.log_cause_gap, LogModel.length(gap)) else null,
        if (opened > 0) pluralStringResource(R.plurals.log_cause_attempts, opened, opened) else null,
    ).joinToString(" · ")
}

/** "Hoy", or the day written out in the phone's language ("Jueves, 15 de octubre"). */
@Composable
internal fun dayTitle(d: LogDay, locale: Locale) = if (d.today) stringResource(R.string.log_today) else DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM").let { p ->
    d.date.format(java.time.format.DateTimeFormatter.ofPattern(p, locale)).replaceFirstChar { it.titlecase(locale) }
}

/**
 * D59: masked episodes per day, 7 bars, today last and brightest, on the dark hero. One ink: the band colour per day
 * (D44) read as an unexplained pink and brown; the rows' tags carry the band.
 */
@Composable
private fun DayBars(days: List<LogDay>, modifier: Modifier) {
    val most = days.maxOf { it.masked }.coerceAtLeast(1)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEach { d ->
            val ink = if (d.today) Text0 else Text1.copy(alpha = .75f)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                // The day's count over its bar: the heights alone have no scale.
                Text(
                    if (d.masked == 0) "" else d.masked.toString(), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = if (d.today) Text0 else Text1, maxLines = 1, softWrap = false, // "43" at large text is wider than its slot: let it spill, not clip
                )
                Box(Modifier.padding(top = 2.dp).height(40.dp), contentAlignment = Alignment.BottomCenter) {
                    val h = if (d.masked == 0) 2.dp else (6 + 34f * d.masked / most).dp
                    Box(Modifier.width(12.dp).height(h).clip(RoundedCornerShape(3.dp)).background(if (d.masked == 0) Text1.copy(alpha = .3f) else ink))
                }
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, appLocale()).let { it.uppercase(appLocale()) }, Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall, color = if (d.today) Text0 else Text1, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AppBars(apps: List<Pair<String, Int>>, label: (String) -> String) {
    val most = apps.first().second
    // Each row is its bar: icon and name over a fill as wide as its share of the top app, the count at the end.
    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        apps.forEach { (app, n) ->
            Box(Modifier.fillMaxWidth().height(32.dp)) {
                // One ink for every bar (D59 did the same for the day bars); never narrower than the icon and a short name, so the label stays on its fill.
                Box(Modifier.fillMaxWidth((n.toFloat() / most).coerceAtLeast(.4f)).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(Palette.GreenFg.copy(alpha = .16f)))
                Row(
                    Modifier.matchParentSize().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppIcon(app, 18.dp)
                    Text(label(app), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(n.toString(), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Palette.Fg)
                }
            }
        }
    }
}

private val Gap = 10.dp
private val Pad = 14.dp
private val IconSize = 18.dp

/** The time column: as wide as the widest time this phone writes ("10:45 p. m.", "22:45"), measured, so it grows with the text size. */
@Composable
private fun timeWidth(): Dp {
    val context = LocalContext.current
    val style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, density) {
        val widest = DateFormat.getTimeFormat(context).format(Date(java.time.LocalDate.now().atTime(22, 48).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))
        with(density) { measurer.measure(widest, style).size.width.toDp() + 2.dp }
    }
}

/**
 * One Log line, read as a timeline: the time in its own column, the kind's icon, what happened, an optional [end].
 * [quiet] lines (protection on and off) step back so masks, attempts and blocks lead. With [open], a chevron turns as it opens.
 */
@Composable
private fun Line(
    time: String, @DrawableRes icon: Int, tint: Color, title: String, text: String?,
    @StringRes kind: Int? = null, quiet: Boolean = false, open: Boolean? = null, onClick: (() -> Unit)? = null, end: (@Composable () -> Unit)? = null,
) {
    // The icon carries the kind for sighted readers; TalkBack gets it as a word, then time, title and detail in one stop.
    // At large text the tag would squeeze the title to a sliver: it moves under the text instead.
    val tagBelow = LocalDensity.current.fontScale >= 1.5f
    val said = listOfNotNull(kind?.let { stringResource(it) }, time, title, text).joinToString(", ")
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = said }.fillMaxWidth().let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .heightIn(min = if (onClick != null) 48.dp else if (quiet) 40.dp else 52.dp).padding(horizontal = Pad, vertical = if (quiet) 6.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, Modifier.width(timeWidth()), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = Palette.Dim, maxLines = 1)
        SIcon(icon, tint)
        Column(Modifier.weight(1f)) {
            // A quiet line is one plain line, a marker between events rather than an event.
            if (quiet) Text(
                if (text == null) title else stringResource(R.string.log_gap_text, title, text),
                style = MaterialTheme.typography.bodyMedium, color = Palette.Dim, maxLines = 4, overflow = TextOverflow.Ellipsis, // large text wraps a quiet line to 3; 2 cut "La det…"
            ) else {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) // two lines: at large text one held "No se pudo r…"
                if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
            }
            if (tagBelow && end != null) Box(Modifier.padding(top = 4.dp)) { end() }
        }
        if (!tagBelow) end?.invoke()
        if (open != null) {
            val turn by animateFloatAsState(if (open) 270f else 90f, motion(tween(200, easing = Ease)), label = "chev")
            SIcon(R.drawable.ic_chev, Palette.Dim, 16.dp, Modifier.rotate(turn))
        }
    }
}

/** What an opened line shows, lined up under its title; it closes faster than it opens. */
@Composable
private fun Detail(open: Boolean, content: @Composable ColumnScope.() -> Unit) = AnimatedVisibility(
    open,
    enter = expandVertically(motion(tween(220, easing = Ease))) + fadeIn(motion(tween(220, easing = Ease))),
    exit = shrinkVertically(motion(tween(160, easing = Ease))) + fadeOut(motion(tween(120))),
) {
    Column(Modifier.padding(start = Pad + timeWidth() + Gap + IconSize + Gap, end = Pad, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/** The classifier's confidence word: a parent can't act on it, so only a debug build shows it. */
@Composable
private fun BandTag(band: Band) = if (!LocalContext.current.isDebuggable) Unit else when (band) {
    Band.HIGH -> Tag(stringResource(R.string.log_band_high), TagKind.VerySafe)
    Band.MID -> Tag(stringResource(R.string.log_band_mid), TagKind.Safe)
    Band.LOW -> Tag(stringResource(R.string.log_band_low), TagKind.Doubtful)
}

/** [inApp]: the page is already about one app, so its masked lines lead with what happened, not the app's name. */
@Composable
internal fun EntryRow(row: LogRow, labels: Map<String, String>, inApp: Boolean = false) {
    val fmt = DateFormat.getTimeFormat(LocalContext.current)
    val time = fmt.format(Date(row.at))
    val unknown = stringResource(R.string.log_app_unknown)
    // A line about an app leads with the app, its icon says what happened; lines without one lead with the event.
    val label = { app: String -> if (app.isEmpty()) unknown else labels[app] ?: app }
    val debug = LocalContext.current.isDebuggable
    // Release: one calm green for every cover; the band colours are the classifier's confidence (debug).
    val tone = { band: Band? -> if (!debug) Palette.GreenFg else if (band == null) Palette.Dim else band.color() }
    val times = @Composable { line: String, n: Int -> if (n > 1) stringResource(R.string.log_gap_text, line, pluralStringResource(R.plurals.log_times, n, n)) else line }
    when (row) {
        is LogRow.MaskedRun -> {
            var open by rememberSaveable(row.at) { mutableStateOf(false) }
            Column {
                val episodes = pluralStringResource(R.plurals.log_episodes, row.episodes.size, row.episodes.size)
                Line(
                    time, R.drawable.ic_shield, tone(row.band), if (inApp) episodes else label(row.app),
                    // The time column has when; each episode's time is inside.
                    if (inApp) null else episodes, R.string.log_kind_masked,
                    open = open, onClick = { open = !open }, end = { row.band?.let { BandTag(it) } },
                )
                Detail(open) {
                    row.episodes.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.log_app_line, fmt.format(Date(e.at)), pluralStringResource(R.plurals.log_parts, e.count, e.count)),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = Palette.Dim,
                            )
                            e.band?.let { BandTag(it) }
                        }
                    }
                }
            }
        }
        is LogRow.Masked -> {
            var open by rememberSaveable(row.at) { mutableStateOf(false) }
            val zones = pluralStringResource(R.plurals.log_zones, row.count, row.count)
            val review = stringResource(presetName(row.preset))
            Column {
                val parts = pluralStringResource(R.plurals.log_parts, row.count, row.count)
                Line(
                    time, R.drawable.ic_shield, tone(row.band),
                    if (inApp) parts else if (row.app.isEmpty()) stringResource(R.string.log_masked) else label(row.app),
                    if (inApp) null else parts, R.string.log_kind_masked,
                    // The score and the review are the classifier's detail: only a debug build opens.
                    open = if (debug) open else null, onClick = if (debug) ({ open = !open }) else null, end = { row.band?.let { BandTag(it) } },
                )
                Detail(open && debug) {
                    Text(
                        if (row.band == null || !debug) stringResource(R.string.log_detail_precise, review, zones)
                        else stringResource(R.string.log_detail, review, "%.2f".format(appLocale(), row.score), zones),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Dim,
                    )
                }
            }
        }
        is LogRow.Unanalyzable -> Line(
            time, R.drawable.ic_block, Palette.Dim, if (row.app.isEmpty()) stringResource(R.string.log_unchecked) else label(row.app),
            times(stringResource(R.string.log_gap_text, stringResource(R.string.log_unchecked_why), LogModel.length(row.seconds)), row.count),
            R.string.log_kind_unchecked, quiet = row.brief,
        )
        is LogRow.On -> Line(
            time, R.drawable.ic_power, Palette.GreenFg, stringResource(R.string.log_on),
            null, quiet = true,
        )
        is LogRow.ShareStopped -> Line(
            time, R.drawable.ic_stop, Palette.Dim, stringResource(R.string.log_share_stopped), stringResource(R.string.log_share_stopped_text), quiet = true,
        )
        // A blip of a gap steps back like Off/On; red is for gaps that add up to a minute or more.
        is LogRow.Paired -> Line(
            time, R.drawable.ic_phone, Palette.GreenFg, stringResource(R.string.log_paired), stringResource(R.string.log_paired_text), quiet = true,
        )
        is LogRow.Unprotected -> Line(
            time, R.drawable.ic_shield_off, if (row.brief) Palette.Dim else Palette.RedFg, stringResource(R.string.log_gap),
            times(stringResource(R.string.log_gap_text, stringResource(gapText(row.reason)), LogModel.length(row.seconds)), row.count),
            quiet = row.brief,
        )
        is LogRow.Tamper -> {
            val target = stringResource(
                when (row.target) {
                    TamperTarget.OVERLAY -> R.string.log_tamper_overlay
                    TamperTarget.APP_INFO -> R.string.log_tamper_app_info
                    TamperTarget.UNINSTALL -> R.string.log_tamper_uninstall
                    TamperTarget.ACTIVE_APPS -> R.string.log_tamper_active_apps
                    else -> R.string.log_tamper_accessibility
                },
            )
            Line(time, R.drawable.ic_lock, Palette.Orange, stringResource(R.string.log_tamper), times(target, row.count))
        }
        is LogRow.AppBlocked -> Line(
            time, R.drawable.ic_block, Palette.Orange, label(row.app),
            stringResource(R.string.log_gap_text, stringResource(R.string.log_app_blocked), stringResource(R.string.minutes_short, row.minutes)),
            R.string.log_kind_block,
        )
        is LogRow.BlockedOpen -> Line(
            time, R.drawable.ic_block, Palette.Orange, label(row.app),
            times(
                stringResource(
                    R.string.log_gap_text, stringResource(R.string.log_blocked_open),
                    stringResource(if (row.reason == BlockReason.ESCALATION) R.string.log_blocked_escalation else R.string.log_blocked_rule),
                ),
                row.count,
            ),
            R.string.log_kind_block,
        )
        is LogRow.Off -> Line(
            time, if (row.reason == OffReason.SCREEN_OFF) R.drawable.ic_moon else R.drawable.ic_power, Palette.Dim,
            stringResource(R.string.log_off), stringResource(offText(row.reason)), quiet = true,
        )
        is LogRow.Paused -> Line(
            time, if (row.reason == OffReason.SCREEN_OFF) R.drawable.ic_moon else R.drawable.ic_power, Palette.Dim,
            stringResource(R.string.log_paused, LogModel.length(row.seconds)), stringResource(offText(row.reason)), quiet = true,
        )
    }
}
