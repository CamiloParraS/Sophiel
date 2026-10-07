package dev.sophiel.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sophiel.AppContainer
import dev.sophiel.R
import dev.sophiel.core.Sensitivity
import dev.sophiel.settings.ParentPreset
import dev.sophiel.ui.theme.Palette

private const val DEBUG_TAPS = 7

/**
 * Ajustes (D38, D40, D44), behind the door. Every change goes through [Door.pass], which extends the unlock.
 * Seven taps on the version label open the debug menu, in every build (D38).
 */
@Composable
fun SettingsScreen(container: AppContainer, door: Door, onBack: () -> Unit, onHistory: () -> Unit, onDebug: () -> Unit) {
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
    val taps = remember { mutableIntStateOf(0) }
    val presets = ParentPreset.entries
    val android14 = stringResource(R.string.needs_android14)
    Column(Modifier.fillMaxSize()) {
        HeaderBar(stringResource(R.string.settings_title), start = { FlatIconButton(R.drawable.ic_back, stringResource(R.string.back), onBack) })
        UnlockedBanner(door)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PreferenceGroup(title = stringResource(R.string.group_protection)) {
                item {
                    SettingRow(stringResource(R.string.set_care)) {
                        CareControl(settings.sensitivity) { door.pass { container.pickSensitivity(it) } }
                    }
                }
                item {
                    SettingRow(stringResource(R.string.set_how)) {
                        SegmentedControl(
                            listOf(R.string.preset_light, R.string.preset_balanced, R.string.preset_thorough, R.string.preset_precise).map { stringResource(it) },
                            presets.indexOf(settings.preset),
                            { door.pass { container.settings.update { s -> s.copy(preset = presets[it]) } } },
                            help = listOf(R.string.how_help_light, R.string.how_help_balanced, R.string.how_help_thorough, R.string.how_help_precise).map { stringResource(it) },
                            disabled = if (canShoot) emptySet() else setOf(presets.indexOf(ParentPreset.PRECISE)),
                        )
                        if (!canShoot) Text(android14, style = MaterialTheme.typography.bodySmall, color = Palette.Dim)
                    }
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
                        description = stringResource(R.string.set_peek_text) + if (canShoot) "" else "\n$android14",
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
                item {
                    PrefRow(
                        stringResource(R.string.set_history), description = stringResource(R.string.set_history_text), icon = R.drawable.ic_list,
                        onClick = { door.pass(onHistory) }, end = { SIcon(R.drawable.ic_chev) },
                    )
                }
            }
            PreferenceGroup(title = stringResource(R.string.group_privacy)) {
                item {
                    PrefRow(
                        stringResource(R.string.set_no_internet), description = stringResource(R.string.set_no_internet_text),
                        icon = R.drawable.ic_shield_ok, iconTint = Palette.GreenFg,
                    )
                }
            }
        }
        Text(
            stringResource(R.string.version_label, version),
            Modifier.fillMaxWidth().clickable { if (++taps.intValue >= DEBUG_TAPS) { taps.intValue = 0; door.pass(onDebug) } }
                .navigationBarsPadding().padding(12.dp),
            style = MaterialTheme.typography.bodySmall, color = Palette.Dim, textAlign = TextAlign.Center,
        )
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

/** A title over a control, filling one row of a group. */
@Composable
private fun SettingRow(title: String, content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
