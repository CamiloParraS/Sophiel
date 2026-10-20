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
 * The unlocked countdown: a glass chip on the dark header, beside the gear (Status) or at the end of a parent screen's header. "1:48 · Bloquear ahora";
 * a tap locks. Tabular digits, so the chip doesn't twitch as the seconds change.
 */
@Composable
fun UnlockedChip(door: Door) {
    val left = dev.sophiel.pin.formatCountdown(door.remainingMs)
    val label = stringResource(R.string.unlocked_banner, left)
    val ease = tween<Float>(160, easing = Ease)
    AnimatedVisibility(
        door.unlocked,
        enter = fadeIn(motion(ease)) + scaleIn(motion(ease), initialScale = .95f),
        exit = fadeOut(motion(tween(120, easing = Ease))) + scaleOut(motion(tween(120, easing = Ease)), targetScale = .95f),
    ) {
        Row(
            Modifier.minimumInteractiveComponentSize()
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.lock_now), onClick = door.lockNow)
                .semantics(mergeDescendants = true) { contentDescription = label }
                .heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(Palette.DarkFill)
                .padding(start = 10.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SIcon(R.drawable.ic_unlock, Palette.Mint, 16.dp)
            Text(left, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = Palette.Text0)
            // At large text the suffix would push the gear and the name off the bar; the whole chip still locks (label above).
            if (androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.5f) Text("· " + stringResource(R.string.lock_now), style = MaterialTheme.typography.labelLarge, color = Palette.DarkInk)
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
