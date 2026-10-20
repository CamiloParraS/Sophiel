package dev.sophiel.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sophiel.R
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.lift

/**
 * Every app that had something covered this week, most first; tapping one shows only its episodes. Times and counts only,
 * never a pixel (rule 3). [pick] is the app opened, kept by the caller so Back steps from the app to the list to the Log.
 */
@Composable
fun LogAppsScreen(model: LogModel, labels: Map<String, String>, pick: String?, onPick: (String?) -> Unit, door: Door, onBack: () -> Unit) {
    val label = { app: String -> labels[app] ?: app }
    val up = { if (pick != null) onPick(null) else onBack() }
    BackHandler(onBack = up)
    val locale = Locale.getDefault()
    val week = model.forApp(pick.orEmpty())
    Column(Modifier.fillMaxSize().background(Palette.Chalk)) {
        HeaderBar(
            if (pick == null) stringResource(R.string.log_by_app) else label(pick),
            start = { SButton(null, up, icon = R.drawable.ic_back, label = stringResource(R.string.back)) },
            end = { UnlockedChip(door) },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().cutoutPadding().pageWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CompositionLocalProvider(LocalRowMin provides 48.dp) {
                when {
                    model.covered.isEmpty() && model.unnamed == 0 -> Text(
                        stringResource(R.string.log_apps_none), Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Dim, textAlign = TextAlign.Center,
                    )
                    pick == null -> {
                        Summary(model.weekMasked, pluralStringResource(R.plurals.log_apps_n, model.covered.size, model.covered.size))
                        PreferenceGroup {
                            val most = model.covered.firstOrNull()?.count ?: 1
                            model.covered.forEach { c -> item { CoverRow(c, most, label(c.app), locale) { onPick(c.app) } } }
                            // The week's count includes these, so the list says where they went instead of adding up short of the hero.
                            if (model.unnamed > 0) item {
                                Text(
                                    stringResource(R.string.log_apps_unnamed, model.unnamed), Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 14.dp),
                                    style = MaterialTheme.typography.bodyMedium, color = Palette.Dim,
                                )
                            }
                        }
                    }
                    else -> {
                        Summary(week.sumOf { it.masked }, stringResource(R.string.log_apps_last, lastTime(model.covered.firstOrNull { it.app == pick }?.lastAt ?: 0L, locale)))
                        week.forEach { d ->
                            PreferenceGroup(title = pluralStringResource(R.plurals.log_day_total, d.masked, dayTitle(d, locale), d.masked)) {
                                d.grouped(LogFilter.ALL).forEach { r -> item { EntryRow(r, labels, inApp = true) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "jue 3:42 p. m." in the phone's language. */
private fun lastTime(at: Long, locale: Locale): String =
    java.time.format.DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEjmm"), locale).format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

/** The week's count large, as the hero says it, with a line under it: the page opens on a figure, not on a list. */
@Composable
private fun Summary(count: Int, line: String) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.semantics(mergeDescendants = true) {}.fillMaxWidth().lift(shape).clip(shape).background(Palette.Card).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(count.toString(), style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"), color = Palette.Fg, maxLines = 1)
        Column {
            Text(stringResource(R.string.log_hero_text), style = MaterialTheme.typography.titleMedium)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
        }
    }
}

/** One app: icon, name, count, its share of the top app as a bar under the name, when it last happened, a chevron. */
@Composable
private fun CoverRow(c: AppCover, most: Int, name: String, locale: Locale, onClick: () -> Unit) {
    val lastText = stringResource(R.string.log_apps_last, lastTime(c.lastAt, locale))
    val said = "$name, ${pluralStringResource(R.plurals.log_apps_week, c.count, c.count)}, $lastText"
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = said }.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(c.app, 28.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.count.toString(), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = Palette.Fg)
            }
            // The bar sits under the name, never behind text (it cut through two lines when it did); one neutral ink, as on the Log's card.
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Btn)) {
                Box(Modifier.fillMaxWidth((c.count.toFloat() / most).coerceAtLeast(.04f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Dim))
            }
            Text(lastText, style = MaterialTheme.typography.bodySmall, color = Palette.Fg.copy(alpha = .7f), maxLines = 1)
        }
        SIcon(R.drawable.ic_chev, Palette.Dim, 16.dp)
    }
}
