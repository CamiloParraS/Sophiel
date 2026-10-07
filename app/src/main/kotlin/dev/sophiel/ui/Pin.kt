package dev.sophiel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.sophiel.R
import dev.sophiel.pin.CreateState
import dev.sophiel.pin.CreateStep
import dev.sophiel.pin.PinResult
import dev.sophiel.pin.PinStore
import dev.sophiel.pin.formatCountdown
import dev.sophiel.ui.theme.Ease
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.motion
import dev.sophiel.ui.theme.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_DIGITS = 6
private const val MIN_DIGITS = 4

/** Ticks [clock] every 250 ms while [active], for countdowns. */
@Composable
fun tickingNow(clock: () -> Long, active: Boolean = true): State<Long> {
    val now = remember { mutableLongStateOf(clock()) }
    LaunchedEffect(active) {
        while (active) {
            now.longValue = clock()
            delay(250)
        }
    }
    return now
}

/** Dots for the digits typed so far (4 to 6). Shakes whenever [shake] changes, unless motion is reduced. */
@Composable
private fun PinSlots(length: Int, shake: Int) {
    val reduced = reducedMotion()
    val x = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake > 0 && !reduced) x.animateTo(0f, keyframes { durationMillis = 320; -9f at 64; 8f at 144; -5f at 224; 0f at 320 })
    }
    Row(
        Modifier.height(14.dp).offset { IntOffset(x.value.dp.roundToPx(), 0) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(maxOf(MIN_DIGITS, length)) { i ->
            Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < length) Palette.Fg else Palette.Fg.copy(alpha = .18f)))
        }
    }
}

@Composable
private fun PinKeys(enabled: Boolean, onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    val deleteLabel = stringResource(R.string.pin_delete)
    Column(Modifier.alpha(if (enabled) 1f else .35f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("123", "456", "789", " 0<").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val keyModifier = Modifier.size(76.dp, 48.dp).clip(RoundedCornerShape(12.dp))
                    when (k) {
                        ' ' -> Box(keyModifier)
                        '<' -> Box(
                            keyModifier.background(Palette.Btn).clickable(enabled, role = Role.Button, onClick = onDelete).semantics { contentDescription = deleteLabel },
                            contentAlignment = Alignment.Center,
                        ) { SIcon(R.drawable.ic_del, Palette.Fg, 22.dp) }
                        else -> Box(keyModifier.background(Palette.Btn).clickable(enabled, role = Role.Button) { onDigit(k) }, contentAlignment = Alignment.Center) {
                            Text(k.toString(), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}

private enum class Attempt { Idle, Wrong, Right }

/**
 * The PIN sheet's content (D38, D44): slots, keys, Desbloquear. [onUnlocked] runs after a right PIN and a
 * short "Desbloqueado" message; the caller extends the unlock and closes the sheet. Digits live in plain
 * `remember`, never in saved state.
 */
@Composable
fun ColumnScope.PinPrompt(pin: PinStore, clock: () -> Long, onUnlocked: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var digits by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var shake by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableStateOf(Attempt.Idle) }
    var sawLock by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(false) }
    val lockout by pin.lockout.collectAsState()
    val now by tickingNow(clock)
    val remaining = lockout.remaining(now)
    if (remaining > 0) sawLock = true
    val locked = remaining > 0

    fun reject() {
        digits = ""
        shake++
        haptic.performHapticFeedback(HapticFeedbackType.Reject)
    }

    fun submit() {
        if (busy || locked || digits.length < MIN_DIGITS) return
        scope.launch {
            busy = true
            val result = pin.verify(digits)
            busy = false
            when (result) {
                PinResult.Ok -> {
                    attempt = Attempt.Right
                    delay(450)
                    onUnlocked()
                }
                PinResult.Wrong -> {
                    // The 5th wrong PIN locks: the countdown says it, so no stale "attempts left" afterwards.
                    attempt = if (pin.lockout.value.remaining(clock()) > 0) Attempt.Idle else Attempt.Wrong
                    reject()
                }
                is PinResult.Locked -> reject()
            }
        }
    }

    Text(stringResource(R.string.pin_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.pin_subtitle), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
    PinSlots(digits.length, shake)
    val (message, color) = when {
        locked -> stringResource(R.string.pin_locked, formatCountdown(remaining)) to Palette.RedFg
        attempt == Attempt.Right -> stringResource(R.string.pin_unlocked) to Palette.GreenFg
        attempt == Attempt.Wrong -> pluralStringResource(R.plurals.pin_wrong, lockout.attemptsLeft, lockout.attemptsLeft) to Palette.RedFg
        sawLock -> stringResource(R.string.pin_try_again) to Palette.Dim
        else -> "" to Palette.Dim
    }
    Text(message, Modifier.heightIn(min = 20.dp), style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.Center)
    val keysOn = !busy && !locked && attempt != Attempt.Right
    PinKeys(keysOn, { if (digits.length < MAX_DIGITS) digits += it }, { digits = digits.dropLast(1) })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(stringResource(R.string.pin_cancel), onCancel, Modifier.weight(1f))
        PillButton(
            stringResource(R.string.pin_unlock), ::submit, Modifier.weight(1f),
            style = PillStyle.Suggested, busy = busy, enabled = keysOn && digits.length >= MIN_DIGITS,
        )
    }
    Text(
        stringResource(R.string.pin_forgot),
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { forgot = !forgot }.padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
    )
    AnimatedVisibility(forgot, enter = expandVertically(motion(tween(240, easing = Ease))), exit = shrinkVertically(motion(tween(240, easing = Ease)))) {
        Text(stringResource(R.string.pin_forgot_body), Modifier.widthIn(max = 300.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Dim, textAlign = TextAlign.Center)
    }
}

/**
 * Create + confirm (D38): enter, enter again, a mismatch asks for the confirm again. [onCreate] saves the
 * PIN (off-main, in PinStore). The wizard's step 1 passes [subtitle] and [top] (its step and dots); Cambiar PIN reuses it.
 */
@Composable
fun CreatePinScreen(
    onCreate: suspend (String) -> Unit, modifier: Modifier = Modifier, title: String = stringResource(R.string.pin_create_title),
    subtitle: String? = null, onBack: (() -> Unit)? = null, top: @Composable () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var state by remember { mutableStateOf(CreateState()) }
    var digits by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var shake by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        HeaderBar(title, subtitle = subtitle, start = { if (onBack != null) FlatIconButton(R.drawable.ic_back, stringResource(R.string.back), onBack) })
        top()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Text(
                stringResource(if (state.step == CreateStep.ENTER) R.string.pin_create_enter else R.string.pin_create_confirm),
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            )
            PinSlots(digits.length, shake)
            Text(
                if (state.mismatch) stringResource(R.string.pin_create_mismatch) else "",
                Modifier.heightIn(min = 20.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.RedFg, textAlign = TextAlign.Center,
            )
            PinKeys(!busy, { if (digits.length < MAX_DIGITS) digits += it }, { digits = digits.dropLast(1) })
            PillButton(
                stringResource(if (state.step == CreateStep.ENTER) R.string.pin_continue else R.string.pin_save),
                {
                    val next = state.submit(digits)
                    digits = ""
                    state = next
                    if (next.mismatch) { shake++; haptic.performHapticFeedback(HapticFeedbackType.Reject) }
                    next.result?.let { pin -> scope.launch { busy = true; onCreate(pin) } }
                },
                Modifier.widthIn(min = 200.dp), style = PillStyle.Suggested, busy = busy, enabled = digits.length >= MIN_DIGITS,
            )
            Text(stringResource(R.string.pin_create_note), Modifier.widthIn(max = 300.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Dim, textAlign = TextAlign.Center)
        }
    }
}
