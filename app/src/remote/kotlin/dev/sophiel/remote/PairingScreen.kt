package dev.sophiel.remote

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.sophiel.BuildConfig
import dev.sophiel.R
import dev.sophiel.ui.BtnStyle
import dev.sophiel.ui.ConfirmDialog
import dev.sophiel.ui.Door
import dev.sophiel.ui.HeaderBar
import dev.sophiel.ui.PrefRow
import dev.sophiel.ui.PreferenceGroup
import dev.sophiel.ui.SButton
import dev.sophiel.ui.SIcon
import dev.sophiel.ui.Tag
import dev.sophiel.ui.TagKind
import dev.sophiel.ui.theme.Palette

private tailrec fun Context.activity(): ComponentActivity? = this as? ComponentActivity ?: (this as? ContextWrapper)?.baseContext?.activity()

/** Whether the app is exempt from battery optimisation, re-read every time the app comes back to the front. */
@Composable
internal fun rememberBatteryExempt(): State<Boolean> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(RemoteState.isBatteryExempt(ctx)) }
    val owner = ctx.activity()
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) state.value = RemoteState.isBatteryExempt(ctx) }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    return state
}

private fun askBatteryExemption(ctx: Context) =
    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))

/** The Ajustes row "Teléfono del Padre", behind the door (D82). The pairing screen shows over Ajustes and relocks with the door. */
@Composable
fun PairingRow(door: Door) {
    var open by rememberSaveable { mutableStateOf(false) }
    val paired by RemoteState.paired.collectAsState()
    androidx.compose.runtime.LaunchedEffect(door.unlocked) { if (!door.unlocked) open = false }
    PreferenceGroup {
        item {
            PrefRow(
                stringResource(R.string.pair_row), icon = R.drawable.ic_phone, onClick = { door.pass { open = true } },
                description = if (paired) stringResource(R.string.pair_status) else null,
                end = { SIcon(R.drawable.ic_chev) },
            )
        }
    }
    if (open) PairingScreen(onClose = { open = false })
}

/**
 * Pairing (D82, D83). Order: battery exemption if missing (skippable), then the privacy line and the QR. The claim socket
 * is collected only while the QR step shows, so leaving the screen (back, relock, rotation away) closes it.
 */
@Composable
private fun PairingScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val exempt by rememberBatteryExempt()
    var skipped by rememberSaveable { mutableStateOf(false) }
    val paired by RemoteState.paired.collectAsState()
    var again by rememberSaveable { mutableStateOf(false) } // "Mostrar código otra vez" while already paired
    val claiming = (exempt || skipped) && (!paired || again)
    var confirmUnpair by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Palette.Chalk)) {
            HeaderBar(
                stringResource(R.string.pair_title),
                start = { SButton(null, onClose, icon = R.drawable.ic_back, label = stringResource(R.string.back)) },
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    !exempt && !skipped -> {
                        Text(stringResource(R.string.pair_battery_title), style = MaterialTheme.typography.titleLarge, color = Palette.Moss)
                        Text(stringResource(R.string.pair_battery_text), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
                        SButton(stringResource(R.string.pair_battery_allow), { askBatteryExemption(ctx) }, Modifier.fillMaxWidth(), BtnStyle.Go)
                        SButton(stringResource(R.string.pair_battery_skip), { skipped = true }, Modifier.fillMaxWidth())
                    }
                    claiming -> Claim(onPaired = { again = false })
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SIcon(R.drawable.ic_shield_ok, Palette.GreenFg)
                            Text(stringResource(R.string.pair_status), style = MaterialTheme.typography.titleMedium, color = Palette.GreenFg)
                        }
                        SButton(stringResource(R.string.pair_again), { again = true }, Modifier.fillMaxWidth())
                        SButton(stringResource(R.string.pair_unpair), { confirmUnpair = true }, Modifier.fillMaxWidth(), BtnStyle.Destructive)
                    }
                }
            }
        }
        if (confirmUnpair) ConfirmDialog(
            title = stringResource(R.string.pair_unpair_title), text = stringResource(R.string.pair_unpair_text),
            confirmLabel = stringResource(R.string.pair_unpair), cancelLabel = stringResource(R.string.pin_cancel),
            onConfirm = { confirmUnpair = false; RemoteState.unpairFromHere(); onClose() },
            onDismiss = { confirmUnpair = false },
        )
    }
}

/** The privacy line, the QR, the connection line and "Mostrar código otra vez". Owns the socket for as long as it is composed. */
@Composable
private fun Claim(onPaired: () -> Unit) {
    val store = RemoteState.child
    val socket = remember { ClaimSocket(ChildFrame.wsUrl(BuildConfig.RELAY_URL)) }
    var code by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(false) }
    LaunchedEffect(socket) {
        socket.events().collect { e ->
            when (e) {
                ClaimEvent.Open -> connected = true
                ClaimEvent.Closed -> { connected = false; code = null }
                is ClaimEvent.Frame -> when (val f = e.frame) {
                    is ChildFrame.Code -> code = f.claimCode
                    is ChildFrame.Paired -> { RemoteState.onPaired(f.pairingId, f.deviceToken); onPaired() }
                    ChildFrame.Unpaired -> Unit
                }
            }
        }
    }
    Text(stringResource(R.string.pair_privacy), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
    val text = code?.let { PairingQr.encode(it, store.key()) }
    Column(Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
        if (text != null) QrCode(text, Modifier.fillMaxSize())
    }
    Text(
        stringResource(if (connected) R.string.pair_connected else R.string.pair_disconnected),
        style = MaterialTheme.typography.titleMedium, color = if (connected) Palette.GreenFg else Palette.Dim,
    )
    SButton(stringResource(R.string.pair_again), { code = null; socket.newCode() }, Modifier.fillMaxWidth(), enabled = connected)
}

/** ZXing's matrix on a Canvas (D80): one square per dark module, on white. */
@Composable
private fun QrCode(text: String, modifier: Modifier) {
    val matrix = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0)) }
    Canvas(modifier) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + .5f, cell + .5f))
        }
    }
}

/** Status line while paired (D82). */
@Composable
fun PairedLine() {
    val paired by RemoteState.paired.collectAsState()
    if (!paired) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SIcon(R.drawable.ic_phone, Palette.Dim, 14.dp)
        Text(stringResource(R.string.pair_status), style = MaterialTheme.typography.bodySmall, color = Palette.Dim)
    }
}

/** Recomendado "Sin restricción de batería" while paired and not exempt (D82). */
@Composable
fun BatteryRecommended() {
    val paired by RemoteState.paired.collectAsState()
    val exempt by rememberBatteryExempt()
    val ctx = LocalContext.current
    if (!paired || exempt) return
    PreferenceGroup(title = stringResource(R.string.group_recommended)) {
        item {
            PrefRow(
                stringResource(R.string.pair_battery_need), description = stringResource(R.string.pair_battery_need_text),
                icon = R.drawable.ic_power, iconTint = Palette.Orange,
                end = { SButton(stringResource(R.string.pair_battery_allow), { askBatteryExemption(ctx) }, style = BtnStyle.Fix) },
            )
        }
    }
}
