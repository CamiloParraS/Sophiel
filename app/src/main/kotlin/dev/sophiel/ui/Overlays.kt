package dev.sophiel.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animate as animateTo
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import dev.sophiel.ui.theme.reducedMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import dev.sophiel.R
import androidx.compose.ui.window.Dialog
import dev.sophiel.ui.theme.Drawer
import dev.sophiel.ui.theme.Ease
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.motion

/**
 * Bottom sheet over a dimmed scrim, drawn inside the screen (place it last in a full-size Box).
 * Rises on the drawer curve and leaves on a quicker one; reduced motion makes both a jump.
 * Drags down 1:1 (up is resisted); let go past a quarter of its height, or with a downward flick, and it closes.
 */
@Composable
fun BoxScope.BottomSheet(visible: Boolean, title: String, onDismiss: () -> Unit, color: Color = Palette.Win, content: @Composable ColumnScope.() -> Unit) {
    var drag by remember { mutableFloatStateOf(0f) }
    var back by remember { mutableFloatStateOf(0f) } // predictive Back progress, 0..1
    val reduce = reducedMotion()
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(visible) { events ->
        try {
            events.collect { if (!reduce) back = it.progress }
            onDismiss() // back stays where the gesture ended; the exit slide carries on from there
        } catch (e: CancellationException) {
            scope.launch { animateTo(back, 0f, animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> back = v } }
        }
    }
    var height by remember { mutableIntStateOf(0) }
    val close = stringResource(R.string.sheet_close)
    val settle = motion<Float>(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
    LaunchedEffect(visible) { if (visible) { drag = 0f; back = 0f } }
    AnimatedVisibility(visible, enter = fadeIn(motion(tween(240))), exit = fadeOut(motion(tween(240)))) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - .6f * back }.background(Color.Black.copy(alpha = .4f)).clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = close, role = Role.Button, onClick = onDismiss).semantics { contentDescription = close })
    }
    AnimatedVisibility(
        visible,
        Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(motion(tween(400, easing = Drawer))) { it },
        exit = slideOutVertically(motion(tween(240, easing = Ease))) { it },
    ) {
        Column(
            Modifier
                .semantics { paneTitle = title; dismiss { onDismiss(); true } } // TalkBack: announces the sheet, offers Dismiss
                .offset { IntOffset(0, drag.roundToInt()) }
                .graphicsLayer { // follows the finger a little: shrinks toward its bottom edge and sinks
                    val k = 1f - .08f * back
                    scaleX = k; scaleY = k; transformOrigin = TransformOrigin(.5f, 1f); translationY = back * size.height * .05f
                }
                .onSizeChanged { height = it.height }
                .draggable(
                    rememberDraggableState { dy ->
                        // Up past the rest point: a fraction of the finger, so it gives a little and stops.
                        drag += if (drag + dy < 0) dy * .15f else dy
                    },
                    Orientation.Vertical,
                    onDragStopped = { velocity ->
                        if (drag > height / 4f || velocity > 1200f) onDismiss()
                        else animate(drag, 0f, velocity, settle) { v, _ -> drag = v }
                    },
                )
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(color)
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

/** Page content under an open sheet: TalkBack skips it, so the sheet behaves as modal. */
fun Modifier.behindSheet(open: Boolean): Modifier = if (open) clearAndSetSemantics { } else this

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
            Text(title, Modifier.asHeading(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Dim, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SButton(cancelLabel, onDismiss, Modifier.weight(1f))
                SButton(confirmLabel, onConfirm, Modifier.weight(1f), style = if (destructive) BtnStyle.Destructive else BtnStyle.Suggested)
            }
        }
    }
}
