package dev.sophiel.ui

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sophiel.R
import dev.sophiel.log.EventLog
import dev.sophiel.log.OffReason
import dev.sophiel.ui.theme.Ease
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.motion
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/** Historial (D39, D44): draws a [LogModel]. Reads the file off the main thread; Borrar historial asks first. */
@Composable
fun LogScreen(log: EventLog, door: Door, onBack: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    var version by remember { mutableIntStateOf(0) }
    val model by produceState<LogModel?>(null, version) {
        value = withContext(Dispatchers.IO) { LogModel.of(log.entries(), System.currentTimeMillis(), zone) }
    }
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
            else -> Content(m, Modifier.weight(1f), onClear = { confirm = true })
        }
    }
    if (confirm) ConfirmDialog(
        stringResource(R.string.log_clear_title), stringResource(R.string.log_clear_text),
        confirmLabel = stringResource(R.string.log_clear_confirm), cancelLabel = stringResource(R.string.pin_cancel),
        onConfirm = { confirm = false; scope.launch(Dispatchers.IO) { log.clear(); version++ } }, onDismiss = { confirm = false },
    )
}

@Composable
private fun Content(model: LogModel, modifier: Modifier, onClear: () -> Unit) {
    val locale = Locale.getDefault()
    var selected by rememberSaveable { mutableIntStateOf(model.days.lastIndex) }
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    val day = model.days[selected]
    val dayTitle = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM").let { p ->
        day.date.format(java.time.format.DateTimeFormatter.ofPattern(p, locale)).replaceFirstChar { it.titlecase(locale) }
    }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(
                pluralStringResource(R.plurals.log_week, model.weekMasked, model.weekMasked),
                Modifier.padding(start = 4.dp, bottom = 6.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Dim,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                model.days.forEachIndexed { i, d -> DayCell(d, i == selected, Modifier.weight(1f)) { selected = i } }
            }
        }
        Text(
            if (day.today) stringResource(R.string.log_today_title, if (locale.language == "en") dayTitle else dayTitle.replaceFirstChar { it.lowercase(locale) }) else dayTitle,
            style = MaterialTheme.typography.titleMedium,
        )
        SegmentedControl(
            listOf(R.string.log_filter_all, R.string.log_filter_masked, R.string.log_filter_others).map { stringResource(it) },
            filter.ordinal, { filter = LogFilter.entries[it] },
        )
        Crossfade(selected to filter, animationSpec = motion(tween(110)), label = "day") { (s, f) ->
            val d = model.days[s]
            val rows = d.rows(f)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(d.masked, R.string.log_stat_masked, Modifier.weight(1f))
                    Stat(d.unanalyzable, R.string.log_stat_unchecked, Modifier.weight(1f))
                    Stat(d.off, R.string.log_stat_off, Modifier.weight(1f))
                }
                if (rows.isEmpty()) Text(
                    stringResource(if (f == LogFilter.MASKED) R.string.log_nothing_masked else R.string.log_nothing),
                    Modifier.fillMaxWidth().padding(vertical = 18.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim, textAlign = TextAlign.Center,
                ) else PreferenceGroup { rows.forEach { r -> item { EntryRow(r) } } }
            }
        }
        PillButton(stringResource(R.string.log_clear), onClear, Modifier.align(Alignment.CenterHorizontally), style = PillStyle.Destructive, icon = R.drawable.ic_trash)
    }
}

@Composable
private fun DayCell(day: LogDay, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    val ink = if (on) Color.White else Palette.Fg
    Column(
        modifier.clip(shape).background(if (on) primary else Palette.Card).border(1.dp, if (on) Color.Transparent else Palette.Border, shape)
            .clickable(onClick = onClick).padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (day.today) stringResource(R.string.log_today) else day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase(),
            style = MaterialTheme.typography.labelSmall, color = if (on) Color.White.copy(alpha = .8f) else Palette.Dim, maxLines = 1,
        )
        Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = ink)
        Box(Modifier.height(18.dp).padding(top = 2.dp), contentAlignment = Alignment.BottomCenter) {
            // The bar grows with the day's masked count and takes the colour of its highest band (D44).
            val h = if (day.masked == 0) 3.dp else (4 + day.masked * 3.5f).coerceAtMost(16f).dp
            Box(Modifier.width(14.dp).height(h).clip(RoundedCornerShape(3.dp)).background(if (on) Color.White.copy(alpha = .85f) else day.top.color()))
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
private fun EntryRow(row: LogRow) {
    val time = DateFormat.getTimeFormat(LocalContext.current).format(java.util.Date(row.at))
    val clock = @Composable { Text(time, style = MaterialTheme.typography.bodyMedium, color = Palette.Dim) }
    when (row) {
        is LogRow.Masked -> {
            var open by rememberSaveable(row.at) { mutableStateOf(false) }
            val tint = if (row.band == null) Palette.Dim else row.band.color()
            val zones = pluralStringResource(R.plurals.log_zones, row.count, row.count)
            val review = stringResource(presetName(row.preset))
            Column {
                PrefRow(
                    stringResource(R.string.log_masked), description = pluralStringResource(R.plurals.log_parts, row.count, row.count),
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
            stringResource(R.string.log_unchecked), description = stringResource(R.string.log_unchecked_text, row.seconds),
            icon = R.drawable.ic_block, end = clock,
        )
        is LogRow.On -> PrefRow(
            stringResource(R.string.log_on),
            description = stringResource(R.string.log_on_text, stringResource(presetName(row.preset)).lowercase(), stringResource(careName(row.sensitivity)).lowercase()),
            icon = R.drawable.ic_power, iconTint = Palette.GreenFg, end = clock,
        )
        is LogRow.Off -> PrefRow(
            stringResource(R.string.log_off), description = stringResource(offText(row.reason)),
            icon = if (row.reason == OffReason.SCREEN_OFF) R.drawable.ic_moon else R.drawable.ic_power, end = clock,
        )
    }
}
