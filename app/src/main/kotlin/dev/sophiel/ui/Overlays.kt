package dev.sophiel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.sophiel.ui.theme.Drawer
import dev.sophiel.ui.theme.Ease
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.motion

/**
 * Bottom sheet over a dimmed scrim, drawn inside the screen (place it last in a full-size Box).
 * Rises on the drawer curve and leaves on a quicker one; reduced motion makes both a jump.
 */
@Composable
fun BoxScope.BottomSheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(motion(tween(240))), exit = fadeOut(motion(tween(240)))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f)).clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
    }
    AnimatedVisibility(
        visible,
        Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(motion(tween(400, easing = Drawer))) { it },
        exit = slideOutVertically(motion(tween(240, easing = Ease))) { it },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Palette.Win)
                .pointerInput(Unit) { detectTapGestures { } } // taps on the sheet must not reach the scrim
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Fg.copy(alpha = .2f)))
            content()
        }
    }
}

/** Centred alert with two pill buttons. [destructive] paints the confirm button red, otherwise it is the suggested one. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(18.dp)).background(Palette.Win).padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Dim, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(cancelLabel, onDismiss, Modifier.weight(1f))
                PillButton(confirmLabel, onConfirm, Modifier.weight(1f), style = if (destructive) PillStyle.Destructive else PillStyle.Suggested)
            }
        }
    }
}
