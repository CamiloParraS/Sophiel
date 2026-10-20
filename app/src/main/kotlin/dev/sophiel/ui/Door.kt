package dev.sophiel.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.sophiel.AppContainer
import dev.sophiel.R
import dev.sophiel.pin.Unlock
import dev.sophiel.pin.gate
import dev.sophiel.ui.theme.Ease
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.motion

/** What a screen needs from the door (D38): whether it is open, how long it stays open, and how to pass it. */
class Door(val unlocked: Boolean, val remainingMs: Long, val pass: (() -> Unit) -> Unit, val lockNow: () -> Unit)

/**
 * One door for Stop and Ajustes. Unlocked: the action runs and the window extends. Locked: the PIN sheet
 * rises over [content]; a right PIN runs the action. Expiry is read from a ticking clock, never scheduled.
 */
@Composable
fun DoorHost(container: AppContainer, content: @Composable BoxScope.(Door) -> Unit) {
    val unlock = container.unlock
    val clock = SystemClock::elapsedRealtime
    val endsAt by unlock.endsAt.collectAsState()
    val now by tickingNow(clock, active = endsAt != null)
    val unlocked = endsAt?.let { now < it } == true
    LaunchedEffect(now) { unlock.unlocked() } // an expired window folds away and the lock icons come back
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val door = Door(
        unlocked = unlocked,
        remainingMs = ((endsAt ?: 0L) - now).coerceAtLeast(0),
        pass = { action -> if (!unlock.gate(action)) pending = action },
        lockNow = unlock::lockNow,
    )
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().behindSheet(pending != null)) { content(door) }
        BottomSheet(visible = pending != null, title = stringResource(R.string.pin_title), onDismiss = { pending = null }, color = Palette.Chalk) { // Chalk: the Surface keys need a page to lift off
            PinPrompt(
                container.pin, clock,
                onUnlocked = {
                    // The padlock animates first; a sheet dismissed meanwhile must not unlock.
                    val action = pending ?: return@PinPrompt
                    unlock.extend()
                    pending = null
                    action()
                },
                onCancel = { pending = null },
            )
        }
    }
}

/**
 * The unlocked countdown (D71): the open padlock in a ring that empties as the window runs out, on the dark header beside the gear
 * (Status) or at the end of a parent screen's header. A tap locks. The time left is spoken, not printed: the ring is enough to see.
 */
@Composable
fun UnlockedChip(door: Door) {
    val label = stringResource(R.string.unlocked_banner, dev.sophiel.pin.formatCountdown(door.remainingMs))
    val ease = tween<Float>(160, easing = Ease)
    AnimatedVisibility(
        door.unlocked,
        enter = fadeIn(motion(ease)) + scaleIn(motion(ease), initialScale = .9f),
        exit = fadeOut(motion(tween(120, easing = Ease))) + scaleOut(motion(tween(120, easing = Ease)), targetScale = .9f),
    ) {
        val left = (door.remainingMs / dev.sophiel.pin.Unlock.WINDOW_MS.toFloat()).coerceIn(0f, 1f)
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.lock_now), onClick = door.lockNow)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(36.dp)) {
                val w = 2.5.dp.toPx() // thick enough to read as a timer, not an outline
                drawCircle(Palette.DarkFill, size.minDimension / 2)
                drawArc(Palette.Mint, -90f, -360f * left, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
            }
            SIcon(R.drawable.ic_unlock, Palette.Mint, 16.dp)
        }
    }
}

/** Gear for Ajustes: a small lock on it while the door is shut. */
@Composable
fun GearButton(door: Door, onOpen: () -> Unit, onDark: Boolean = false) {
    Box {
        SButton(null, { door.pass(onOpen) }, style = if (onDark) BtnStyle.OnDark else BtnStyle.Default, icon = R.drawable.ic_gear, label = stringResource(R.string.settings_title))
        if (!door.unlocked) SIcon(R.drawable.ic_lock, if (onDark) Palette.DarkLock else Palette.Dim, 11.dp, Modifier.align(Alignment.BottomEnd).padding(end = 9.dp, bottom = 9.dp))
    }
}
