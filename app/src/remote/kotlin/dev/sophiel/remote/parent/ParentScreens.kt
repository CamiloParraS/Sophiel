package dev.sophiel.remote.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sophiel.R
import dev.sophiel.ui.BtnStyle
import dev.sophiel.ui.ConfirmDialog
import dev.sophiel.ui.BottomSheet
import dev.sophiel.ui.HeaderBar
import dev.sophiel.ui.PrefRow
import dev.sophiel.ui.PreferenceGroup
import dev.sophiel.ui.SButton
import dev.sophiel.ui.SIcon
import dev.sophiel.ui.StatusPage
import dev.sophiel.ui.Tone
import dev.sophiel.ui.asHeading
import dev.sophiel.ui.cutoutPadding
import dev.sophiel.ui.pageWidth
import dev.sophiel.ui.theme.Palette
import kotlinx.coroutines.launch

/** The Parent phone's whole app: login while there is no token, the home once there is (D81). A 401 anywhere clears the token and lands here. */
@Composable
fun ParentRoot() {
    val token by ParentGraph.session.token.collectAsState()
    if (token == null) AuthScreen() else ParentHome()
}

private fun Failure.text() = when (this) {
    Failure.WRONG_CREDENTIALS -> R.string.par_err_wrong
    Failure.LOCKED -> R.string.par_err_locked
    Failure.EMAIL_TAKEN -> R.string.par_err_taken
    Failure.INVALID -> R.string.par_err_invalid
    Failure.OFFLINE -> R.string.par_err_offline
    Failure.SESSION, Failure.OTHER -> R.string.par_err_other
}

@Composable
private fun AuthScreen() {
    var signup by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Failure?>(null) }
    val scope = rememberCoroutineScope()
    val session = ParentGraph.session
    BackHandler(signup) { signup = false; error = null }
    fun submit() {
        if (busy) return
        busy = true; error = null
        scope.launch {
            error = if (signup) session.signup(email, password) else session.login(email, password)
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().background(Palette.Chalk).navigationBarsPadding().imePadding()) {
        if (signup) HeaderBar(stringResource(R.string.par_signup_bar), start = {
            SButton(null, { signup = false; error = null }, icon = R.drawable.ic_back, label = stringResource(R.string.back))
        })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).then(if (signup) Modifier else Modifier.statusBarsPadding())
                .cutoutPadding().pageWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            val failed = error
            // The state card: ink, a badge, a headline and one line; it turns to the error when a call fails.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.Moss).padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(if (failed != null) Palette.TileRed else Palette.TileOn), contentAlignment = Alignment.Center) {
                    SIcon(if (failed != null) R.drawable.ic_warn else R.drawable.ic_key, if (failed != null) Palette.RedInk else Palette.Mint, 26.dp)
                }
                Text(
                    stringResource(if (failed != null) R.string.par_err_title else if (signup) R.string.par_signup_title else R.string.par_login_title),
                    Modifier.padding(top = 14.dp).asHeading(), style = MaterialTheme.typography.headlineSmall, color = Palette.Text0, textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(failed?.text() ?: if (signup) R.string.par_signup_text else R.string.par_login_text),
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Text1, textAlign = TextAlign.Center,
                )
                if (failed == null && !signup) Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SIcon(R.drawable.ic_shield_ok, Palette.Mint, 16.dp)
                    Text(stringResource(R.string.par_privacy), style = MaterialTheme.typography.bodySmall, color = Palette.Text1)
                }
            }
            Field(email, { email = it; error = null }, stringResource(R.string.par_email), KeyboardType.Email, VisualTransformation.None)
            Field(password, { password = it; error = null }, stringResource(R.string.par_password), KeyboardType.Password, PasswordVisualTransformation())
            if (signup) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.tint(Palette.Orange, .15f)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SIcon(R.drawable.ic_warn, Palette.OrangeFg, 20.dp)
                Column {
                    Text(stringResource(R.string.par_norecovery_title), style = MaterialTheme.typography.titleSmall, color = Palette.OrangeFg)
                    Text(stringResource(R.string.par_norecovery_text), style = MaterialTheme.typography.bodyMedium, color = Palette.Fg)
                }
            }
            SButton(
                stringResource(if (signup) R.string.par_signup_do else R.string.par_login_do), ::submit, Modifier.fillMaxWidth(),
                style = BtnStyle.Go, busy = busy, enabled = email.isNotBlank() && password.isNotEmpty(),
            )
            if (!signup) Link(stringResource(R.string.par_make_account)) { signup = true; error = null }
        }
        // Back to the Child role: only here, only while logged out (D81).
        if (!signup) Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
            Link(stringResource(R.string.par_back_to_child)) { ParentGraph.roles.set(ParentRole.CHILD) }
        }
    }
}

@Composable
private fun Link(text: String, onClick: () -> Unit) =
    Text(
        text, Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 48.dp).padding(top = 13.dp),
        style = MaterialTheme.typography.labelLarge, color = Palette.GreenFg, textAlign = TextAlign.Center,
    )

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType, transform: VisualTransformation) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Card)) {
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 15.dp),
            singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Fg), cursorBrush = SolidColor(Palette.Fg),
            keyboardOptions = KeyboardOptions(keyboardType = type), visualTransformation = transform,
            decorationBox = { field ->
                if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = Palette.Dim)
                field()
            },
        )
    }
}

@Composable
private fun ParentHome() {
    var account by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Failure?>(null) }
    val scope = rememberCoroutineScope()
    val session = ParentGraph.session
    Box(Modifier.fillMaxSize().background(Palette.Chalk)) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(stringResource(R.string.par_home), end = {
                SButton(null, { account = true }, icon = R.drawable.ic_gear, label = stringResource(R.string.par_account))
            })
            Column(Modifier.weight(1f).navigationBarsPadding().cutoutPadding().pageWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
                // Empty until pairing (tickets 17, 19): it says where the Child phone's QR is.
                StatusPage(R.drawable.ic_phone, stringResource(R.string.par_empty_title), stringResource(R.string.par_empty_text), Tone.Off)
            }
        }
        BottomSheet(account, stringResource(R.string.par_account), { account = false }) {
            PreferenceGroup {
                item { PrefRow(stringResource(R.string.par_logout), icon = R.drawable.ic_unlock, onClick = { account = false; scope.launch { session.logout() } }) }
                item {
                    PrefRow(
                        stringResource(R.string.par_delete), description = stringResource(R.string.par_delete_help), icon = R.drawable.ic_trash, iconTint = Palette.RedFg,
                        onClick = { failure = null; confirmDelete = true },
                    )
                }
            }
            failure?.let { Text(stringResource(it.text()), style = MaterialTheme.typography.bodyMedium, color = Palette.RedFg) }
            Text(stringResource(R.string.par_account_hint), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
        }
        if (confirmDelete) ConfirmDialog(
            title = stringResource(R.string.par_delete_title), text = stringResource(R.string.par_delete_text),
            confirmLabel = stringResource(R.string.par_delete), cancelLabel = stringResource(R.string.pin_cancel),
            onConfirm = {
                confirmDelete = false
                if (!deleting) { deleting = true; scope.launch { failure = session.deleteAccount(); deleting = false } }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
