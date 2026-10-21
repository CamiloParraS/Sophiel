package dev.sophiel.ui

import androidx.annotation.DrawableRes
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
    val (bg, tile, badge) = when (tone) {
        InkTone.Ok -> Triple(Palette.Moss, Palette.TileOn, Palette.Text0)
        InkTone.Amber -> Triple(Palette.HeroAmber, Palette.TileAmber, Palette.AmberInk)
        InkTone.Red -> Triple(Palette.HeroRed, Palette.TileRed, Palette.RedInk)
    }
    val side = if (centered) Alignment.CenterHorizontally else Alignment.Start
    @Composable fun BadgeBox(m: Modifier) = Box(m.size(64.dp).clip(if (square) RoundedCornerShape(20.dp) else CookieShape(10)).background(badge), contentAlignment = Alignment.Center) {
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
            for (r in 0 until rows) for (q in 0 until 7) {
                val at = Offset(q * t + gap, r * t + gap)
                drawRoundRect(tile, at, Size(s, s), corner)
                if (r * 7 + q in worn) {
                    drawRoundRect(Palette.GreenDeep, at, Size(s, s), corner)
                    drawRoundRect(CamoBrush, at, Size(s, s), corner)
                    drawRoundRect(Palette.Mint.copy(alpha = .7f), at, Size(s, s), corner, style = Stroke(1.5.dp.toPx()))
                }
            }
            drawRect(Brush.verticalGradient(.3f to Color.Transparent, .86f to bg.copy(alpha = .92f)))
        }
        if (icon != null && !centered) BadgeBox(Modifier.align(Alignment.TopEnd).padding(16.dp))
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp), horizontalAlignment = side) {
            header?.invoke()
            if (icon != null && centered) BadgeBox(Modifier.padding(bottom = 10.dp))
            CappedFontScale(1.3f) {
                Text(
                    title, Modifier.padding(top = if ((icon == null || !centered) && header == null) 56.dp else 0.dp).asHeading(), color = Palette.Text0,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = titleSize, lineHeight = titleSize * 1.08f, lineBreak = LineBreak.Heading),
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                )
            }
            if (text != null) Text(
                text, Modifier.padding(top = 8.dp), color = Palette.Text1,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp, lineBreak = LineBreak.Heading),
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
            if (footer != null) Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = side, content = footer)
        }
    }
}
