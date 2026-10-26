package dev.sophiel.remote.parent

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import dev.sophiel.ui.theme.Ease
import dev.sophiel.ui.theme.motion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.sophiel.R
import dev.sophiel.remote.RemoteState
import dev.sophiel.remote.Role.Hijo
import dev.sophiel.ui.appLocale
import dev.sophiel.ui.relativeTime
import dev.sophiel.ui.BtnStyle
import dev.sophiel.ui.ConfirmDialog
import dev.sophiel.ui.BottomSheet
import dev.sophiel.ui.HeaderBar
import dev.sophiel.ui.InkCard
import dev.sophiel.ui.InkTone
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
            // The state card (D75): the tile wall, a badge, a headline and one line; it turns red when a call fails.
            InkCard(
                stringResource(if (failed != null) R.string.par_err_title else if (signup) R.string.par_signup_title else R.string.par_login_title),
                text = stringResource(failed?.text() ?: if (signup) R.string.par_signup_text else R.string.par_login_text),
                tone = if (failed != null) InkTone.Red else InkTone.Ok, icon = if (failed != null) R.drawable.ic_warn else R.drawable.ic_key,
                square = failed != null, centered = true, titleSize = if (signup) 34.sp else 38.sp,
                footer = if (failed != null) null else ({ // D83: on login and on sign-up
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SIcon(R.drawable.ic_shield_ok, Palette.Mint, 16.dp)
                        Text(stringResource(R.string.par_privacy), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall, color = Palette.Text1, textAlign = TextAlign.Center)
                    }
                }),
            )
            Field(email, { email = it; error = null }, stringResource(R.string.par_email), KeyboardType.Email, VisualTransformation.None)
            Field(password, { password = it; error = null }, stringResource(R.string.par_password), KeyboardType.Password, PasswordVisualTransformation())
            if (signup) Note(
                R.drawable.ic_warn, Palette.OrangeFg, stringResource(R.string.par_norecovery_title), stringResource(R.string.par_norecovery_text),
                fill = Palette.tint(Palette.Orange, .15f),
            )
            SButton(
                stringResource(if (signup) R.string.par_signup_do else R.string.par_login_do), ::submit, Modifier.fillMaxWidth(),
                style = BtnStyle.Go, busy = busy, enabled = email.isNotBlank() && password.isNotEmpty(),
            )
            if (!signup) Link(stringResource(R.string.par_make_account)) { signup = true; error = null }
        }
        // Back to the Child role: only here, only while logged out (D81).
        if (!signup) Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
            Link(stringResource(R.string.par_back_to_child)) { RemoteState.chooseRole(Hijo) }
        }
    }
}

@Composable
private fun Link(text: String, onClick: () -> Unit) =
    Text(
        text, Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 48.dp).padding(top = 13.dp),
        style = MaterialTheme.typography.labelLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = Palette.Moss, textAlign = TextAlign.Center,
    )

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType, transform: VisualTransformation) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Card).then(if (value.isNotEmpty()) Modifier.border(2.dp, Palette.Moss, RoundedCornerShape(18.dp)) else Modifier)) {
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
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    children.firstOrNull { it.id == open }?.let { c ->
        Box(Modifier.fillMaxSize()) { PhoneScreen(c) { open = null } }
        return
    }
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
                InkCard(stringResource(R.string.par_empty_title), text = stringResource(R.string.par_empty_text), icon = R.drawable.ic_phone, lit = 3)
                SButton(stringResource(R.string.par_scan_do), { adding = true }, Modifier.fillMaxWidth().padding(top = 10.dp), style = BtnStyle.Go)
            } else Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().cutoutPadding().pageWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val home = ParentGraph.home
                val cards by home.cards.collectAsState()
                // All asked on open, and again when a phone is added or removed (D81).
                LaunchedEffect(children.map { it.id }) { home.open() }
                val now = rememberNow()
                children.forEach { c -> ChildCard(c.name, cards[c.id] ?: Card.Loading, now, c.id, home) { open = c.id } }
            }
        }
        AddSheet(adding) { adding = false }
        BottomSheet(account, stringResource(R.string.par_account), { account = false }) {
            InkCard(
                stringResource(R.string.role_parent), text = pluralStringResource(R.plurals.par_account_linked, children.size, children.size),
                icon = R.drawable.ic_key, titleSize = 28.sp,
            )
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
private fun BoxScope.AddSheet(visible: Boolean, onDone: () -> Unit) {
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
                Note(R.drawable.ic_warn, Palette.RedFg, if (it == R.string.par_scan_bad_text) stringResource(R.string.par_scan_bad_title) else null, stringResource(it))
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
internal fun ago(at: Long, now: Long): String {
    val locale = appLocale()
    val s = ((now - at) / 1000).coerceAtLeast(0)
    if (s >= 60) return relativeTime(at, now, locale)
    val f = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))
    return f.format(s.toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.SECONDS)
}

/** The wall clock, ticking every second, for "hace 12 s". */
@Composable
internal fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    return now
}

/** A tinted note: icon, optional title in [tone], one line of text. Announced when it appears. */
@Composable
internal fun Note(icon: Int, tone: Color, title: String?, text: String, modifier: Modifier = Modifier, fill: Color = Palette.tint(tone, .12f)) = Row(
    modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(fill).padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite },
    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
) {
    SIcon(icon, tone, 20.dp)
    Column {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = tone)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Fg)
    }
}

/** One Child phone's state card (D81), in the Status card's dark look (D75); its banner sits under it. */
@Composable
private fun ChildCard(name: String, card: Card, now: Long, id: String, home: HomeCards, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    val live = card as? Card.Live
    val bg = when {
        live == null -> Palette.HeroOff
        live.protecting -> Palette.Moss
        else -> Palette.HeroAmber
    }
    val title = when (card) {
        Card.Loading -> null
        Card.Offline -> stringResource(R.string.par_card_offline)
        Card.Gone -> stringResource(R.string.par_card_gone)
        is Card.Live -> stringResource(if (card.protecting) R.string.par_card_on else R.string.par_card_off)
    }
    val line = when (card) {
        Card.Loading -> null
        Card.Offline -> stringResource(R.string.par_card_offline_text)
        Card.Gone -> stringResource(R.string.par_card_gone_text)
        is Card.Live -> (if (card.accessibilityOff) stringResource(R.string.par_card_acc_off) + " · " else "") + ago(card.at, now)
    }
    Column {
        // The card opens the phone's Historial and Apps (D81); Start / Stop / Reintentar stay their own targets.
        val open = Modifier.clickable(onClickLabel = stringResource(R.string.par_phone_open, name), role = Role.Button, onClick = onOpen)
        val head: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = if (live != null) Palette.Text1 else Palette.Dim)
                SIcon(R.drawable.ic_chev, if (live != null) Palette.Text1 else Palette.Dim, 16.dp)
            }
        }
        val actions: @Composable ColumnScope.() -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (card) {
                    Card.Loading -> {}
                    Card.Offline -> Pill(stringResource(R.string.par_card_retry), onDark = false) { scope.launch { home.refresh(id) } }
                    Card.Gone -> Pill(stringResource(R.string.par_card_remove), onDark = false) { home.remove(id) }
                    is Card.Live ->
                        if (card.protecting) Pill(stringResource(R.string.par_stop), busy = card.busy) { scope.launch { home.stop(id) } }
                        else Pill(stringResource(R.string.par_start), busy = card.busy, enabled = card.canStart || card.busy, go = true) { scope.launch { home.start(id) } }
                }
            }
        }
        if (live != null) InkCard(
            title.orEmpty(), open, text = line, tone = if (live.protecting) InkTone.Ok else InkTone.Amber, titleSize = 36.sp, header = head, footer = actions,
        ) else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.Surface).then(open).padding(20.dp)) {
            head()
            if (title == null) {
                // Skeleton while the log request is out.
                Box(Modifier.padding(top = 10.dp).fillMaxWidth(.6f).height(26.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Btn))
                Box(Modifier.padding(top = 8.dp).fillMaxWidth(.8f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Btn))
            } else {
                Text(title, Modifier.padding(top = 6.dp).asHeading(), style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 39.sp), color = Palette.Moss)
                Text(line.orEmpty(), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
            }
            Column(Modifier.padding(top = 12.dp), content = actions)
        }
        // The result drops in under its card and folds away when the next action starts.
        var last by remember { mutableStateOf<Outcome?>(null) }
        live?.banner?.let { last = it }
        AnimatedVisibility(live?.banner != null, enter = expandVertically(motion(tween(240, easing = Ease))) + fadeIn(motion(tween(200))), exit = shrinkVertically(motion(tween(160))) + fadeOut(motion(tween(120)))) {
            last?.let { Banner(it) }
        }
    }
}

@Composable
private fun Pill(text: String, busy: Boolean = false, enabled: Boolean = true, go: Boolean = false, onDark: Boolean = true, onClick: () -> Unit) =
    SButton(
        text, onClick, style = if (onDark) BtnStyle.OnDark else BtnStyle.Default, busy = busy, enabled = enabled,
        fill = if (go && enabled && !busy) Palette.Mint else Color.Unspecified, tint = if (go && enabled && !busy) Palette.Moss else Color.Unspecified,
    )

@Composable
private fun Banner(o: Outcome) {
    val good = o == Outcome.STARTED || o == Outcome.ALREADY_ON || o == Outcome.STOPPED
    val text = when (o) {
        Outcome.STARTED -> R.string.par_out_started
        Outcome.ALREADY_ON -> R.string.par_out_already
        Outcome.STOPPED -> R.string.par_out_stopped
        Outcome.ACCESSIBILITY_OFF -> R.string.par_out_acc_off
        Outcome.SWITCH_OFF -> R.string.par_out_switch_off
        Outcome.FAILED -> R.string.par_out_failed
    }
    Note(
        if (good) R.drawable.ic_shield_ok else R.drawable.ic_warn, if (good) Palette.GreenFg else Palette.RedFg, null, stringResource(text),
        Modifier.padding(top = 6.dp),
    )
}
