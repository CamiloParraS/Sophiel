package dev.sophiel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sophiel.AppContainer
import dev.sophiel.R
import dev.sophiel.capture.isDebuggable
import dev.sophiel.core.Sensitivity
import dev.sophiel.feed.SpikeModel
import dev.sophiel.settings.ParentPreset

/** The debug menu's sub-screens (D38); null in the activity means the menu itself. */
enum class DebugPage(val title: Int) {
    TestFeed(R.string.debug_test_feed), Benchmark(R.string.debug_benchmark), Masks(R.string.debug_masks),
}

/**
 * Debug menu (D38), every build: what used to be the bottom bar and Status's debug chips. Spike and
 * experiment switches (D24, D34, D35) and the raw cutoff (D40) apply between frames, no restart.
 */
@Composable
fun DebugMenu(container: AppContainer, door: Door, onBack: () -> Unit, open: (DebugPage) -> Unit, rerunWizard: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HeaderBar(stringResource(R.string.debug_title), start = { FlatIconButton(R.drawable.ic_back, stringResource(R.string.back), onBack) })
        UnlockedBanner(door)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PreferenceGroup {
                DebugPage.entries.forEach { page ->
                    item { PrefRow(stringResource(page.title), onClick = { door.pass { open(page) } }, end = { SIcon(R.drawable.ic_chev) }) }
                }
            }
            if (LocalContext.current.isDebuggable) {
                PreferenceGroup {
                    item {
                        var on by remember { mutableStateOf(container.debugPill) }
                        SwitchRow(
                            stringResource(R.string.debug_pill), on,
                            { v -> door.pass { on = v; container.debugPill = v } },
                            description = stringResource(R.string.debug_pill_text),
                        )
                    }
                    // D49: from step 1; the PIN made there replaces the current one.
                    item { PrefRow(stringResource(R.string.debug_rerun_wizard), description = stringResource(R.string.debug_rerun_wizard_text), onClick = { door.pass(rerunWizard) }, end = { SIcon(R.drawable.ic_chev) }) }
                }
            }
            Switches(container)
        }
    }
}

@Composable
private fun Switches(container: AppContainer) {
    val settings by container.settings.state.collectAsState()
    var liveModel by remember { mutableStateOf(container.liveModel) }
    var override by remember { mutableStateOf(container.thresholdOverride) }
    val precise = settings.preset == ParentPreset.PRECISE
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Spike (D24): switches the live model between frames. Precise picks its own.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpikeModel.entries.forEach { m ->
                FilterChip(
                    selected = m == liveModel,
                    onClick = { liveModel = m; container.liveModel = m },
                    enabled = !precise,
                    label = { Text(m.label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sensitivity.entries.forEach { s ->
                FilterChip(
                    selected = override == null && s == settings.sensitivity,
                    onClick = { override = null; container.pickSensitivity(s) },
                    label = { Text(s.name) },
                )
            }
        }
        // D40: raw cutoff, in memory only; picking a sensitivity clears it.
        Text("Threshold %.2f%s".format(container.threshold(), if (override != null) " (raw)" else ""), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = container.threshold(),
            onValueChange = { override = it; container.thresholdOverride = it },
            modifier = Modifier.fillMaxWidth(0.8f),
        )
    }
}
