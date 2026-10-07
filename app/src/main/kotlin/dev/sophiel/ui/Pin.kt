package dev.sophiel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

/** One pad key: fills its row slot, scales down while pressed (like PillButton). */
@Composable
private fun RowScope.PinKey(
    enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, bg: Color = Palette.Btn,
    onLongClick: (() -> Unit)? = null, content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, motion(tween(120, easing = Ease)), label = "key")
    Box(
        modifier.weight(1f).height(64.dp).scale(scale).clip(RoundedCornerShape(14.dp)).background(bg)
            .combinedClickable(source, ripple(), enabled = enabled, role = Role.Button, onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Number pad. [enabled] gates taps; [dim] greys it out (locked or done), so a quick verify doesn't flash it.
 * [onConfirm], if given, fills the bottom-left slot with a ✓ key; [busy] shows a spinner there.
 */
@Composable
private fun PinKeys(
    enabled: Boolean, onDigit: (Char) -> Unit, onDelete: () -> Unit, onClear: () -> Unit, dim: Boolean = false,
    onConfirm: (() -> Unit)? = null, canConfirm: Boolean = false, busy: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val deleteLabel = stringResource(R.string.pin_delete)
    val confirmLabel = stringResource(R.string.pin_unlock)
    val tap = { haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap) }
    Column(Modifier.widthIn(max = 340.dp).fillMaxWidth().alpha(if (dim) .35f else 1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("123", "456", "789", " 0<").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    when (k) {
                        ' ' -> if (onConfirm == null) Spacer(Modifier.weight(1f)) else {
                            val primary = MaterialTheme.colorScheme.primary
                            val fg = MaterialTheme.colorScheme.onPrimary
                            PinKey(
                                enabled && canConfirm && !busy, onConfirm, Modifier.semantics { contentDescription = confirmLabel },
                                bg = primary.copy(alpha = if (canConfirm || busy) 1f else .45f),
                            ) {
                                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = fg, trackColor = fg.copy(alpha = .4f), strokeWidth = 2.dp)
                                else SIcon(R.drawable.ic_check, fg, 24.dp)
                            }
                        }
                        '<' -> PinKey(
                            enabled, { tap(); onDelete() }, Modifier.semantics { contentDescription = deleteLabel },
                            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClear() },
                        ) { SIcon(R.drawable.ic_del, Palette.Fg, 24.dp) }
                        else -> PinKey(enabled, { tap(); onDigit(k) }) {
                            Text(k.toString(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal))
                        }
                    }
                }
            }
        }
    }
}

private enum class Attempt { Idle, Wrong, Right }

/**
 * The PIN sheet's content (D38, D44): slots, keys (the ✓ key unlocks), Cancelar.[onUnlocked] runs after a right PIN and a
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
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
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
    PinKeys(
        keysOn, { if (digits.length < MAX_DIGITS) digits += it }, { digits = digits.dropLast(1) }, { digits = "" },
        dim = locked || attempt == Attempt.Right, onConfirm = ::submit, canConfirm = digits.length >= MIN_DIGITS, busy = busy,
    )
    PillButton(stringResource(R.string.pin_cancel), onCancel, Modifier.widthIn(min = 200.dp))
    Text(
        stringResource(R.string.pin_forgot),
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { forgot = !forgot }.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 14.dp),
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
            PinKeys(!busy, { if (digits.length < MAX_DIGITS) digits += it }, { digits = digits.dropLast(1) }, { digits = "" })
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
