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
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

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
    Failure.GONE -> R.string.par_card_gone_text
    Failure.BAD_CODE -> R.string.par_scan_bad_text
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
            Link(stringResource(R.string.par_back_to_child)) { dev.sophiel.remote.RemoteState.chooseRole(dev.sophiel.remote.Role.Hijo) }
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
    var adding by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val session = ParentGraph.session
    val children by session.children.list.collectAsState()
    Box(Modifier.fillMaxSize().background(Palette.Chalk)) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(stringResource(R.string.par_home), end = {
                Row {
                    SButton(null, { adding = true }, icon = R.drawable.ic_plus, label = stringResource(R.string.par_add))
                    SButton(null, { account = true }, icon = R.drawable.ic_gear, label = stringResource(R.string.par_account))
                }
            })
            if (children.isEmpty()) Column(Modifier.weight(1f).navigationBarsPadding().cutoutPadding().pageWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
                // It says where the Child phone's QR is.
                StatusPage(R.drawable.ic_phone, stringResource(R.string.par_empty_title), stringResource(R.string.par_empty_text), Tone.Off)
                SButton(stringResource(R.string.par_scan_do), { adding = true }, Modifier.fillMaxWidth().padding(top = 10.dp), style = BtnStyle.Go)
            } else Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().cutoutPadding().pageWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val home = ParentGraph.home
                val cards by home.cards.collectAsState()
                // All asked on open, and again when a phone is added or removed (D81).
                LaunchedEffect(children.map { it.id }) { home.open() }
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
                children.forEach { c -> ChildCard(c.name, cards[c.id] ?: Card.Loading, now, c.id, home) }
            }
        }
        AddSheet(adding) { adding = false }
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

/** "+": the disclosure line, Google's scanner, POST /pairings, then the name (D80, D81). A bad scan names the fix on the Child phone. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.AddSheet(visible: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val session = ParentGraph.session
    val scope = rememberCoroutineScope()
    val defaultName = stringResource(R.string.par_name_default)
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var pairedId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    fun close() { busy = false; error = null; pairedId = null; name = ""; onDone() }
    fun scan() {
        if (busy) return
        busy = true; error = null
        GmsBarcodeScanning.getClient(ctx, GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
            .startScan()
            .addOnSuccessListener { code ->
                scope.launch {
                    when (val r = session.scan(code.rawValue.orEmpty(), defaultName)) {
                        is Scan.Ok -> pairedId = r.pairingId
                        is Scan.Fail -> error = r.why.text()
                    }
                    busy = false
                }
            }
            .addOnCanceledListener { busy = false }
            .addOnFailureListener { busy = false; error = R.string.par_scan_unavailable } // e.g. module not installed yet and no network
    }
    val id = pairedId
    BottomSheet(visible, stringResource(if (id == null) R.string.par_scan_title else R.string.par_name_title), { close() }) {
        if (id == null) {
            Text(stringResource(R.string.par_scan_text), style = MaterialTheme.typography.bodyMedium, color = Palette.Fg)
            error?.let {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.tint(Palette.RedFg, .12f)).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SIcon(R.drawable.ic_warn, Palette.RedFg, 20.dp)
                    Column {
                        if (it == R.string.par_scan_bad_text) Text(stringResource(R.string.par_scan_bad_title), style = MaterialTheme.typography.titleSmall, color = Palette.RedFg)
                        Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = Palette.Fg)
                    }
                }
            }
            SButton(stringResource(R.string.par_scan_do), ::scan, Modifier.fillMaxWidth(), style = BtnStyle.Go, busy = busy)
            Text(stringResource(R.string.par_scan_disclosure), style = MaterialTheme.typography.bodySmall, color = Palette.Dim)
        } else {
            Text(stringResource(R.string.par_name_text), style = MaterialTheme.typography.bodyMedium, color = Palette.Fg)
            Field(name, { name = it }, stringResource(R.string.par_name_hint), KeyboardType.Text, VisualTransformation.None)
            SButton(
                stringResource(R.string.par_name_do), { session.children.rename(id, name); close() }, Modifier.fillMaxWidth(),
                style = BtnStyle.Go, enabled = name.isNotBlank(),
            )
        }
    }
}

/** "hace 12 s" under a minute, then the app's usual relative time. */
@Composable
private fun ago(at: Long, now: Long): String {
    val locale = dev.sophiel.ui.appLocale()
    val s = ((now - at) / 1000).coerceAtLeast(0)
    if (s >= 60) return dev.sophiel.ui.relativeTime(at, now, locale)
    val f = android.icu.text.RelativeDateTimeFormatter.getInstance(android.icu.util.ULocale.forLocale(locale))
    return f.format(s.toDouble(), android.icu.text.RelativeDateTimeFormatter.Direction.LAST, android.icu.text.RelativeDateTimeFormatter.RelativeUnit.SECONDS)
}

/** One Child phone's state card (D81), in the Status card's dark look (D75); its banner sits under it. */
@Composable
private fun ChildCard(name: String, card: Card, now: Long, id: String, home: HomeCards) {
    val scope = rememberCoroutineScope()
    val live = card as? Card.Live
    val bg = when {
        live == null -> Palette.HeroOff
        live.protecting -> Palette.Moss
        else -> Palette.HeroAmber
    }
    Column {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bg).padding(18.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = Palette.Text1)
            val (title, line) = when (card) {
                Card.Loading -> null to null
                Card.Offline -> stringResource(R.string.par_card_offline) to stringResource(R.string.par_card_offline_text)
                Card.Gone -> stringResource(R.string.par_card_gone) to stringResource(R.string.par_card_gone_text)
                is Card.Live -> stringResource(if (card.protecting) R.string.par_card_on else R.string.par_card_off) to
                    (if (card.accessibilityOff) stringResource(R.string.par_card_acc_off) + " · " else "") + ago(card.at, now)
            }
            if (title == null) {
                // Skeleton while the log request is out.
                Box(Modifier.padding(top = 10.dp).fillMaxWidth(.6f).height(26.dp).clip(RoundedCornerShape(8.dp)).background(Palette.DarkFill))
                Box(Modifier.padding(top = 8.dp).fillMaxWidth(.8f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(Palette.DarkFill))
            } else {
                Text(title, Modifier.padding(top = 4.dp).asHeading(), style = MaterialTheme.typography.headlineSmall, color = Palette.Text0)
                Text(
                    line.orEmpty(), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    color = if (live?.accessibilityOff == true) Palette.AmberInk else Palette.Text1,
                )
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (card) {
                    Card.Loading -> {}
                    Card.Offline -> Pill(stringResource(R.string.par_card_retry)) { scope.launch { home.refresh(id) } }
                    Card.Gone -> Pill(stringResource(R.string.par_card_remove)) { home.remove(id) }
                    is Card.Live ->
                        if (card.protecting) Pill(stringResource(R.string.par_stop), busy = card.busy) { scope.launch { home.stop(id) } }
                        else Pill(stringResource(R.string.par_start), busy = card.busy, enabled = card.canStart || card.busy, go = true) { scope.launch { home.start(id) } }
                }
                if (card != Card.Gone) UnpairButton(name, id, home) // ticket 21; moves to the per-phone screen if that lands
            }
        }
        live?.banner?.let { Banner(it) }
    }
}

@Composable
private fun Pill(text: String, busy: Boolean = false, enabled: Boolean = true, go: Boolean = false, onClick: () -> Unit) =
    SButton(
        text, onClick, style = BtnStyle.OnDark, busy = busy, enabled = enabled,
        fill = if (go && enabled && !busy) Palette.Mint else Color.Unspecified, tint = if (go && enabled && !busy) Palette.Moss else Color.Unspecified,
    )

@Composable
private fun Banner(o: Outcome) {
    val good = o == Outcome.STARTED || o == Outcome.ALREADY_ON || o == Outcome.STOPPED
    val tone = if (good) Palette.GreenFg else Palette.RedFg
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(Palette.tint(tone, .12f)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(if (good) R.drawable.ic_shield_ok else R.drawable.ic_warn, tone, 20.dp)
        Text(
            stringResource(
                when (o) {
                    Outcome.STARTED -> R.string.par_out_started
                    Outcome.ALREADY_ON -> R.string.par_out_already
                    Outcome.STOPPED -> R.string.par_out_stopped
                    Outcome.ACCESSIBILITY_OFF -> R.string.par_out_acc_off
                    Outcome.SWITCH_OFF -> R.string.par_out_switch_off
                    Outcome.FAILED -> R.string.par_out_failed
                },
            ),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium, color = Palette.Fg,
        )
    }
}
