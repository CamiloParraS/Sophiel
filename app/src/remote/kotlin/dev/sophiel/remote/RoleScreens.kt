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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sophiel.R
import dev.sophiel.remote.parent.ParentRoot
import dev.sophiel.ui.BtnStyle
import dev.sophiel.ui.InkCard
import dev.sophiel.ui.asHeading
import dev.sophiel.ui.SButton
import dev.sophiel.ui.theme.Palette

/** First launch in `remote` (D82): Hijo is preselected and runs today's wizard; Padre goes to [ParentRoot]. */
@Composable
fun RoleChoice() {
    var pick by rememberSaveable { mutableStateOf(Role.Hijo) }
    Column(Modifier.fillMaxSize().background(Palette.Chalk).systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.role_title), Modifier.padding(start = 4.dp, top = 8.dp), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 33.sp, letterSpacing = (-1).sp), color = Palette.Moss)
            // D75: the chosen role is the state card, with a check badge; the other waits as a white tile.
            listOf(Role.Hijo to (R.string.role_child to R.string.role_child_text), Role.Padre to (R.string.role_parent to R.string.role_parent_text)).forEach { (r, t) ->
                val on = pick == r
                val pickIt = Modifier.selectable(on, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { pick = r })
                if (on) InkCard(stringResource(t.first), pickIt, text = stringResource(t.second), icon = R.drawable.ic_check, lit = 3, titleSize = 34.sp)
                else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.Surface).then(pickIt).padding(20.dp)) {
                    Text(stringResource(t.first), Modifier.asHeading(), style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 37.sp), color = Palette.Moss)
                    Text(stringResource(t.second), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge, color = Palette.Dim)
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
