package dev.sophiel.ui

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import dev.sophiel.R
import dev.sophiel.log.EventLog
import dev.sophiel.log.GapReason
import dev.sophiel.log.OffReason
import dev.sophiel.log.BlockReason
import dev.sophiel.settings.appLabel
import dev.sophiel.log.TamperTarget
import dev.sophiel.ui.theme.Ease
import dev.sophiel.ui.theme.Palette
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

/** Historial (D39, D44): draws a [LogModel]. Reads the file off the main thread; Borrar historial asks first. */
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
    Column(Modifier.fillMaxSize()) {
        HeaderBar(stringResource(R.string.log_title), start = { FlatIconButton(R.drawable.ic_back, stringResource(R.string.back), onBack) })
        UnlockedBanner(door)
        val m = model
        when {
            m == null -> Box(Modifier.weight(1f))
            m.empty -> Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                StatusPage(R.drawable.ic_list, stringResource(R.string.log_empty), stringResource(R.string.log_empty_text), Tone.Off)
            }
            else -> Content(m, labels, zone, Modifier.weight(1f), onClear = { confirm = true })
        }
    }
    if (confirm) ConfirmDialog(
        stringResource(R.string.log_clear_title), stringResource(R.string.log_clear_text),
        confirmLabel = stringResource(R.string.log_clear_confirm), cancelLabel = stringResource(R.string.pin_cancel),
        onConfirm = { confirm = false; scope.launch(Dispatchers.IO) { log.clear(); version++ } }, onDismiss = { confirm = false },
    )
}

@Composable
private fun Content(model: LogModel, labels: Map<String, String>, zone: ZoneId, modifier: Modifier, onClear: () -> Unit) {
    val locale = Locale.getDefault()
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    val week = model.week
    val label = { app: String -> labels[app] ?: app }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.log_week_title), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(week.masked, R.string.log_stat_masked, Modifier.weight(1f))
            Stat(week.intentos, R.string.log_stat_attempts, Modifier.weight(1f))
            Stat(week.bloqueos, R.string.log_stat_blocks, Modifier.weight(1f))
        }
        week.perApp.firstOrNull()?.let { (app, n) ->
            Text(stringResource(R.string.log_top_app, label(app), n), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Dim)
        }
        DayBars(model.days)
        if (week.perApp.isNotEmpty()) {
            Text(stringResource(R.string.log_by_app), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Dim)
            AppBars(week.perApp.take(5), label)
        }
        SegmentedControl(
            listOf(R.string.log_filter_all, R.string.log_filter_masked, R.string.log_filter_others).map { stringResource(it) },
            filter.ordinal, { filter = LogFilter.entries[it] },
        )
        // A busy week has hundreds of rows, all composed at once in this scroll: show a page, add a page per tap.
        var shown by rememberSaveable(filter) { mutableIntStateOf(PAGE) }
        val days = model.days.asReversed().map { it to it.rows(filter) }.filter { it.second.isNotEmpty() }
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
        if (total > shown) PillButton(
            stringResource(R.string.log_more, total - shown), { shown += PAGE }, Modifier.align(Alignment.CenterHorizontally),
        )
        PillButton(stringResource(R.string.log_clear), onClear, Modifier.align(Alignment.CenterHorizontally), style = PillStyle.Destructive, icon = R.drawable.ic_trash)
    }
}

/** D59: masked episodes per day, 7 bars, today last; the bar takes the colour of the day's highest band (D44). */
@Composable
private fun DayBars(days: List<LogDay>) {
    val most = days.maxOf { it.masked }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        days.forEach { d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d.masked.toString(), style = MaterialTheme.typography.labelSmall, color = Palette.Dim)
                Box(Modifier.height(56.dp), contentAlignment = Alignment.BottomCenter) {
                    val h = if (d.masked == 0) 3.dp else (4 + 52f * d.masked / most).dp
                    Box(Modifier.width(16.dp).height(h).clip(RoundedCornerShape(3.dp)).background(d.top.color()))
                }
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase(),
                    style = MaterialTheme.typography.labelSmall, color = if (d.today) MaterialTheme.colorScheme.primary else Palette.Dim, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AppBars(apps: List<Pair<String, Int>>, label: (String) -> String) {
    val most = apps.first().second
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        apps.forEach { (app, n) ->
            Column {
                Row {
                    Text(label(app), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(n.toString(), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
                }
                Box(Modifier.padding(top = 3.dp).fillMaxWidth(n.toFloat() / most).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
private fun Stat(n: Int, @StringRes label: Int, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.clip(shape).background(Palette.Card).border(1.dp, Palette.Border, shape).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(n.toString(), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = Palette.Dim)
    }
}

@Composable
private fun EntryRow(row: LogRow, labels: Map<String, String>) {
    val time = DateFormat.getTimeFormat(LocalContext.current).format(java.util.Date(row.at))
    val unknown = stringResource(R.string.log_app_unknown)
    val label = { app: String -> if (app.isEmpty()) unknown else labels[app] ?: app }
    val clock = @Composable { Text(time, style = MaterialTheme.typography.bodyMedium, color = Palette.Dim) }
    when (row) {
        is LogRow.Masked -> {
            var open by rememberSaveable(row.at) { mutableStateOf(false) }
            val tint = if (row.band == null) Palette.Dim else row.band.color()
            val zones = pluralStringResource(R.plurals.log_zones, row.count, row.count)
            val review = stringResource(presetName(row.preset))
            Column {
                PrefRow(
                    stringResource(R.string.log_masked),
                    description = stringResource(R.string.log_app_line, label(row.app), pluralStringResource(R.plurals.log_parts, row.count, row.count)),
                    icon = R.drawable.ic_shield, iconTint = tint, onClick = { open = !open },
                    end = {
                        Column(horizontalAlignment = Alignment.End) {
                            clock()
                            when (row.band) {
                                Band.HIGH -> Tag(stringResource(R.string.log_band_high), TagKind.VerySafe)
                                Band.MID -> Tag(stringResource(R.string.log_band_mid), TagKind.Safe)
                                Band.LOW -> Tag(stringResource(R.string.log_band_low), TagKind.Doubtful)
                                null -> Unit
                            }
                        }
                    },
                )
                AnimatedVisibility(open, enter = expandVertically(motion(tween(240, easing = Ease))), exit = shrinkVertically(motion(tween(240, easing = Ease)))) {
                    Text(
                        if (row.band == null) stringResource(R.string.log_detail_precise, review, zones)
                        else stringResource(R.string.log_detail, review, "%.2f".format(Locale.getDefault(), row.score), zones),
                        Modifier.padding(start = 44.dp, end = 14.dp, bottom = 10.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim,
                    )
                }
            }
        }
        is LogRow.Unanalyzable -> PrefRow(
            stringResource(R.string.log_unchecked), description = stringResource(R.string.log_unchecked_text, label(row.app), row.seconds),
            icon = R.drawable.ic_block, end = clock,
        )
        is LogRow.On -> PrefRow(
            stringResource(R.string.log_on),
            description = stringResource(R.string.log_on_text, stringResource(presetName(row.preset)).lowercase(), stringResource(careName(row.sensitivity)).lowercase()),
            icon = R.drawable.ic_power, iconTint = Palette.GreenFg, end = clock,
        )
        is LogRow.ShareStopped -> PrefRow(
            stringResource(R.string.log_share_stopped), description = stringResource(R.string.log_share_stopped_text),
            icon = R.drawable.ic_stop, end = clock,
        )
        is LogRow.Unprotected -> PrefRow(
            stringResource(R.string.log_gap),
            description = stringResource(R.string.log_gap_text, stringResource(gapText(row.reason)), LogModel.length(row.seconds)),
            icon = R.drawable.ic_shield_off, iconTint = Palette.RedFg, end = clock,
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
            PrefRow(
                stringResource(R.string.log_tamper),
                description = if (row.count > 1) stringResource(R.string.log_gap_text, target, pluralStringResource(R.plurals.log_times, row.count, row.count)) else target,
                icon = R.drawable.ic_lock, iconTint = Palette.Orange, end = clock,
            )
        }
        is LogRow.AppBlocked -> PrefRow(
            stringResource(R.string.log_app_blocked),
            description = stringResource(R.string.log_gap_text, label(row.app), stringResource(R.string.minutes_short, row.minutes)),
            icon = R.drawable.ic_block, iconTint = Palette.Orange, end = clock,
        )
        is LogRow.BlockedOpen -> {
            val why = stringResource(R.string.log_gap_text, label(row.app), stringResource(if (row.reason == BlockReason.ESCALATION) R.string.log_blocked_escalation else R.string.log_blocked_rule))
            PrefRow(
                stringResource(R.string.log_blocked_open),
                description = if (row.count > 1) stringResource(R.string.log_gap_text, why, pluralStringResource(R.plurals.log_times, row.count, row.count)) else why,
                icon = R.drawable.ic_block, iconTint = Palette.Orange, end = clock,
            )
        }
        is LogRow.Off -> PrefRow(
            stringResource(R.string.log_off), description = stringResource(offText(row.reason)),
            icon = if (row.reason == OffReason.SCREEN_OFF) R.drawable.ic_moon else R.drawable.ic_power, end = clock,
        )
    }
}
