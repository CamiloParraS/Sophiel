package dev.sophiel.ui

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import kotlin.math.ceil
import androidx.compose.ui.unit.coerceIn
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sophiel.R
import dev.sophiel.capture.DebugMask
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.motion
import dev.sophiel.ui.theme.reducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

// The Status hero, cut from .scratch/mockup-home-v2.html: a grid of tiles (the screen the app watches), a state badge and the headline.

private const val COLS = 7
private val Moss = Color(0xFF12201A)
private val Chalk = Palette.Chalk
private val Text0 = Color(0xFFF2F7EF)
private val Text1 = Color(0xFFB9CDB4)
private val Edge = Color(0xFFBFD8A8)

private const val CHUNK = 4 // device pixels per dither cell
private const val SIDE = DebugMask.SIDE * CHUNK
private val Bayer4 = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

/** The mask's own camo, sparse and dithered, drawn on the tiles the protected wave passes over. */
private val CamoBrush: ShaderBrush by lazy {
    val field = DebugMask.camo(64, 128, 0xFFFFFF, seed = 5)
    val colour = DebugMask.PATTERN
    val px = IntArray(DebugMask.SIDE * DebugMask.SIDE) { k ->
        val x = k % DebugMask.SIDE
        val y = k / DebugMask.SIDE
        val cut = .72f + (Bayer4[(y and 3) * 4 + (x and 3)] / 16f - .5f) * .22f
        if ((field[k] and 0xFF) / 255f > cut) (0x70 shl 24) or (colour[k] and 0xFFFFFF) else 0
    }
    val small = Bitmap.createBitmap(px, DebugMask.SIDE, DebugMask.SIDE, Bitmap.Config.ARGB_8888)
    val big: ImageBitmap = Bitmap.createScaledBitmap(small, SIDE, SIDE, false).asImageBitmap()
    ShaderBrush(ImageShader(big, TileMode.Repeated, TileMode.Repeated))
}

// Either side of the badge; any that land under text are skipped.
/** The shipped mask pattern itself, dense and at screen scale, as the overlay draws it. */
private val MaskBrush: ShaderBrush by lazy { ShaderBrush(ImageShader(DebugMask.patternBitmap().asImageBitmap(), TileMode.Repeated, TileMode.Repeated)) }

private val RedTiles = setOf(5 to 1, 1 to 2, 5 to 3, 0 to 4)
private val OrangeTiles = setOf(5 to 1, 1 to 2, 6 to 3, 0 to 4)

@Composable
fun GridHero(page: Page, title: String, text: String, modifier: Modifier = Modifier, fit: Dp? = null, gear: @Composable () -> Unit = {}) {
    val still = reducedMotion()
    val full = (LocalConfiguration.current.screenHeightDp * .5f).dp.coerceIn(340.dp, 440.dp)
    // The hero takes what the sheet leaves (never below 320 dp), so spare height becomes atmosphere, not a gap above the button.
    // The first fit snaps: nothing animates on open.
    var fitted by remember { mutableStateOf(false) }
    val hero by animateDpAsState((fit ?: full).coerceAtLeast(320.dp), if (fitted) motion(tween(240)) else snap(), label = "hero")
    SideEffect { if (fit != null) fitted = true }
    // Light icons on the dark hero; back to dark ones when leaving.
    val view = LocalView.current
    DisposableEffect(view) {
        val bars = WindowCompat.getInsetsController((view.context as Activity).window, view)
        bars.isAppearanceLightStatusBars = false
        onDispose { bars.isAppearanceLightStatusBars = true }
    }
    val bg by animateColorAsState(
        when (page) { Page.INTERRUPTED -> Color(0xFF231A1A); Page.STOPPED -> Color(0xFF241F17); else -> Moss }, motion(tween(300)), label = "bg",
    )
    val tile by animateColorAsState(
        when (page) {
            Page.OFF -> Color(0xFF1B2A24); Page.INTERRUPTED -> Color(0xFF2F2222); Page.STOPPED -> Color(0xFF2F2820); else -> Color(0xFF1A2F23)
        },
        motion(tween(300)), label = "tile",
    )
    val (badge, icon) = when (page) {
        Page.PROTECTED -> Color(0xFF6FE6A8) to R.drawable.ic_mark // the app's padlock, shut
        Page.INTERRUPTED -> Color(0xFFFF8A8F) to R.drawable.ic_warn
        Page.STOPPED -> Color(0xFFFFC08A) to R.drawable.ic_power
        Page.OFF -> Text1 to R.drawable.ic_power
        Page.READY -> Color(0xFF53AB7D) to R.drawable.ic_mark_open // armed, not shut: the green held back, the shackle up
        else -> Text1 to R.drawable.ic_mark_open
    }
    val badgeColor by animateColorAsState(badge, motion(tween(300)), label = "badge")

    // One clock, only while something moves: the idle glow and the entry wave (protected), the travelling outline (starting).
    var now by remember { mutableLongStateOf(0L) }
    var waveAt by remember { mutableLongStateOf(-100_000L) }
    LaunchedEffect(page, still) {
        if (still || (page != Page.PROTECTED && page != Page.STARTING && page != Page.READY)) return@LaunchedEffect
        var first = true
        while (true) {
            withFrameMillis {
                now = it
                if (first && page == Page.PROTECTED) waveAt = it
                first = false
            }
        }
    }

    // Where the name, gear and headline sit, in hero coordinates (stable while scrolling): tiles under them stay quiet.
    val hero0 = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var nameAt by remember { mutableStateOf(Rect.Zero) }
    var gearAt by remember { mutableStateOf(Rect.Zero) }
    var textAt by remember { mutableStateOf(Rect.Zero) }
    fun at(c: LayoutCoordinates) = hero0[0]?.localBoundingBoxOf(c) ?: Rect.Zero

    Box(modifier.fillMaxWidth().height(hero).background(bg).onPlaced { hero0[0] = it }) {
        Canvas(Modifier.fillMaxWidth().height(hero)) {
            drawGrid(page, tile, now, waveAt, still, listOf(nameAt, textAt, gearAt))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = .5f)), startY = size.height * .68f, endY = size.height))
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), Modifier.onGloballyPositioned { nameAt = at(it) }, style = MaterialTheme.typography.titleMedium, color = Text0)
            }
            Box(Modifier.onGloballyPositioned { gearAt = at(it) }) { gear() }
        }
        // Badge and headline as one group, centred in what the top bar and the sheet's lip leave.
        Column(
            Modifier.matchParentSize().statusBarsPadding().padding(start = 22.dp, end = 22.dp, top = 48.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Badge(badgeColor, bg, icon, spin = page == Page.STARTING)
            Column(Modifier.padding(top = 4.dp).onGloballyPositioned { textAt = at(it) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp, lineHeight = 36.sp), color = Text0, textAlign = TextAlign.Center)
                Text(text, Modifier.padding(top = 8.dp).widthIn(max = 300.dp), style = MaterialTheme.typography.bodyMedium, color = Text1, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 96 dp disc in the state colour (tint, 1.5 dp ring, soft outer halo). */
@Composable
private fun Badge(color: Color, bg: Color, @DrawableRes icon: Int, spin: Boolean) {
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(120.dp)) {
            val r = 48.dp.toPx()
            drawCircle(color.copy(alpha = .07f), r + 6.dp.toPx(), style = Stroke(12.dp.toPx()))
            drawCircle(bg, r)
            drawCircle(MaskBrush, r, alpha = .22f) // the real mask, 1:1, faint: the badge wears what Sophiel puts on a screen
            drawCircle(color.copy(alpha = .14f), r)
            drawCircle(color.copy(alpha = .4f), r - .75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        }
        if (spin) CircularProgressIndicator(Modifier.size(40.dp), color = color, trackColor = color.copy(alpha = .25f), strokeWidth = 3.dp)
        else SIcon(icon, color, 52.dp)
    }
}

private fun DrawScope.drawGrid(page: Page, tile: Color, now: Long, waveAt: Long, still: Boolean, texts: List<Rect>) {
    val t = size.width / COLS
    val rows = ceil(size.height / t).toInt()
    val gap = 1.5.dp.toPx()
    val s = t - 2 * gap
    val corner = CornerRadius(2.dp.toPx())
    val pad = 4.dp.toPx()
    fun under(r: Rect, tileAt: Rect) = !r.isEmpty && r.inflate(pad).overlaps(tileAt)
    val tt = (now - waveAt) / 1000f
    val waving = !still && page == Page.PROTECTED && tt in 0f..4f
    // Ambient life, calm states only (interrupted/stopped stay frozen: the stillness says something): a slow diagonal light band every ~10 s,
    // and on Protected three tiles that breathe out of step, each on its own period.
    val ambient = !still && (page == Page.PROTECTED || page == Page.READY)
    val bandAt = ((now / 10_000f) % 1f) * (COLS + rows + 6) - 3
    fun glows(q: Int, r: Int): Float {
        var a = 0f
        for (i in 0 until 3) {
            val period = 3400L + i * 900L
            val pick = ((((now / period) + i * 7) * 2654435761L) and 0xFFFFFFFFL) % (COLS * rows)
            if (pick.toInt() == r * COLS + q) a = max(a, sin(PI.toFloat() * ((now % period) / period.toFloat())))
        }
        return a
    }
    val phase = ((now / 1000f) * (if (still) 0f else 1.1f) + .9f) % 2.4f
    for (r in 0 until rows) for (q in 0 until COLS) {
        val x = q * t + gap
        val y = r * t + gap
        val at = Offset(x, y)
        val box = Size(s, s)
        val tileAt = Rect(at, box)
        drawRoundRect(tile, at, box, corner)
        // Tiles under the name, gear and text: no camo, dimmed outlines (still outlined, so no holes).
        val quiet = texts.any { under(it, tileAt) }
        val dim = if (quiet) .35f else 1f
        val edge = Stroke(1.dp.toPx())
        if (page != Page.READY && page != Page.STARTING) {
            drawRoundRect(Edge.copy(alpha = .1f * (if (quiet) .5f else 1f)), at, box, corner, style = edge)
        }
        when (page) {
            Page.PROTECTED -> {
                val w = tt * 3.4f - hypot(q + .5f - COLS / 2f, r + .5f - (size.height / t + .3f))
                if (!quiet && waving && w > 0f && w < 1.6f) {
                    drawRoundRect(Color(0xFF287548).copy(alpha = .5f * sin(PI.toFloat() * w / 1.6f)), at, box, corner)
                    drawRoundRect(CamoBrush, at, box, corner)
                }
                if (ambient) {
                    val k = (1f - abs(q + r - bandAt) / 2.5f).coerceAtLeast(0f)
                    drawRoundRect(Color(0xFF6FE6A8).copy(alpha = .07f * k * k * (3 - 2 * k) * dim), at, box, corner)
                    // A breathing tile wears the mask's camo, as if Sophiel just covered it.
                    val g = if (quiet) 0f else glows(q, r)
                    if (g > 0f) {
                        drawRoundRect(Color(0xFF287548).copy(alpha = .35f * g), at, box, corner)
                        drawRoundRect(CamoBrush, at, box, corner, alpha = g)
                    }
                }
            }
            Page.INTERRUPTED -> if (!quiet && q to r in RedTiles) {
                drawRoundRect(Color(0x99E41826), at, box, corner)
                val c = Offset(x + s / 2, y + s / 2)
                val k = 7.dp.toPx()
                val ink = Color(0xFFFF8A8F)
                val line = 2.dp.toPx()
                drawLine(ink, c - Offset(k, k), c + Offset(k, k), line, StrokeCap.Round)
                drawLine(ink, c + Offset(k, -k), c + Offset(-k, k), line, StrokeCap.Round)
            } else if (!quiet && (q * 7 + r * 3) % 5 == 0) drawRoundRect(Color(0x14FFFFFF), at, box, corner)
            Page.READY -> {
                // The armed outlines catch the band as it passes.
                val k = if (ambient) (1f - abs(q + r - bandAt) / 2.5f).coerceAtLeast(0f) else 0f
                drawRoundRect(Edge.copy(alpha = (.22f + .3f * k * k * (3 - 2 * k)) * dim), at, box, corner, style = Stroke(1.2.dp.toPx()))
            }
            Page.STARTING -> {
                val pos = (r * COLS + q) / (rows * COLS).toFloat() * 2.4f
                val a = max(0f, 1f - abs((phase - pos + 2.4f) % 2.4f) / .5f)
                drawRoundRect(Edge.copy(alpha = (.15f + .7f * a) * dim), at, box, corner, style = Stroke((1.2f + a).dp.toPx()))
            }
            Page.STOPPED -> if (!quiet && q to r in OrangeTiles) {
                drawRoundRect(Color(0x8CEA6200), at, box, corner)
                val c = Offset(x + s / 2, y + s / 2)
                val ink = Color(0xFFFFC08A)
                drawLine(ink, c - Offset(0f, 9.dp.toPx()), c + Offset(0f, 2.dp.toPx()), 2.4.dp.toPx(), StrokeCap.Round)
                drawCircle(ink, 1.4.dp.toPx(), c + Offset(0f, 9.dp.toPx()))
            } else if (!quiet && (q * 7 + r * 3) % 5 == 0) drawRoundRect(Color(0x14FFFFFF), at, box, corner)
            Page.OFF -> Unit
        }
    }
}
