package dev.sophiel.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sophiel.settings.ParentPreset
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.reducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** What each wizard step shows in the middle: the Status hero's tiles, doing the thing the step is about. */
enum class VisualKind { OVERLAY, SERVICE, AWAKE, NOTIFICATIONS, PRESET, START }

private const val COLS = 5
private const val ROWS = 3

private fun smooth(x: Float): Float { val k = x.coerceIn(0f, 1f); return k * k * (3 - 2 * k) }

/** How many of the 15 tiles each "Cómo revisa" preset looks at, spread over the grid. */
private fun watched(preset: ParentPreset): List<Int> {
    val n = when (preset) { ParentPreset.LIGHT -> 4; ParentPreset.BALANCED -> 8; ParentPreset.THOROUGH -> 12; ParentPreset.PRECISE -> 15 }
    return (0 until COLS * ROWS).filter { it * n / (COLS * ROWS) != (it - 1) * n / (COLS * ROWS) || it == 0 }
}

/** A 5x3 tile grid on a 4.2 s loop. Reduced motion draws one still frame. [preset] only matters for [VisualKind.PRESET]. */
@Composable
fun TileVisual(kind: VisualKind, modifier: Modifier = Modifier, height: Dp = 168.dp, preset: ParentPreset = ParentPreset.BALANCED) {
    val still = reducedMotion()
    val clock = rememberInfiniteTransition(label = "visual")
    val loop = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val seen = if (kind == VisualKind.PRESET) watched(preset) else emptyList()
    Canvas(modifier.fillMaxWidth().height(height)) {
        // Read here, in the draw phase: the loop redraws the canvas, never recomposes the step.
        val time = if (still) when (kind) { VisualKind.SERVICE -> .6f; VisualKind.START -> .8f; VisualKind.OVERLAY -> .5f; else -> .3f } else loop.value
        val gap = 4.dp.toPx()
        val tile = minOf(46.dp.toPx(), (size.height - (ROWS - 1) * gap) / ROWS) // shrinks to fit a short box
        val x0 = (size.width - (COLS * tile + (COLS - 1) * gap)) / 2
        val y0 = (size.height - (ROWS * tile + (ROWS - 1) * gap)) / 2
        val corner = CornerRadius(8.dp.toPx())
        val box = Size(tile, tile)
        for (r in 0 until ROWS) for (q in 0 until COLS) {
            val i = r * COLS + q
            val at = Offset(x0 + q * (tile + gap), y0 + r * (tile + gap))
            val pos = (q + r) / (COLS + ROWS - 2f)
            val cover = when (kind) {
                VisualKind.OVERLAY -> (1f - abs(time * 1.5f - .25f - pos) * 3.5f).coerceIn(0f, 1f) // a layer sweeping over the screen
                VisualKind.SERVICE -> { // see-through, then fully opaque
                    val s = if (time < .45f) time / .45f else if (time < .8f) 1f else (1f - time) / .2f
                    .28f + .72f * smooth(s)
                }
                VisualKind.AWAKE -> if ((q to r) == (1 to 0) || (q to r) == (3 to 1) || (q to r) == (2 to 2)) {
                    sin(PI.toFloat() * ((time + (q + r) * .31f) % 1f)) // three tiles breathing out of step
                } else 0f
                VisualKind.NOTIFICATIONS -> if (q == 2 && r == 1) 1f else 0f
                VisualKind.PRESET -> seen.indexOf(i).let { k ->
                    if (k < 0) 0f else (1f - (((time * seen.size - k) % seen.size + seen.size) % seen.size)).coerceIn(0f, 1f) // the scan, tile by tile
                }
                VisualKind.START -> ((time * 1.7f - pos * .6f) * 4f).coerceIn(0f, 1f) * (if (time > .88f) (1f - time) / .12f else 1f)
            }
            drawRoundRect(Palette.Moss.copy(alpha = .07f), at, box, corner)
            val edge = if (kind == VisualKind.PRESET && i in seen) Palette.Moss.copy(alpha = .75f) else Palette.Moss.copy(alpha = .14f)
            drawRoundRect(edge, at, box, corner, style = Stroke((if (kind == VisualKind.PRESET && i in seen) 2.dp else 1.dp).toPx()))
            if (cover > 0f) {
                drawRoundRect(Palette.Moss.copy(alpha = cover), at, box, corner)
                drawRoundRect(CamoBrush, at, box, corner, alpha = cover)
            }
        }
        if (kind == VisualKind.NOTIFICATIONS) {
            val c = Offset(x0 + 2 * (tile + gap) + tile / 2, y0 + 1 * (tile + gap) + tile / 2)
            for (w in 0 until 2) {
                val k = ((time * 2f + w * .5f) % 1f)
                drawCircle(Palette.Moss.copy(alpha = .55f * (1f - k)), tile / 2 + k * 70.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            }
        }
    }
}
