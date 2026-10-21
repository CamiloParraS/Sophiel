package dev.sophiel.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sophiel.R
import dev.sophiel.remote.parent.ParentRoot
import dev.sophiel.ui.BtnStyle
import dev.sophiel.ui.PrefRow
import dev.sophiel.ui.PreferenceGroup
import dev.sophiel.ui.SButton
import dev.sophiel.ui.SelectTick
import dev.sophiel.ui.theme.Palette

/** First launch in `remote` (D82): Hijo is preselected and runs today's wizard; Padre goes to [ParentRoot]. */
@Composable
fun RoleChoice() {
    var pick by rememberSaveable { mutableStateOf(Role.Hijo) }
    Column(Modifier.fillMaxSize().background(Palette.Chalk).systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.role_title), style = MaterialTheme.typography.headlineMedium, color = Palette.Moss)
            PreferenceGroup {
                listOf(Role.Hijo to (R.string.role_child to R.string.role_child_text), Role.Padre to (R.string.role_parent to R.string.role_parent_text)).forEach { (r, t) ->
                    item {
                        PrefRow(
                            stringResource(t.first), description = stringResource(t.second), onClick = { pick = r },
                            end = { SelectTick(pick == r, Palette.GreenFg) },
                        )
                    }
                }
            }
        }
        SButton(stringResource(R.string.role_continue), { RemoteState.chooseRole(pick) }, Modifier.fillMaxWidth().navigationBarsPadding(), BtnStyle.Go)
    }
}

/** Wraps today's app: asks the role once, routes Padre to [ParentRoot] (D81: no wizard, protection or door). */
@Composable
fun RoleGate(app: @Composable () -> Unit) {
    val role by RemoteState.role.collectAsState()
    when (role) {
        null -> RoleChoice()
        Role.Padre -> ParentRoot()
        Role.Hijo -> app()
    }
}
