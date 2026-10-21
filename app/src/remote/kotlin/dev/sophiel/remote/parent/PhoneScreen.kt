package dev.sophiel.remote.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sophiel.R
import dev.sophiel.remote.Rule
import dev.sophiel.settings.AppEntry
import dev.sophiel.settings.AppRule
import dev.sophiel.ui.AppRuleSheet
import dev.sophiel.ui.AppSearch
import dev.sophiel.ui.BtnStyle
import dev.sophiel.ui.HeaderBar
import dev.sophiel.ui.LocalRowMin
import dev.sophiel.ui.LogContent
import dev.sophiel.ui.LogHero
import dev.sophiel.ui.LogModel
import dev.sophiel.ui.SButton
import dev.sophiel.ui.SIcon
import dev.sophiel.ui.SegmentedControl
import dev.sophiel.ui.StatusPage
import dev.sophiel.ui.Tone
import dev.sophiel.ui.appRows
import dev.sophiel.ui.behindSheet
import dev.sophiel.ui.cutoutPadding
import dev.sophiel.ui.pageWidth
import dev.sophiel.ui.ruleOptions
import dev.sophiel.ui.theme.Palette
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One Child phone (D81): tabs Historial (the Child's own Log rows) and Apps (D58's rows and rule sheet, letter tiles), fetched on open
 * with a `log` request. Nothing fetched here outlives the screen.
 */
@Composable
fun BoxScope.PhoneScreen(child: Child, onBack: () -> Unit) {
    val view = remember(child.id) { PhoneView(ParentGraph.api, ParentGraph.session, child.id) }
    val state by view.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(view) { view.fetch() }
    BackHandler(onBack = onBack)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    val ready = state as? PhoneState.Ready
    Column(Modifier.fillMaxSize().behindSheet(picking != null).background(Palette.Chalk)) {
        HeaderBar(
            child.name,
            subtitle = ready?.let { stringResource(if (it.protecting) R.string.par_card_on else R.string.par_card_off) + " · " + ago(it.at, now) },
            start = { SButton(null, onBack, icon = R.drawable.ic_back, label = stringResource(R.string.back)) },
            end = {
                // Ticket 21: "Desvincular" goes here, as an overflow on this bar.
            },
        )
        when (val s = state) {
            PhoneState.Loading -> Page { StatusPage(R.drawable.ic_phone, stringResource(R.string.par_card_loading), "", Tone.Off, spinner = true) }
            PhoneState.Offline -> Page {
                StatusPage(R.drawable.ic_phone, stringResource(R.string.par_card_offline), stringResource(R.string.par_card_offline_text), Tone.Off)
                SButton(stringResource(R.string.par_card_retry), { scope.launch { view.fetch() } }, Modifier.fillMaxWidth().padding(top = 10.dp), style = BtnStyle.Go)
            }
            PhoneState.Failed -> Page {
                StatusPage(R.drawable.ic_warn, stringResource(R.string.par_out_failed), "", Tone.Warn)
                SButton(stringResource(R.string.par_card_retry), { scope.launch { view.fetch() } }, Modifier.fillMaxWidth().padding(top = 10.dp), style = BtnStyle.Go)
            }
            PhoneState.Gone -> Page {
                StatusPage(R.drawable.ic_warn, stringResource(R.string.par_card_gone), stringResource(R.string.par_card_gone_text), Tone.Warn)
                SButton(stringResource(R.string.par_card_remove), { ParentGraph.home.remove(child.id); onBack() }, Modifier.fillMaxWidth().padding(top = 10.dp))
            }
            is PhoneState.Ready -> {
                SegmentedControl(
                    listOf(stringResource(R.string.log_title), stringResource(R.string.set_apps)), tab, { tab = it },
                    Modifier.cutoutPadding().pageWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                )
                if (tab == 0) Historial(s) else Apps(s) { picking = it }
            }
        }
    }
    val app = ready?.apps?.firstOrNull { it.pkg == picking }
    AppRuleSheet(app?.let { AppEntry(it.pkg, it.label) }, app?.rule?.local ?: AppRule.NORMAL, { picking = null }) { r ->
        picking = null
        app?.let { scope.launch { view.setRule(it.pkg, Rule.of(r)) } }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Page(content: @Composable () -> Unit) =
    Column(Modifier.weight(1f).navigationBarsPadding().cutoutPadding().pageWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) { content() }

/** The Child's Historial, from the fetched rows: its hero and its day lists. No Borrar (the Log is the Child's) and no by-app page. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Historial(s: PhoneState.Ready) {
    val model = remember(s.entries) { LogModel.of(s.entries, System.currentTimeMillis(), ZoneId.systemDefault()) }
    val scroll = rememberScrollState()
    var topPx by remember { mutableIntStateOf(0) } // where the scroll starts: the filter pins there
    Column(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { topPx = it.positionInRoot().y.toInt() }.verticalScroll(scroll)) {
        LogHero(model.week, model.days, Modifier, underBar = false)
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().cutoutPadding().pageWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CompositionLocalProvider(LocalRowMin provides 48.dp) {
                if (model.empty) StatusPage(R.drawable.ic_list, stringResource(R.string.log_empty), stringResource(R.string.log_empty_text), Tone.Off, Modifier.padding(vertical = 24.dp))
                else LogContent(model, s.labels, scroll, topPx, onClear = null, onByApp = null)
            }
        }
    }
}

/** D58's rows and rule sheet, drawn from the reply; disabled while the Child's accessibility service is off. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Apps(s: PhoneState.Ready, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val rows = s.apps.filter { it.label.contains(query.trim(), ignoreCase = true) }.map { AppEntry(it.pkg, it.label) }
    val rules = s.apps.associate { it.pkg to it.rule.local }
    val twins = s.apps.groupingBy { it.label }.eachCount()
    Column(Modifier.weight(1f).cutoutPadding().pageWidth()) {
        if (!s.appsEnabled) Note(R.drawable.ic_warn, Palette.OrangeFg, null, stringResource(R.string.needs_service))
        s.unsaved?.let { pkg ->
            Note(R.drawable.ic_warn, Palette.RedFg, stringResource(R.string.par_unsaved_title), stringResource(R.string.par_unsaved_text, s.labels[pkg] ?: pkg))
        }
        AppSearch(query) { query = it }
        val options = ruleOptions()
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 14.dp)) {
            appRows(rows, { rules[it] ?: AppRule.NORMAL }, s.appsEnabled && s.saving == null, twins, options, onPick) { LetterTile(it.label, 32.dp) }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.navigationBarsPadding())
    }
}

/** No icons cross the wire (D81): the label's first letter on ink. */
@Composable
private fun LetterTile(label: String, size: Dp) =
    Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Palette.Moss), contentAlignment = Alignment.Center) {
        Text(label.trim().take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = Palette.Text0)
    }

@Composable
private fun Note(icon: Int, tone: androidx.compose.ui.graphics.Color, title: String?, text: String) = Row(
    Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.tint(tone, .12f)).padding(14.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
) {
    SIcon(icon, tone, 20.dp)
    Column {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = tone)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Fg)
    }
}
