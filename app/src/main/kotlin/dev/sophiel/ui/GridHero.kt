package dev.sophiel.ui

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
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
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.sophiel.ui.theme.Ease
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.coerceIn
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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

// The Status hero (D75): a grid of tiles (the screen the app watches), a state badge and the headline.

private const val COLS = 7
internal val Moss = Palette.Moss
internal val Text0 = Palette.Text0
internal val Text1 = Palette.Text1

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

private val RedTiles = setOf(0 to 1, 1 to 2, 4 to 3)
private val OrangeTiles = setOf(0 to 1, 1 to 2, 4 to 3)

/** Set once the first hero of this process has been drawn: the welcome plays on a cold open only, never on coming back to Status. */
private var welcomed = false

/** Headlines are large text already: past [max] they stop growing, or one word no longer fits the line. */
@Composable
internal fun CappedFontScale(max: Float, content: @Composable () -> Unit) {
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale.coerceAtMost(max)),
        content = content,
    )
}

/**
 * D75: the state card, a 28 dp slab holding the tile wall (the screen the app watches), the state's badge, the headline and, on
 * Protected, the capture mode (D54) as one quiet line. [covered]: today's masking episodes; on Ready and Protected that many tiles wear the mask.
 * [fit] stretches the card (the wizard); otherwise it is 318 dp or what its text needs.
 */
@Composable
fun GridHero(
    page: Page, title: String, text: String, modifier: Modifier = Modifier, fit: Dp? = null, caution: Boolean = false, covered: Int = 0,
    @DrawableRes modeIcon: Int? = null, mode: String? = null,
) {
    val still = reducedMotion()
    val bg by animateColorAsState(
        when (page) { Page.INTERRUPTED -> Palette.HeroRed; Page.STOPPED -> Palette.HeroAmber; else -> Moss }, motion(tween(300)), label = "bg",
    )
    val tile by animateColorAsState(
        when (page) {
            Page.OFF -> Palette.HeroOff; Page.INTERRUPTED -> Palette.TileRed; Page.STOPPED -> Palette.TileAmber; else -> Palette.TileOn
        },
        motion(tween(300)), label = "tile",
    )
    val badge = when (page) {
        Page.PROTECTED -> Palette.Mint
        Page.INTERRUPTED -> Palette.RedInk
        Page.STOPPED -> Palette.AmberInk
        Page.READY -> if (caution) Palette.AmberInk else Palette.MintDim // the green held back; amber while a recommended check is off
        else -> Text1
    }
    val badgeColor by animateColorAsState(badge, motion(tween(300)), label = "badge")
    // The shackle, 0 up, 1 shut. Opening the screen shows it as it is (D44); only Start (shuts) and Stop (lifts) move it.
    // On Protected it waits a beat so the open lock is seen first, then drops: the click.
    val locked = page == Page.PROTECTED
    // Welcome: on the app's first open the badge arrives and, when protected, the lock starts open and falls shut (no haptic: it is not an event).
    val welcome = remember { (!welcomed && !still).also { welcomed = true } }
    val arrive = remember { Animatable(if (welcome) 0f else 1f) }
    val shut = remember { Animatable(if (locked && !welcome) 1f else 0f) }
    // Weight: the shackle falls under gravity (accelerating, no bounce) and stops hard; the body takes the hit,
    // squashing and springing back, a ring leaves it, and the phone thuds once.
    val press = remember { Animatable(1f) }
    val ring = remember { Animatable(1f) } // 0 at impact, 1 spent
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(locked, still) {
        val to = if (locked) 1f else 0f
        if (still) return@LaunchedEffect shut.snapTo(to)
        if (!locked) { shut.animateTo(0f, spring(dampingRatio = .55f, stiffness = 900f)); return@LaunchedEffect }
        if (shut.value >= 1f) return@LaunchedEffect // opened already shut: nothing falls
        delay(if (welcome) 380 else 140)
        shut.animateTo(1f, tween(130, easing = FastOutLinearInEasing))
        if (!welcome) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { ring.snapTo(0f); ring.animateTo(1f, tween(620, easing = Ease)) }
        press.snapTo(.9f)
        press.animateTo(1f, spring(dampingRatio = .42f, stiffness = 420f))
    }

    LaunchedEffect(Unit) {
        if (!welcome) return@LaunchedEffect
        if (!locked) launch { ring.snapTo(0f); ring.animateTo(1f, tween(620, easing = Ease)) } // protected has its own ring, at the click
        arrive.animateTo(1f, spring(dampingRatio = .6f, stiffness = 300f))
    }

    // One clock, only while something moves: the scan, the breathing tiles and the entry wave (protected), the travelling light (starting). Ready stays still.
    var now by remember { mutableLongStateOf(0L) }
    var waveAt by remember { mutableLongStateOf(-100_000L) }
    LaunchedEffect(page, still) {
        if (still || (page != Page.PROTECTED && page != Page.STARTING)) return@LaunchedEffect
        var first = true
        while (true) {
            withFrameMillis {
                now = it
                if (first && page == Page.PROTECTED) waveAt = it
                first = false
            }
        }
    }

    // Where the badge and the text sit, in card coordinates (stable while scrolling): tiles under them stay quiet.
    val card = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var textAt by remember { mutableStateOf(Rect.Zero) }
    var badgeAt by remember { mutableStateOf(Rect.Zero) }
    fun at(c: LayoutCoordinates) = card[0]?.localBoundingBoxOf(c) ?: Rect.Zero
    val quiet = listOf(textAt, badgeAt)

    // From 2x text the content can outgrow the card: it grows with it (the page scrolls) instead of clipping.
    Box(modifier.fillMaxWidth().heightIn(min = fit ?: 318.dp).clip(RoundedCornerShape(28.dp)).background(bg).onPlaced { card[0] = it }) {
        // Two layers: tiles never read the clock, so Compose replays them; only the moving overlay redraws per frame.
        Canvas(Modifier.matchParentSize()) { drawGrid(page, tile, 0L, waveAt, still, quiet, animated = false, covered = covered, ink = bg) }
        Canvas(Modifier.matchParentSize()) {
            drawGrid(page, tile, now, waveAt, still, quiet, animated = true, ink = bg)
            // The wall fades into the card's ink from a third down, so the text sits on calm ground.
            drawRect(Brush.verticalGradient(.35f to Color.Transparent, .88f to bg.copy(alpha = .92f)))
        }
        Box(Modifier.align(Alignment.TopEnd).padding(16.dp).onGloballyPositioned { badgeAt = at(it) }) {
            Badge(page, badgeColor, bg, shut = { shut.value }, press = { press.value * (.8f + .2f * arrive.value) }, ring = { ring.value }, arrive = { arrive.value })
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 110.dp, bottom = 20.dp)) {
            Column(Modifier.onGloballyPositioned { textAt = at(it) }) {
                CappedFontScale(1.3f) {
                    Text(
                        title, Modifier.asHeading(), color = Text0,
                        style = MaterialTheme.typography.displaySmall.let { if (page == Page.INTERRUPTED) it.copy(fontSize = 42.sp, lineHeight = 44.sp) else it }
                            .copy(lineBreak = LineBreak.Heading),
                    )
                }
                Text(
                    text, Modifier.padding(top = 8.dp).widthIn(max = 290.dp), color = Text1,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp, lineBreak = LineBreak.Heading),
                )
                if (mode != null) Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (modeIcon != null) SIcon(modeIcon, Text1, 15.dp)
                    Text(mode, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = Text1)
                }
            }
        }
    }
}

/** The state's badge, 78 dp: a shield while the shield is up or armed, a rounded square once it is not (the shape says it too). */
@Composable
private fun Badge(page: Page, color: Color, ink: Color, shut: () -> Float, press: () -> Float, ring: () -> Float, arrive: () -> Float) {
    val cookie = page == Page.PROTECTED || page == Page.READY || page == Page.STARTING
    Box(Modifier.size(78.dp).graphicsLayer { scaleX = press(); scaleY = press(); alpha = arrive().coerceIn(0f, 1f) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.requiredSize(78.dp)) {
            val e = ring()
            val r = size.minDimension / 2
            if (e < 1f) drawCircle(color.copy(alpha = .45f * (1f - e)), r + 4.dp.toPx() + 22.dp.toPx() * e, style = Stroke(2.dp.toPx()))
            if (cookie) drawPath(shieldPath(size.minDimension), color)
            else drawRoundRect(color, cornerRadius = CornerRadius(size.minDimension * .24f))
        }
        when (page) {
            Page.STARTING -> CircularProgressIndicator(Modifier.size(32.dp), color = ink, trackColor = ink.copy(alpha = .25f), strokeWidth = 3.dp)
            Page.OFF -> SIcon(R.drawable.ic_power, ink, 34.dp)
            Page.STOPPED -> Canvas(Modifier.size(32.dp)) { // the pause: its own shape, not a lock
                val w = size.width * .2f
                val h = size.height * .56f
                val y = (size.height - h) / 2
                drawRoundRect(ink, Offset(size.width * .22f, y), Size(w, h), CornerRadius(w / 3))
                drawRoundRect(ink, Offset(size.width * .78f - w, y), Size(w, h), CornerRadius(w / 3))
            }
            else -> Canvas(Modifier.size(40.dp)) { drawPadlock(ink, shut(), tilt = 0f) }
        }
    }
}

/** The guardian's shield in a 100-unit box, scaled to [side]. */
internal fun shieldPath(side: Float): Path =
    PathParser().parsePathString("M50,5C63,14 76,18 90,18V48C90,71 73,87 50,96C27,87 10,71 10,48V18C24,18 37,14 50,5Z").toPath().apply {
        transform(androidx.compose.ui.graphics.Matrix().apply { scale(side / 100f, side / 100f) })
    }

/** A cookie: radius 44 + 5 cos(lobes * angle) in a 100-unit box, scaled to [side]. */
internal fun cookiePath(side: Float, lobes: Int): Path = Path().apply {
    val k = side / 100f
    for (d in 0..360) {
        val a = d * PI.toFloat() / 180f
        val r = 44f + 5f * kotlin.math.cos(lobes * a)
        val x = (50f + r * kotlin.math.cos(a)) * k
        val y = (50f + r * sin(a)) * k
        if (d == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The launcher padlock's body and keyhole (ic_launcher_foreground), in its 108-unit viewport. */
private val LockBody: Path by lazy {
    PathParser().parsePathString(
        "M35,38H73A7,7 0,0 1,80 45V83A7,7 0,0 1,73 90H35A7,7 0,0 1,28 83V45A7,7 0,0 1,35 38Z" +
            "M49.5,53H58.5A2.5,2.5 0,0 1,61 55.5V64.5A2.5,2.5 0,0 1,58.5 67H56.5V73A2,2 0,0 1,54.5 75H53.5A2,2 0,0 1,51.5 73V67H49.5A2.5,2.5 0,0 1,47 64.5V55.5A2.5,2.5 0,0 1,49.5 53Z",
    ).toPath().apply { fillType = PathFillType.EvenOdd }
}

/** The launcher padlock, tilted [tilt]°, with the shackle at [shut] (0 up, 1 shut; the spring may overshoot a little). */
internal fun DrawScope.drawPadlock(color: Color, shut: Float, tilt: Float = 15f) {
    val k = size.minDimension / 108f
    val pivot = Offset(54f, 60f)
    withTransform({
        scale(k, k, Offset.Zero)
        translate(0f, 2f)
        scale(1.1f, 1.1f, pivot)
        rotate(tilt, pivot)
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

internal fun DrawScope.drawGrid(
    page: Page, tile: Color, now: Long, waveAt: Long, still: Boolean, texts: List<Rect>, animated: Boolean? = null, covered: Int = 0, ink: Color = Palette.Moss,
) {
    val st = animated != true // static layer: tiles, frozen states
    val dy = animated != false // moving layer: wave, band, breathing tiles, travelling light
    val t = size.width / COLS
    val rows = ceil(size.height / t).toInt()
    val gap = 2.5.dp.toPx()
    val s = t - 2 * gap
    val corner = CornerRadius(10.dp.toPx())
    val pad = 4.dp.toPx()
    fun under(r: Rect, tileAt: Rect) = !r.isEmpty && r.inflate(pad).overlaps(tileAt)
    val tt = (now - waveAt) / 1000f
    val waving = !still && page == Page.PROTECTED && tt in 0f..4f
    // Ambient life on Protected only (Ready waits, interrupted/stopped stay frozen: the stillness says something): a scan band that sweeps
    // the wall top to bottom every 6 s, as Sophiel reads the screen, and three tiles that breathe out of step, each on its own period.
    val ambient = !still && page == Page.PROTECTED
    val bandAt = ((now / 6_000f) % 1f) * (rows + 4) - 2
    fun tileRect(k: Int) = Rect(Offset((k % COLS) * t + gap, (k / COLS) * t + gap), Size(s, s))
    // Today's covers, one tile each, at fixed places (a hash order of the tiles not under text), so the wall fills as the day does.
    val worn = if (covered <= 0 || (page != Page.PROTECTED && page != Page.READY)) emptySet() else {
        val open = (0 until rows * COLS).filter { k -> texts.none { under(it, tileRect(k)) } }
        open.sortedBy { k -> (k * 2654435761L) and 0xFFFFFFFFL }.take(covered).toSet()
    }
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
        val k = r * COLS + q
        // Tiles under the badge and the text sit back: no camo, no marks.
        val quiet = texts.any { under(it, Rect(at, box)) }
        if (st) drawRoundRect(if (quiet) lerp(tile, ink, .3f) else tile, at, box, corner)
        if (st && k in worn) { // a cover today: the mask's camo, lit, with a mint edge, so it reads as data at a glance
            drawRoundRect(Palette.GreenDeep, at, box, corner)
            drawRoundRect(CamoBrush, at, box, corner)
            drawRoundRect(Palette.Mint.copy(alpha = .7f), at, box, corner, style = Stroke(1.5.dp.toPx()))
        }
        if (quiet || k in worn) continue
        val c = Offset(x + s / 2, y + s / 2)
        when (page) {
            Page.PROTECTED -> {
                val w = tt * 3.4f - hypot(q + .5f - COLS / 2f, r + .5f - (size.height / t + .3f))
                val wave = if (dy && waving && w > 0f && w < 1.6f) sin(PI.toFloat() * w / 1.6f) else 0f
                val g = if (dy && ambient) glows(q, r) else 0f
                val camo = max(wave, g)
                if (camo > .02f) {
                    drawRoundRect(Palette.GreenDeep, at, box, corner, alpha = camo)
                    drawRoundRect(CamoBrush, at, box, corner, alpha = camo)
                } else if (dy && ambient) {
                    val b = (1f - abs(r - bandAt) / 1.2f).coerceAtLeast(0f)
                    if (b > 0f) drawRoundRect(Palette.Mint.copy(alpha = .22f * b * b * (3 - 2 * b)), at, box, corner)
                }
            }
            Page.INTERRUPTED -> if (st && q to r in RedTiles) {
                drawRoundRect(Color(0xFF4A2125), at, box, corner)
                val m = 7.dp.toPx()
                val line = 2.5.dp.toPx()
                drawLine(Palette.RedInk, c - Offset(m, m), c + Offset(m, m), line, StrokeCap.Round)
                drawLine(Palette.RedInk, c + Offset(m, -m), c + Offset(-m, m), line, StrokeCap.Round)
            }
            Page.STOPPED -> if (st && q to r in OrangeTiles) {
                drawRoundRect(Color(0xFF4A3416), at, box, corner)
                for (dx in listOf(-3.5.dp.toPx(), 3.5.dp.toPx())) {
                    drawLine(Palette.AmberInk, c + Offset(dx, -6.dp.toPx()), c + Offset(dx, 6.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                }
            }
            Page.STARTING -> {
                val pos = k / (rows * COLS).toFloat() * 2.4f
                val a = if (dy) max(0f, 1f - abs((phase - pos + 2.4f) % 2.4f) / .5f) else 0f
                if (a > 0f) drawRoundRect(Palette.Mint.copy(alpha = .3f * a), at, box, corner)
            }
            Page.READY, Page.OFF -> Unit
        }
    }
}

/** A cookie-shaped clip (the Apps tile's badge): [lobes] bumps around a circle. */
internal class CookieShape(private val lobes: Int) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
        androidx.compose.ui.graphics.Outline.Generic(cookiePath(size.minDimension, lobes))
}
