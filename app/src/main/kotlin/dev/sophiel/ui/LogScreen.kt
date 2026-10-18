package dev.sophiel.ui

import android.text.format.DateFormat
import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
private const val PAGE = 20

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

/** Historial (D39, D44): draws a [LogModel] in Status's language, a dark tile hero with the week's count and a chalk sheet over it. */
@Composable
fun LogScreen(log: EventLog, door: Door, onBack: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    var version by remember { mutableIntStateOf(0) }
    val pm = LocalContext.current.packageManager
    val read by produceState<Pair<LogModel, Map<String, String>>?>(null, version) {
        value = withContext(Dispatchers.IO) {
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
            m to apps.filter(String::isNotEmpty).toSet().associateWith { pkg ->
                appLabel(pm, pkg)
            }
        }
    }
    val model = read?.first
    val labels = read?.second.orEmpty()
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Light icons on the dark hero; back to dark ones when leaving.
    val view = LocalView.current
    DisposableEffect(view) {
        val bars = WindowCompat.getInsetsController((view.context as Activity).window, view)
        bars.isAppearanceLightStatusBars = false
        onDispose { bars.isAppearanceLightStatusBars = true }
    }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var heroPx by remember { mutableIntStateOf(0) }
    var barPx by remember { mutableIntStateOf(0) }
    val lip = with(density) { 20.dp.roundToPx() }
    // The top bar turns solid once the sheet's edge reaches it, so the back button never floats over chalk.
    val solid by remember { derivedStateOf { heroPx > 0 && heroPx - lip - scroll.value <= barPx } }
    val barFill by animateColorAsState(if (solid) Moss else Color.Transparent, motion(tween(160)), label = "bar")
    Box(Modifier.fillMaxSize().background(Palette.Chalk)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            val m = model
            LogHero(m?.week?.masked, m?.days.orEmpty(), Modifier.onSizeChanged { heroPx = it.height })
            // The sheet rides 20 dp over the hero, as on Status; layout, not offset, so it leaves no gap at the bottom.
            Column(
                Modifier.fillMaxWidth().layout { mm, c ->
                    val p = mm.measure(c)
                    layout(p.width, p.height - lip) { p.place(0, -lip) }
                }.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Palette.Chalk)
                    .navigationBarsPadding().cutoutPadding().pageWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CompositionLocalProvider(LocalRowMin provides 48.dp) {
                    when {
                        m == null -> Unit
                        m.empty -> StatusPage(R.drawable.ic_list, stringResource(R.string.log_empty), stringResource(R.string.log_empty_text), Tone.Off, Modifier.padding(vertical = 24.dp))
                        else -> Content(m, labels, onClear = { confirm = true })
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(barFill).statusBarsPadding().cutoutPadding().onSizeChanged { barPx = it.height }.padding(start = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SButton(null, onBack, style = BtnStyle.OnDark, icon = R.drawable.ic_back, label = stringResource(R.string.back))
            Text(stringResource(R.string.log_title), Modifier.asHeading().weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.titleMedium, color = Text0)
            UnlockedChip(door)
        }
    }
    if (confirm) ConfirmDialog(
        stringResource(R.string.log_clear_title), stringResource(R.string.log_clear_text),
        confirmLabel = stringResource(R.string.log_clear_confirm), cancelLabel = stringResource(R.string.pin_cancel),
        onConfirm = { confirm = false; scope.launch(Dispatchers.IO) { log.clear(); version++ } }, onDismiss = { confirm = false },
    )
}

/** The week at a glance: the count as the one headline, a bar per day under it, on Status's tile grid. */
@Composable
private fun LogHero(masked: Int?, days: List<LogDay>, modifier: Modifier) {
    Box(modifier.fillMaxWidth().background(Moss)) {
        Canvas(Modifier.matchParentSize()) { drawGrid(Page.OFF, Palette.HeroOff, 0L, -100_000L, true, emptyList()) }
        // Count left, the week's bars right: one short band, so the per-app card and the rows start higher.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().cutoutPadding().padding(start = 22.dp, end = 22.dp, top = 76.dp, bottom = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(masked?.toString().orEmpty(), style = MaterialTheme.typography.displayLarge, color = Text0)
                Text(stringResource(R.string.log_hero_text), style = MaterialTheme.typography.bodyMedium, color = Text1)
            }
            if (days.isNotEmpty()) DayBars(days, Modifier.width(168.dp))
        }
    }
}

@Composable
private fun ColumnScope.Content(model: LogModel, labels: Map<String, String>, onClear: () -> Unit) {
    val locale = Locale.getDefault()
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    val week = model.week
    val label = { app: String -> labels[app] ?: app }
    // Each figure wears the icon of its rows below, so the summary and the list read as one thing.
    // The verdict first, then only the figures that aren't zero. Blocks are the parent's own rules, so they never make it "look".
    val look = week.unprotected > 0 || week.intentos > 0
    Text(
        stringResource(if (look) R.string.log_verdict_look else R.string.log_verdict_ok), Modifier.padding(start = 4.dp),
        style = MaterialTheme.typography.titleMedium, color = if (look) Palette.Fg else Palette.GreenFg,
    )
    // Three across until large text, where a third is too narrow for "Aperturas": then one per row.
    val stack = LocalDensity.current.fontScale >= 1.5f
    val stats: @Composable (Modifier) -> Unit = { m ->
        // The count, with the time it adds up to under it: hours of overnight gap alone would read as exposure.
        if (week.unprotected > 0) Stat(
            week.unprotected, R.drawable.ic_shield_off, stringResource(R.string.log_stat_gap),
            LogModel.length(week.unprotectedSeconds), Palette.RedFg, m,
        )
        if (week.intentos > 0) Stat(week.intentos, R.drawable.ic_lock, stringResource(R.string.log_stat_attempts), null, Palette.OrangeFg, m)
        if (week.bloqueos > 0) Stat(week.bloqueos, R.drawable.ic_block, stringResource(R.string.log_stat_blocks), null, Palette.Fg, m)
    }
    if (look || week.bloqueos > 0) {
        if (stack) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { stats(Modifier.fillMaxWidth()) }
        else Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) { stats(Modifier.weight(1f)) }
    }
    if (week.perApp.isNotEmpty()) PreferenceGroup(title = stringResource(R.string.log_by_app)) { item { AppBars(week.perApp.take(5), label) } }
    SegmentedControl(
        listOf(R.string.log_filter_all, R.string.log_filter_masked, R.string.log_filter_others).map { stringResource(it) },
        filter.ordinal, { filter = LogFilter.entries[it] },
    )
    // A busy week has hundreds of rows, all composed at once in this scroll: show a page, add a page per tap.
    var shown by rememberSaveable(filter) { mutableIntStateOf(PAGE) }
    val days = model.days.asReversed().map { it to it.grouped(filter) }.filter { it.second.isNotEmpty() }
    val total = days.sumOf { it.second.size }
    if (days.isEmpty()) Text(
        stringResource(if (filter == LogFilter.MASKED) R.string.log_nothing_masked else R.string.log_nothing),
        Modifier.fillMaxWidth().padding(vertical = 18.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim, textAlign = TextAlign.Center,
    )
    var left = shown
    for ((d, rows) in days) {
        if (left <= 0) break
        val day = rows.take(left)
        left -= day.size
        val title = if (d.today) stringResource(R.string.log_today) else DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM").let { p ->
            d.date.format(java.time.format.DateTimeFormatter.ofPattern(p, locale)).replaceFirstChar { it.titlecase(locale) }
        }
        PreferenceGroup(title = pluralStringResource(R.plurals.log_day_total, d.masked, title, d.masked)) { day.forEach { r -> item { EntryRow(r, labels) } } }
    }
    if (total > shown) SButton(
        stringResource(R.string.log_more, total - shown), { shown += PAGE }, Modifier.align(Alignment.CenterHorizontally),
    )
    SButton(stringResource(R.string.log_clear), onClear, Modifier.align(Alignment.CenterHorizontally), style = BtnStyle.Destructive, icon = R.drawable.ic_trash)
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
                    color = if (d.today) Text0 else Text1, maxLines = 1,
                )
                Box(Modifier.padding(top = 2.dp).height(40.dp), contentAlignment = Alignment.BottomCenter) {
                    val h = if (d.masked == 0) 2.dp else (6 + 34f * d.masked / most).dp
                    Box(Modifier.width(12.dp).height(h).clip(RoundedCornerShape(3.dp)).background(if (d.masked == 0) Text1.copy(alpha = .3f) else ink))
                }
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase(), Modifier.padding(top = 4.dp),
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
                Box(Modifier.fillMaxWidth(n.toFloat() / most).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(appHue(app).copy(alpha = .2f)))
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

/** A figure for the week, under its rows' icon, with an optional [detail] line; it takes [tint] only when not zero, so a quiet week reads quiet. */
@Composable
private fun Stat(value: Int, @DrawableRes icon: Int, label: String, detail: String?, tint: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val ink = if (value == 0) Palette.Dim else tint
    Column(modifier.fillMaxHeight().lift(shape).clip(shape).background(Palette.Card).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SIcon(icon, ink, 16.dp)
            Text(value.toString(), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = if (value == 0) Palette.Fg else tint, maxLines = 1)
        }
        Text(label, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Dim, maxLines = 2)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = tint, maxLines = 1)
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
    quiet: Boolean = false, open: Boolean? = null, onClick: (() -> Unit)? = null, end: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .heightIn(min = if (onClick != null) 48.dp else if (quiet) 40.dp else 52.dp).padding(horizontal = Pad, vertical = if (quiet) 6.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, Modifier.width(timeWidth()), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = Palette.Dim, maxLines = 1)
        SIcon(icon, tint)
        Column(Modifier.weight(1f)) {
            // A quiet line is one plain line, a marker between events rather than an event.
            if (quiet) Text(
                if (text == null) title else stringResource(R.string.log_gap_text, title, text),
                style = MaterialTheme.typography.bodyMedium, color = Palette.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis,
            ) else {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) // two lines: at large text one held "No se pudo r…"
                if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
            }
        }
        end?.invoke()
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

@Composable
private fun EntryRow(row: LogRow, labels: Map<String, String>) {
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
                Line(
                    time, R.drawable.ic_shield, tone(row.band), label(row.app),
                    // The time column has when; each episode's time is inside.
                    pluralStringResource(R.plurals.log_episodes, row.episodes.size, row.episodes.size),
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
                Line(
                    time, R.drawable.ic_shield, tone(row.band),
                    if (row.app.isEmpty()) stringResource(R.string.log_masked) else label(row.app),
                    pluralStringResource(R.plurals.log_parts, row.count, row.count),
                    // The score and the review are the classifier's detail: only a debug build opens.
                    open = if (debug) open else null, onClick = if (debug) ({ open = !open }) else null, end = { row.band?.let { BandTag(it) } },
                )
                Detail(open && debug) {
                    Text(
                        if (row.band == null || !debug) stringResource(R.string.log_detail_precise, review, zones)
                        else stringResource(R.string.log_detail, review, "%.2f".format(Locale.getDefault(), row.score), zones),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Dim,
                    )
                }
            }
        }
        is LogRow.Unanalyzable -> Line(
            time, R.drawable.ic_block, Palette.Dim, if (row.app.isEmpty()) stringResource(R.string.log_unchecked) else label(row.app),
            // With no app the title already says it; the line keeps only how long.
            times(if (row.app.isEmpty()) LogModel.length(row.seconds) else stringResource(R.string.log_gap_text, stringResource(R.string.log_unchecked), LogModel.length(row.seconds)), row.count),
        )
        is LogRow.On -> Line(
            time, R.drawable.ic_power, Palette.GreenFg, stringResource(R.string.log_on),
            null, quiet = true,
        )
        is LogRow.ShareStopped -> Line(
            time, R.drawable.ic_stop, Palette.Dim, stringResource(R.string.log_share_stopped), stringResource(R.string.log_share_stopped_text), quiet = true,
        )
        is LogRow.Unprotected -> Line(
            time, R.drawable.ic_shield_off, Palette.RedFg, stringResource(R.string.log_gap),
            stringResource(R.string.log_gap_text, stringResource(gapText(row.reason)), LogModel.length(row.seconds)),
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
