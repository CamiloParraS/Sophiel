package dev.sophiel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.sophiel.AppContainer
import dev.sophiel.R
import dev.sophiel.capture.MaskWindowService
import dev.sophiel.settings.AppEntry
import dev.sophiel.settings.AppRule
import dev.sophiel.settings.appList
import dev.sophiel.settings.excludedApps
import dev.sophiel.settings.launchableApps
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.lift
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ajustes > Apps (D58), behind the door: launchable apps, each Normal, Bloqueada or De confianza. Changed ones are pinned first as
 * they were on opening, so a row does not jump under the finger. Without the service nothing is enforced: rows disabled.
 */
@Composable
fun AppsScreen(container: AppContainer, door: Door, onBack: () -> Unit) {
    val context = LocalContext.current
    val rules by container.appRules.state.collectAsState()
    val bound = MaskWindowService.bound.collectAsState().value != null
    var query by rememberSaveable { mutableStateOf("") }
    val loaded by produceState<Pair<List<AppEntry>, Set<String>>?>(null) {
        value = withContext(Dispatchers.IO) { launchableApps(context) to excludedApps(context) }
    }
    val pinned = remember { container.appRules.state.value } // the rules on opening
    val rows = loaded?.let { (apps, excluded) -> appList(apps, pinned, excluded, query) }.orEmpty()
    val options = listOf(stringResource(R.string.app_rule_normal), stringResource(R.string.app_rule_blocked), stringResource(R.string.app_rule_trusted))
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxSize().background(Palette.Chalk)) {
        HeaderBar(stringResource(R.string.set_apps), start = { FlatIconButton(R.drawable.ic_back, stringResource(R.string.back), onBack) }, end = { UnlockedChip(door) })
        Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp).fillMaxWidth().lift(shape).clip(shape).background(Palette.Card)) {
            BasicTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Fg), cursorBrush = SolidColor(Palette.Fg),
                decorationBox = { field ->
                    if (query.isEmpty()) Text(stringResource(R.string.apps_search), style = MaterialTheme.typography.bodyLarge, color = Palette.Dim)
                    field()
                },
            )
        }
        val settings by container.settings.state.collectAsState()
        SwitchRow(
            stringResource(R.string.apps_escalate), settings.escalate && bound,
            { v -> door.pass { container.settings.update { it.copy(escalate = v) }; if (!v) container.escalation.clear() } },
            Modifier.padding(top = 10.dp), enabled = bound,
        )
        Text(
            stringResource(R.string.apps_trust_warning), style = MaterialTheme.typography.bodySmall, color = Palette.Dim,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
            items(rows, key = { it.pkg }) { app ->
                Row(
                    Modifier.fillMaxWidth().background(Palette.Card).heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp).alpha(if (bound) 1f else .5f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app.pkg, 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        if (!bound) Text(stringResource(R.string.needs_service), style = MaterialTheme.typography.bodySmall, color = Palette.Dim)
                    }
                    SegmentedControl(
                        options, AppRule.entries.indexOf(rules[app.pkg] ?: AppRule.NORMAL),
                        { i -> door.pass { container.appRules.set(app.pkg, AppRule.entries[i]) } },
                        Modifier.width(260.dp), disabled = if (bound) emptySet() else options.indices.toSet(),
                    )
                }
                Spacer(Modifier.height(1.dp).fillMaxWidth().background(Palette.Border))
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** An app's launcher icon, loaded off the main thread; empty space until then or if it can't be read. */
@Composable
fun AppIcon(pkg: String, size: Dp) {
    val pm = LocalContext.current.packageManager
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) } ?: Spacer(Modifier.size(size))
}
