package dev.sophiel.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sophiel.AppContainer
import dev.sophiel.R
import dev.sophiel.capture.MaskWindowService
import dev.sophiel.core.Sensitivity
import dev.sophiel.settings.ParentPreset
import dev.sophiel.ui.theme.Palette

private const val DEBUG_TAPS = 7

private enum class Pick { CARE, HOW }

/**
 * Ajustes (D38, D40, D44), behind the door. Every change goes through [Door.pass], which extends the unlock.
 * Seven taps on the version label open the debug menu, in every build (D38).
 */
@Composable
fun BoxScope.SettingsScreen(container: AppContainer, door: Door, onBack: () -> Unit, onHistory: () -> Unit, onApps: () -> Unit, onDebug: () -> Unit) {
    var changingPin by rememberSaveable { mutableStateOf(false) }
    if (changingPin) {
        // Already behind the door, so no current PIN is asked.
        CreatePinScreen(
            onCreate = { container.pin.set(it); door.pass { changingPin = false } },
            title = stringResource(R.string.set_change_pin), onBack = { changingPin = false },
        )
        return
    }
    val settings by container.settings.state.collectAsState()
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    val canShoot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE // D35
    val bound = MaskWindowService.bound.collectAsState().value != null
    val taps = remember { mutableIntStateOf(0) }
    val presets = ParentPreset.entries
    val android14 = stringResource(R.string.needs_android14)
    var picking by rememberSaveable { mutableStateOf<Pick?>(null) }
    val careNames = listOf(R.string.care_strict, R.string.care_normal, R.string.care_relaxed).map { stringResource(it) }
    val presetNames = listOf(R.string.preset_light, R.string.preset_balanced, R.string.preset_thorough, R.string.preset_precise).map { stringResource(it) }
    val careHelps = listOf(R.string.care_help_strict, R.string.care_help_normal, R.string.care_help_relaxed).map { stringResource(it) }
    val presetHelps = listOf(R.string.how_help_light, R.string.how_help_balanced, R.string.how_help_thorough, R.string.how_help_precise).map { stringResource(it) }
    val careIx = Sensitivity.entries.indexOf(settings.sensitivity)
    val presetIx = presets.indexOf(settings.preset)
    Column(Modifier.fillMaxSize().behindSheet(picking != null).background(Palette.Chalk)) { // the page; the groups are Surface cards on it
        HeaderBar(stringResource(R.string.settings_title), start = { SButton(null, onBack, icon = R.drawable.ic_back, label = stringResource(R.string.back)) }, end = { UnlockedChip(door) })
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).cutoutPadding().pageWidth().padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PreferenceGroup(title = stringResource(R.string.group_protection)) {
                item {
                    PrefRow(
                        stringResource(R.string.set_care), description = careHelps[careIx], icon = R.drawable.ic_sliders,
                        onClick = { door.pass { picking = Pick.CARE } }, end = { Value(careNames[careIx]) },
                    )
                }
                item {
                    PrefRow(
                        stringResource(R.string.set_how), description = presetHelps[presetIx], icon = R.drawable.ic_layers,
                        onClick = { door.pass { picking = Pick.HOW } }, end = { Value(presetNames[presetIx]) },
                    )
                }
                item {
                    SwitchRow(
                        stringResource(R.string.set_keep), settings.keepProtecting && bound,
                        { v -> door.pass { container.settings.update { it.copy(keepProtecting = v) } } },
                        description = stringResource(if (bound) R.string.set_keep_text else R.string.needs_full_mask),
                        icon = R.drawable.ic_shield, enabled = bound,
                    )
                }
                item {
                    PrefRow(
                        stringResource(R.string.set_apps), description = stringResource(R.string.set_apps_text), icon = R.drawable.ic_block,
                        onClick = { door.pass(onApps) }, end = { SIcon(R.drawable.ic_chev) },
                    )
                }
                item {
                    PrefRow(
                        stringResource(R.string.set_history), description = stringResource(R.string.set_history_text), icon = R.drawable.ic_list,
                        onClick = { door.pass(onHistory) }, end = { SIcon(R.drawable.ic_chev) },
                    )
                }
            }
            PreferenceGroup(title = stringResource(R.string.group_mask)) {
                item {
                    SwitchRow(
                        stringResource(R.string.set_label), settings.showLabel,
                        { v -> door.pass { container.settings.update { it.copy(showLabel = v) } } },
                        description = stringResource(R.string.set_label_text), icon = R.drawable.ic_tag,
                    )
                }
                item {
                    SwitchRow(
                        stringResource(R.string.set_peek), settings.peekUnderMask && canShoot,
                        { v -> door.pass { container.settings.update { it.copy(peekUnderMask = v) } } },
                        description = if (canShoot) stringResource(R.string.set_peek_text) else android14,
                        icon = R.drawable.ic_eye, enabled = canShoot,
                    )
                }
            }
            PreferenceGroup(title = stringResource(R.string.group_security)) {
                item {
                    PrefRow(
                        stringResource(R.string.set_change_pin), description = stringResource(R.string.set_change_pin_text), icon = R.drawable.ic_key,
                        onClick = { door.pass { changingPin = true } }, end = { SIcon(R.drawable.ic_chev) },
                    )
                }
            }
            // Not a setting, so no row: a fact, under the groups.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                SIcon(R.drawable.ic_shield_ok, Palette.GreenFg, size = 16.dp)
                Text(stringResource(R.string.set_no_internet_text), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Dim)
            }
        }
        Text(
            stringResource(R.string.version_label, version),
            Modifier.fillMaxWidth().clickable { if (++taps.intValue >= DEBUG_TAPS) { taps.intValue = 0; door.pass(onDebug) } }
                .navigationBarsPadding().heightIn(min = 48.dp).padding(12.dp),
            style = MaterialTheme.typography.bodySmall, color = Palette.Dim, textAlign = TextAlign.Center,
        )
    }
    BottomSheet(picking == Pick.CARE, stringResource(R.string.set_care), { picking = null }) {
        OptionList(
            stringResource(R.string.set_care), careNames,
            careHelps,
            careIx, emptySet(),
        ) { door.pass { container.pickSensitivity(Sensitivity.entries[it]) } }
    }
    BottomSheet(picking == Pick.HOW, stringResource(R.string.set_how), { picking = null }) {
        OptionList(
            stringResource(R.string.set_how), presetNames,
            presetHelps,
            presetIx, if (canShoot) emptySet() else setOf(presets.indexOf(ParentPreset.PRECISE)),
            reason = android14, preview = { TileVisual(VisualKind.PRESET, height = 96.dp, preset = settings.preset) },
        ) { door.pass { container.settings.update { s -> s.copy(preset = presets[it]) } } }
    }
}

/** Cuidado: Tapar más / Normal / Tapar menos with its helper line. Ajustes and the wizard's step 6. */
@Composable
fun CareControl(selected: Sensitivity, onPick: (Sensitivity) -> Unit) =
    SegmentedControl(
        listOf(R.string.care_strict, R.string.care_normal, R.string.care_relaxed).map { stringResource(it) },
        Sensitivity.entries.indexOf(selected),
        { onPick(Sensitivity.entries[it]) },
        help = listOf(R.string.care_help_strict, R.string.care_help_normal, R.string.care_help_relaxed).map { stringResource(it) },
    )

/** The current choice at the end of a row that opens its sheet. */
@Composable
private fun Value(text: String) =
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        SIcon(R.drawable.ic_chev)
    }

/** One choice per row, a check on the picked one. A [disabled] row dims and shows [reason] in place of its help. [preview] sits above the list. */
@Composable
fun ColumnScope.OptionList(
    title: String, names: List<String>, helps: List<String>, selected: Int, disabled: Set<Int>, recommended: Int = 1,
    reason: String? = null, preview: (@Composable () -> Unit)? = null, onPick: (Int) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    preview?.invoke()
    PreferenceGroup(Modifier.fillMaxWidth().selectableGroup()) {
        names.forEachIndexed { i, name ->
            val off = i in disabled
            item {
                PrefRow(
                    name,
                    Modifier.alpha(if (off) Palette.DISABLED else 1f).selectable(i == selected, enabled = !off, role = Role.RadioButton) { onPick(i) },
                    description = if (off && reason != null) reason else helps[i],
                    end = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (i == recommended) Tag(stringResource(R.string.group_recommended), TagKind.Recommended)
                            SIcon(R.drawable.ic_check, Palette.GreenFg, modifier = Modifier.alpha(if (i == selected) 1f else 0f))
                        }
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp)) // the list sat close to the gesture bar
}
