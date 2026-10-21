package dev.sophiel.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import dev.sophiel.ui.theme.Ease
import dev.sophiel.ui.theme.motion
import dev.sophiel.ui.theme.reducedMotion
import kotlin.math.abs
import kotlin.math.sin
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sophiel.ui.theme.Palette
import kotlin.math.ceil

/** D75: the colour a dark state card wears. Green only ever means protected; amber and red are the app's truth colours. */
enum class InkTone { Ok, Amber, Red }

/**
 * D75: the Parent phone's state card (login, role, home cards, empty list): the Status card's tile wall in the page's ink, a cookie or square
 * badge, a display headline and one line. Static (the wall only moves on Status). [lit] tiles wear the mask's camo, like Status's covers.
 * [header] and [footer] sit above the headline and below the line (a Child phone's name, its Start / Stop pill).
 */
@Composable
fun InkCard(
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    tone: InkTone = InkTone.Ok,
    @DrawableRes icon: Int? = null,
    square: Boolean = false,
    centered: Boolean = false,
    titleSize: TextUnit = 40.sp,
    lit: Int = 0,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val (bg0, tile0, badge0) = when (tone) {
        InkTone.Ok -> Triple(Palette.Moss, Palette.TileOn, Palette.Text0)
        InkTone.Amber -> Triple(Palette.HeroAmber, Palette.TileAmber, Palette.AmberInk)
        InkTone.Red -> Triple(Palette.HeroRed, Palette.TileRed, Palette.RedInk)
    }
    // A state change (a Child phone stopping, a login failing) re-tints the card instead of cutting.
    val bg by animateColorAsState(bg0, motion(tween(300)), label = "ink")
    val tile by animateColorAsState(tile0, motion(tween(300)), label = "tiles")
    val badge by animateColorAsState(badge0, motion(tween(300)), label = "badge")
    // The card reads itself once when it arrives (or its tone changes): a mint sweep crosses the wall, top to bottom, as on Status's scan band,
    // and the camo tiles bloom as it passes. Under Remove animations it simply starts done.
    val still = reducedMotion()
    val sweep = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(tone, still) {
        if (still) return@LaunchedEffect sweep.snapTo(1f)
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(900, easing = Ease))
    }
    val pop = remember { Animatable(if (still) 1f else .7f) }
    LaunchedEffect(icon, square, still) {
        if (still) return@LaunchedEffect pop.snapTo(1f)
        pop.snapTo(.7f)
        pop.animateTo(1f, spring(dampingRatio = .55f, stiffness = 400f))
    }
    val side = if (centered) Alignment.CenterHorizontally else Alignment.Start
    @Composable fun BadgeBox(m: Modifier) = Box(m.size(64.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - .7f) / .3f).coerceIn(0f, 1f) }.clip(if (square) RoundedCornerShape(20.dp) else CookieShape(10)).background(badge), contentAlignment = Alignment.Center) {
        if (icon != null) SIcon(icon, bg, 28.dp)
    }
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(bg)) {
        Canvas(Modifier.matchParentSize()) {
            val t = size.width / 7
            val rows = ceil(size.height / t).toInt()
            val gap = 2.5.dp.toPx()
            val s = t - 2 * gap
            val corner = CornerRadius(10.dp.toPx())
            val worn = (0 until rows * 7).filter { it / 7 < 2 && (centered || it % 7 < 5) }.sortedBy { (it * 2654435761L) and 0xFFFFFFFFL }.take(lit).toSet()
            val band = sweep.value * (rows + 3) - 1.5f // the sweep's row; past the last, it is gone
            for (r in 0 until rows) for (q in 0 until 7) {
                val at = Offset(q * t + gap, r * t + gap)
                val k = r * 7 + q
                drawRoundRect(tile, at, Size(s, s), corner)
                if (k in worn) {
                    val a = ((band - r + .6f) / 1.2f).coerceIn(0f, 1f) // blooms as the sweep reaches its row
                    drawRoundRect(Palette.GreenDeep, at, Size(s, s), corner, alpha = a)
                    drawRoundRect(CamoBrush, at, Size(s, s), corner, alpha = a)
                    drawRoundRect(Palette.Mint.copy(alpha = .7f), at, Size(s, s), corner, style = Stroke(1.5.dp.toPx()), alpha = a)
                } else {
                    val b = (1f - abs(r - band) / 1.2f).coerceAtLeast(0f)
                    if (b > 0f) drawRoundRect((if (tone == InkTone.Ok) Palette.Mint else badge).copy(alpha = .2f * b * b * (3 - 2 * b)), at, Size(s, s), corner)
                }
            }
            drawRect(Brush.verticalGradient(.3f to Color.Transparent, .86f to bg.copy(alpha = .92f)))
        }
        if (icon != null && !centered) BadgeBox(Modifier.align(Alignment.TopEnd).padding(16.dp))
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp), horizontalAlignment = side) {
            header?.invoke()
            if (icon != null && centered) BadgeBox(Modifier.padding(bottom = 10.dp))
            Crossfade(title to text, animationSpec = motion(tween(160)), label = "words") { (ti, te) ->
                Column(horizontalAlignment = side) {
                    CappedFontScale(1.3f) {
                        Text(
                            ti, Modifier.padding(top = if ((icon == null || !centered) && header == null) 56.dp else 0.dp).asHeading(), color = Palette.Text0,
                            style = MaterialTheme.typography.displaySmall.copy(fontSize = titleSize, lineHeight = titleSize * 1.08f, lineBreak = LineBreak.Heading),
                            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                        )
                    }
                    if (te != null) Text(
                        te, Modifier.padding(top = 8.dp), color = Palette.Text1,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp, lineBreak = LineBreak.Heading),
                        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                    )
                }
            }
            if (footer != null) Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = side, content = footer)
        }
    }
}
