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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import kotlinx.coroutines.delay
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
internal val Moss = Palette.Moss
private val Chalk = Palette.Chalk
internal val Text0 = Palette.Text0
internal val Text1 = Palette.Text1
private val Edge = Palette.Edge

private const val CHUNK = 4 // device pixels per dither cell
private const val SIDE = DebugMask.SIDE * CHUNK
private val Bayer4 = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

/** The mask's own camo, sparse and dithered, drawn on the tiles the protected wave passes over. */
internal val CamoBrush: ShaderBrush by lazy {
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

/** Headlines are large text already: past [max] they stop growing, or one word no longer fits the line. */
@Composable
internal fun CappedFontScale(max: Float, content: @Composable () -> Unit) {
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale.coerceAtMost(max)),
        content = content,
    )
}

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
        when (page) { Page.INTERRUPTED -> Palette.HeroRed; Page.STOPPED -> Palette.HeroAmber; else -> Moss }, motion(tween(300)), label = "bg",
    )
    val tile by animateColorAsState(
        when (page) {
            Page.OFF -> Palette.HeroOff; Page.INTERRUPTED -> Palette.TileRed; Page.STOPPED -> Palette.TileAmber; else -> Palette.TileOn
        },
        motion(tween(300)), label = "tile",
    )
    // null icon = the app's padlock: shut on Protected, shackle up when armed (Ready, Starting).
    val (badge, icon) = when (page) {
        Page.PROTECTED -> Palette.Mint to null
        Page.INTERRUPTED -> Palette.RedInk to R.drawable.ic_warn
        Page.STOPPED -> Palette.AmberInk to R.drawable.ic_power
        Page.OFF -> Text1 to R.drawable.ic_power
        Page.READY -> Palette.MintDim to null // the green held back
        else -> Text1 to null
    }
    val badgeColor by animateColorAsState(badge, motion(tween(300)), label = "badge")
    // The shackle, 0 up, 1 shut. Opening the screen shows it as it is (D44); only Start (shuts) and Stop (lifts) move it.
    // On Protected it waits a beat so the open lock is seen first, then drops with a small spring: the click.
    val locked = page == Page.PROTECTED
    val shut = remember { Animatable(if (locked) 1f else 0f) }
    LaunchedEffect(locked, still) {
        val to = if (locked) 1f else 0f
        if (still) return@LaunchedEffect shut.snapTo(to)
        if (locked) delay(140)
        shut.animateTo(to, spring(dampingRatio = .55f, stiffness = 900f))
    }

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

    // From 2x text the content can outgrow the hero: it grows with it (the page scrolls) instead of clipping.
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    Box(modifier.fillMaxWidth().heightIn(min = hero).clipToBounds().background(bg).onPlaced { hero0[0] = it }) {
        // Two layers: tiles and base outlines never read the clock, so Compose replays them; only the moving overlay redraws per frame.
        Canvas(Modifier.matchParentSize()) { drawGrid(page, tile, 0L, waveAt, still, listOf(nameAt, textAt, gearAt), animated = false) }
        Canvas(Modifier.matchParentSize()) {
            drawGrid(page, tile, now, waveAt, still, listOf(nameAt, textAt, gearAt), animated = true)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = .5f)), startY = size.height * .68f, endY = size.height))
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().cutoutPadding().padding(start = 22.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), Modifier.onGloballyPositioned { nameAt = at(it) }, style = MaterialTheme.typography.titleMedium, color = Text0)
            }
            Box(Modifier.onGloballyPositioned { gearAt = at(it) }) { gear() }
        }
        // Badge and headline as one group, centred in what the top bar and the sheet's lip leave.
        Column(
            Modifier.fillMaxWidth().heightIn(min = hero).statusBarsPadding().cutoutPadding()
                .padding(start = 22.dp, end = 22.dp, top = (24 * fontScale + 24).dp, bottom = 20.dp), // below the top bar, which grows with the text
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Badge(badgeColor, bg, icon, spin = page == Page.STARTING, shut = { shut.value })
            Column(Modifier.padding(top = 4.dp).onGloballyPositioned { textAt = at(it) }, horizontalAlignment = Alignment.CenterHorizontally) {
                CappedFontScale(1.3f) { Text(title, Modifier.asHeading(), style = MaterialTheme.typography.displaySmall, color = Text0, textAlign = TextAlign.Center) }
                Text(text, Modifier.padding(top = 8.dp).widthIn(max = 300.dp), style = MaterialTheme.typography.bodyMedium, color = Text1, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 96 dp disc in the state colour (tint, 1.5 dp ring, soft outer halo). */
@Composable
private fun Badge(color: Color, bg: Color, @DrawableRes icon: Int?, spin: Boolean, shut: () -> Float) {
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
        else if (icon != null) SIcon(icon, color, 52.dp)
        else Canvas(Modifier.size(52.dp)) { drawPadlock(color, shut()) }
    }
}

/** The launcher padlock's body and keyhole (ic_launcher_foreground), in its 108-unit viewport. */
private val LockBody: Path by lazy {
    PathParser().parsePathString(
        "M35,38H73A7,7 0,0 1,80 45V83A7,7 0,0 1,73 90H35A7,7 0,0 1,28 83V45A7,7 0,0 1,35 38Z" +
            "M49.5,53H58.5A2.5,2.5 0,0 1,61 55.5V64.5A2.5,2.5 0,0 1,58.5 67H56.5V73A2,2 0,0 1,54.5 75H53.5A2,2 0,0 1,51.5 73V67H49.5A2.5,2.5 0,0 1,47 64.5V55.5A2.5,2.5 0,0 1,49.5 53Z",
    ).toPath().apply { fillType = PathFillType.EvenOdd }
}

/** The launcher padlock, tilted 15°, with the shackle at [shut] (0 up, 1 shut; the spring may overshoot a little). */
private fun DrawScope.drawPadlock(color: Color, shut: Float) {
    val k = size.minDimension / 108f
    val pivot = Offset(54f, 60f)
    withTransform({
        scale(k, k, Offset.Zero)
        translate(0f, 2f)
        scale(1.1f, 1.1f, pivot)
        rotate(15f, pivot)
    }) {
        val y = 33f - 9f * (1f - shut) // the arc's centre: 24 up, 33 shut
        val shackle = Path().apply {
            moveTo(68f, 44f)
            lineTo(68f, y)
            arcTo(Rect(40f, y - 14f, 68f, y + 14f), 0f, -180f, false)
            lineTo(40f, y + 3f + 8f * shut) // the left leg: clear of the body when up, inside it when shut
        }
        drawPath(shackle, color, style = Stroke(8f, cap = StrokeCap.Round))
        drawPath(LockBody, color)
    }
}

internal fun DrawScope.drawGrid(page: Page, tile: Color, now: Long, waveAt: Long, still: Boolean, texts: List<Rect>, animated: Boolean? = null) {
    val st = animated != true // static layer: tiles, base outlines, frozen states
    val dy = animated != false // moving layer: wave, band, glows, travelling outline
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
        if (st) drawRoundRect(tile, at, box, corner)
        // Tiles under the name, gear and text: no camo, dimmed outlines (still outlined, so no holes).
        val quiet = texts.any { under(it, tileAt) }
        val dim = if (quiet) .35f else 1f
        val edge = Stroke(1.dp.toPx())
        if (st && page != Page.READY && page != Page.STARTING) {
            drawRoundRect(Edge.copy(alpha = .1f * (if (quiet) .5f else 1f)), at, box, corner, style = edge)
        }
        when (page) {
            Page.PROTECTED -> {
                val w = tt * 3.4f - hypot(q + .5f - COLS / 2f, r + .5f - (size.height / t + .3f))
                if (dy && !quiet && waving && w > 0f && w < 1.6f) {
                    drawRoundRect(Palette.GreenDeep.copy(alpha = .5f * sin(PI.toFloat() * w / 1.6f)), at, box, corner)
                    drawRoundRect(CamoBrush, at, box, corner)
                }
                if (dy && ambient) {
                    val k = (1f - abs(q + r - bandAt) / 2.5f).coerceAtLeast(0f)
                    if (k > 0f) drawRoundRect(Palette.Mint.copy(alpha = .07f * k * k * (3 - 2 * k) * dim), at, box, corner)
                    // A breathing tile wears the mask's camo, as if Sophiel just covered it.
                    val g = if (quiet) 0f else glows(q, r)
                    if (g > 0f) {
                        drawRoundRect(Palette.GreenDeep.copy(alpha = .35f * g), at, box, corner)
                        drawRoundRect(CamoBrush, at, box, corner, alpha = g)
                    }
                }
            }
            Page.INTERRUPTED -> if (!st) Unit else if (!quiet && q to r in RedTiles) {
                drawRoundRect(Palette.MaskRed, at, box, corner)
                val c = Offset(x + s / 2, y + s / 2)
                val k = 7.dp.toPx()
                val ink = Palette.RedInk
                val line = 2.dp.toPx()
                drawLine(ink, c - Offset(k, k), c + Offset(k, k), line, StrokeCap.Round)
                drawLine(ink, c + Offset(k, -k), c + Offset(-k, k), line, StrokeCap.Round)
            } else if (!quiet && (q * 7 + r * 3) % 5 == 0) drawRoundRect(Palette.Speck, at, box, corner)
            Page.READY -> {
                // The armed outlines catch the band as it passes.
                if (st) drawRoundRect(Edge.copy(alpha = .22f * dim), at, box, corner, style = Stroke(1.2.dp.toPx()))
                val k = if (dy && ambient) (1f - abs(q + r - bandAt) / 2.5f).coerceAtLeast(0f) else 0f
                if (k > 0f) { // on top of the base: 1 - (1 - .22d)(1 - x) = (.22 + .3s)d
                    val x = .3f * k * k * (3 - 2 * k) * dim / (1f - .22f * dim)
                    drawRoundRect(Edge.copy(alpha = x), at, box, corner, style = Stroke(1.2.dp.toPx()))
                }
            }
            Page.STARTING -> {
                val pos = (r * COLS + q) / (rows * COLS).toFloat() * 2.4f
                val a = if (dy) max(0f, 1f - abs((phase - pos + 2.4f) % 2.4f) / .5f) else 0f
                if (st) drawRoundRect(Edge.copy(alpha = .15f * dim), at, box, corner, style = Stroke(1.2.dp.toPx()))
                if (a > 0f) drawRoundRect(Edge.copy(alpha = .7f * a * dim / (1f - .15f * dim)), at, box, corner, style = Stroke((1.2f + a).dp.toPx()))
            }
            Page.STOPPED -> if (!st) Unit else if (!quiet && q to r in OrangeTiles) {
                drawRoundRect(Palette.MaskAmber, at, box, corner)
                val c = Offset(x + s / 2, y + s / 2)
                val ink = Palette.AmberInk
                drawLine(ink, c - Offset(0f, 9.dp.toPx()), c + Offset(0f, 2.dp.toPx()), 2.4.dp.toPx(), StrokeCap.Round)
                drawCircle(ink, 1.4.dp.toPx(), c + Offset(0f, 9.dp.toPx()))
            } else if (!quiet && (q * 7 + r * 3) % 5 == 0) drawRoundRect(Palette.Speck, at, box, corner)
            Page.OFF -> Unit
        }
    }
}
