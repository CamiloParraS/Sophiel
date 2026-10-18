package dev.sophiel.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyColumn
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
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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
 * A row shows its rule and opens the choice in a sheet, as Ajustes does: three segments beside the name left it no room.
 */
@Composable
fun BoxScope.AppsScreen(container: AppContainer, door: Door, onBack: () -> Unit) {
    val context = LocalContext.current
    val rules by container.appRules.state.collectAsState()
    val bound = MaskWindowService.bound.collectAsState().value != null
    var query by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf<String?>(null) } // the package whose sheet is open
    val loaded by produceState<Pair<List<AppEntry>, Set<String>>?>(null) {
        value = withContext(Dispatchers.IO) { launchableApps(context) to excludedApps(context) }
    }
    val pinned = remember { container.appRules.state.value } // the rules on opening
    val rows = loaded?.let { (apps, excluded) -> appList(apps, pinned, excluded, query) }.orEmpty()
    val options = listOf(stringResource(R.string.app_rule_normal), stringResource(R.string.app_rule_blocked), stringResource(R.string.app_rule_trusted))
    val helps = listOf(stringResource(R.string.app_rule_help_normal), stringResource(R.string.app_rule_help_blocked), stringResource(R.string.apps_trust_warning))
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxSize().behindSheet(picking != null).background(Palette.Chalk)) {
        HeaderBar(stringResource(R.string.set_apps), start = { SButton(null, onBack, icon = R.drawable.ic_back, label = stringResource(R.string.back)) }, end = { UnlockedChip(door) })
        Column(Modifier.weight(1f).cutoutPadding().pageWidth()) {
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
        PreferenceGroup(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp)) {
            item {
                SwitchRow(
                    stringResource(R.string.apps_escalate), settings.escalate && bound,
                    { v -> door.pass { container.settings.update { it.copy(escalate = v) }; if (!v) container.escalation.clear() } },
                    description = if (bound) null else stringResource(R.string.needs_service), enabled = bound,
                )
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 14.dp)) {
            itemsIndexed(rows, key = { _, app -> app.pkg }) { i, app ->
                // One card for the list: the first row rounds its top, the last its bottom.
                val r = 12.dp
                val rowShape = RoundedCornerShape(if (i == 0) r else 0.dp, if (i == 0) r else 0.dp, if (i == rows.lastIndex) r else 0.dp, if (i == rows.lastIndex) r else 0.dp)
                val rule = rules[app.pkg] ?: AppRule.NORMAL
                Column(Modifier.fillMaxWidth().clip(rowShape).background(Palette.Card)) {
                    if (i > 0) Spacer(Modifier.height(1.dp).fillMaxWidth().background(Palette.Border))
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = bound, role = Role.Button) { door.pass { picking = app.pkg } }
                            .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp).alpha(if (bound) 1f else Palette.DISABLED),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.pkg, 32.dp)
                        // Without the service the switch above says why once; the rows only dim.
                        Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            options[AppRule.entries.indexOf(rule)], style = MaterialTheme.typography.titleMedium,
                            color = when (rule) { AppRule.BLOCKED -> Palette.OrangeFg; AppRule.TRUSTED -> Palette.GreenFg; else -> Palette.Dim },
                        )
                        SIcon(R.drawable.ic_chev)
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
        }
    }
    val app = rows.firstOrNull { it.pkg == picking }
    BottomSheet(app != null, app?.label.orEmpty(), { picking = null }) {
        if (app != null) OptionList(
            app.label, options, helps, AppRule.entries.indexOf(rules[app.pkg] ?: AppRule.NORMAL), emptySet(), recommended = -1,
        ) { i -> door.pass { container.appRules.set(app.pkg, AppRule.entries[i]); picking = null } }
    }
}

// Icons and their bar hues are read once per app for the process: lists and the Log redraw them on every scroll and visit.
private val iconCache = android.util.LruCache<String, ImageBitmap>(64)
private val hueCache = java.util.concurrent.ConcurrentHashMap<String, Color>()
private val BarGrey = Color(0xFF999999)

private fun PackageManager.cachedIcon(pkg: String): ImageBitmap? =
    iconCache.get(pkg) ?: runCatching { getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()?.also { iconCache.put(pkg, it) }

/**
 * The hue of an app's icon, so each Por app bar wears its app's colour: the icon averaged to one pixel, then brought
 * to one saturation and brightness so a busy icon doesn't come out muddy. A grey icon (X) stays grey. Grey until read.
 * The grey is a mid grey, not the Dim ink: at the bar's 20% the ink came out as heavy as a selected row.
 */
@Composable
fun appHue(pkg: String): Color {
    val pm = LocalContext.current.packageManager
    val hue by produceState(hueCache[pkg] ?: BarGrey, pkg) {
        value = hueCache[pkg] ?: withContext(Dispatchers.IO) {
            runCatching {
                val small = android.graphics.Bitmap.createScaledBitmap(pm.cachedIcon(pkg)!!.asAndroidBitmap(), 32, 32, true)
                val px = android.graphics.Bitmap.createScaledBitmap(small, 1, 1, true).getPixel(0, 0)
                val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(px, it) }
                if (hsv[1] < .12f) BarGrey else Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], .6f, .85f)))
            }.getOrDefault(BarGrey)
        }.also { hueCache[pkg] = it }
    }
    return hue
}

/** An app's launcher icon, loaded off the main thread; empty space until then or if it can't be read. */
@Composable
fun AppIcon(pkg: String, size: Dp) {
    val pm = LocalContext.current.packageManager
    val icon by produceState(iconCache.get(pkg), pkg) {
        value = iconCache.get(pkg) ?: withContext(Dispatchers.IO) { pm.cachedIcon(pkg) }
    }
    icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) } ?: Spacer(Modifier.size(size))
}
