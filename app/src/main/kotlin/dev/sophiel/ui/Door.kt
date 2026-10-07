package dev.sophiel.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
        content(door)
        BottomSheet(visible = pending != null, onDismiss = { pending = null }) {
            PinPrompt(
                container.pin, clock,
                onUnlocked = {
                    unlock.extend()
                    val action = pending
                    pending = null
                    action?.invoke()
                },
                onCancel = { pending = null },
            )
        }
    }
}

/** "Ajustes desbloqueado · Se bloquea en 1:48" with Bloquear ahora; folds away at zero. */
@Composable
fun UnlockedBanner(door: Door, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        door.unlocked,
        modifier,
        enter = expandVertically(motion(tween(200, easing = Ease))) + fadeIn(motion(tween(160))),
        exit = shrinkVertically(motion(tween(200, easing = Ease))) + fadeOut(motion(tween(160))),
    ) {
        val primary = MaterialTheme.colorScheme.primary
        Row(
            Modifier.fillMaxWidth().background(Palette.tint(primary)).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SIcon(R.drawable.ic_unlock, primary)
            Text(
                stringResource(R.string.unlocked_banner, dev.sophiel.pin.formatCountdown(door.remainingMs)),
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Palette.Fg,
            )
            PillButton(stringResource(R.string.lock_now), door.lockNow, small = true)
        }
    }
}

/** Gear for Ajustes: a small lock on it while the door is shut. */
@Composable
fun GearButton(door: Door, onOpen: () -> Unit) {
    Box {
        FlatIconButton(R.drawable.ic_gear, stringResource(R.string.settings_title)) { door.pass(onOpen) }
        if (!door.unlocked) SIcon(R.drawable.ic_lock, Palette.Dim, 11.dp, Modifier.align(Alignment.BottomEnd).padding(end = 9.dp, bottom = 9.dp))
    }
}
