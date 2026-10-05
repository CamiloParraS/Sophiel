package dev.sophiel.feed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sophiel.capture.DebugMask

// Ticket 02 of .scratch/m5-overlay: throwaway comparison of mask looks at overlay-window alpha.
// Window alpha multiplies the whole window, so Modifier.alpha over the image reproduces the
// 0.8 touch-through cap's show-through. It does not test touches, only the look.

private const val COLS = 2
private const val ROWS = 3
private const val FRAME_W = 360
private const val FRAME_H = 744
private val LOOKS = listOf("Flat + lock", "Lock pattern", "Noise", "Pixelate", "Blur", "Camo")
private const val CAMO_LOOK = 5
private val MASK_COLOR = Color(0xFF9C27B0) // never pure black (BlackFrameDetector)

/** The fixture centre-cropped to the 360x744 capture shape, so tile maths is plain bitmap maths. */
private fun frame(context: android.content.Context, name: String): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open("$ASSET_DIR/$name").use { BitmapFactory.decodeStream(it, null, bounds) }
    val sample = maxOf(1, minOf(bounds.outWidth / FRAME_W, bounds.outHeight / FRAME_H))
    val src = context.assets.open("$ASSET_DIR/$name").use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })!!
    }
    val scale = maxOf(FRAME_W / src.width.toFloat(), FRAME_H / src.height.toFloat())
    val w = (FRAME_W / scale).toInt().coerceAtMost(src.width)
    val h = (FRAME_H / scale).toInt().coerceAtMost(src.height)
    val crop = Bitmap.createBitmap(src, (src.width - w) / 2, (src.height - h) / 2, w, h)
    return Bitmap.createScaledBitmap(crop, FRAME_W, FRAME_H, true)
}

@Composable
fun maskLookScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val names = remember { context.assets.list(ASSET_DIR)!!.filter { it.endsWith(".png") }.sorted() }
    var image by remember { mutableIntStateOf(0) }
    var look by remember { mutableIntStateOf(0) }
    var alpha by remember { mutableFloatStateOf(0.79f) }
    var blocks by remember { mutableIntStateOf(5) } // pixelate: blocks across a tile
    var original by remember { mutableStateOf(false) }
    var period by remember { mutableIntStateOf(DebugMask.PERIOD) } // camo: largest blob period, px
    var brightness by remember { mutableIntStateOf(DebugMask.BRIGHTNESS) } // camo: mean brightness before the tint
    var tint by remember { mutableStateOf(DebugMask.TINT_NAME) }
    // Drawn 1:1 in device pixels, like the shipped OverlayController shader.
    val camo = remember(period, brightness, tint) { DebugMask.camo(period, brightness, DebugMask.TINTS.getValue(tint)) }
    val masked = remember { mutableStateListOf(*Array(COLS * ROWS) { it in 2..3 }) }
    val bitmap = remember(image) { frame(context, names[image]) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("Mask looks at window alpha (ticket 02). Tap a tile to toggle its mask.", fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ image = (image - 1 + names.size) % names.size }) { Text("<") }
            Text("${names[image]}  (${image + 1}/${names.size})", fontSize = 13.sp)
            TextButton({ image = (image + 1) % names.size }) { Text(">") }
        }
        LOOKS.indices.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { i -> FilterChip(look == i, { look = i }, { Text(LOOKS[i], fontSize = 12.sp) }) }
            }
        }
        Text("Window alpha %.2f (0.80 = the touch-through cap)".format(alpha), fontSize = 13.sp)
        Slider(alpha, { alpha = it }, valueRange = 0.5f..1f)
        if (look == 3) {
            Text("Pixelate: $blocks blocks across a tile", fontSize = 13.sp)
            Slider(blocks.toFloat(), { blocks = it.toInt() }, valueRange = 2f..16f)
        }
        if (look == CAMO_LOOK) {
            Text("Camo: blobs up to $period px", fontSize = 13.sp)
            Slider(Integer.numberOfTrailingZeros(period).toFloat(), { period = 1 shl it.toInt() }, valueRange = 3f..6f, steps = 2)
            Text("Camo: brightness $brightness, ${describeMean(camo)} (shipped: ${describeMean(DebugMask.PATTERN)})", fontSize = 13.sp)
            Slider(brightness.toFloat(), { brightness = it.toInt() }, valueRange = 100f..230f, steps = 12)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DebugMask.TINTS.keys.forEach { t -> FilterChip(tint == t, { tint = t }, { Text(t, fontSize = 12.sp) }) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(original, { original = it })
            Text("  Show original", fontSize = 13.sp)
        }

        realWindowControls(alpha, camo)

        Box(Modifier.align(Alignment.CenterHorizontally).height(480.dp).aspectRatio(FRAME_W / FRAME_H.toFloat())) {
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            if (!original) Column(Modifier.fillMaxSize()) {
                repeat(ROWS) { r ->
                    Row(Modifier.weight(1f)) {
                        repeat(COLS) { c ->
                            val i = r * COLS + c
                            Box(Modifier.weight(1f).fillMaxSize().clickable { masked[i] = !masked[i] }) {
                                if (masked[i]) Box(Modifier.fillMaxSize().alpha(alpha)) {
                                    tileMask(look, tileCrop(bitmap, c, r), blocks, camo)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun tileCrop(frame: Bitmap, c: Int, r: Int): Bitmap =
    Bitmap.createBitmap(frame, c * FRAME_W / COLS, r * FRAME_H / ROWS, FRAME_W / COLS, FRAME_H / ROWS)

@Composable
private fun tileMask(look: Int, crop: Bitmap, blocks: Int, camo: IntArray) {
    when (look) {
        3 -> { // from our own captured tile, scaled down then up without smoothing
            val small = remember(crop, blocks) {
                Bitmap.createScaledBitmap(crop, blocks, (blocks * crop.height / crop.width.toFloat()).toInt().coerceAtLeast(1), true)
            }
            Image(small.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
        }
        4 -> Image(crop.asImageBitmap(), null, Modifier.fillMaxSize().clipToBounds().blur(24.dp), contentScale = ContentScale.FillBounds)
        CAMO_LOOK -> { // 1:1 in device pixels: stretching it would preview coarser grain than ships
            val paint = remember(camo) { tiledPaint(camo) }
            Canvas(Modifier.fillMaxSize()) { drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) } }
        }
        else -> Box(Modifier.fillMaxSize().background(MASK_COLOR), contentAlignment = Alignment.Center) {
            if (look == 2) noise()
            if (look == 1) lockPattern()
            if (look != 1) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔒", fontSize = 28.sp)
                Text("Hidden by Sophiel", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun noise() {
    val bmp = remember {
        val rnd = java.util.Random(7)
        Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { b ->
            for (x in 0 until 48) for (y in 0 until 48) {
                val v = 90 + rnd.nextInt(110)
                b.setPixel(x, y, android.graphics.Color.rgb(v, v / 3, v))
            }
        }.asImageBitmap()
    }
    Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
}

@Composable
private fun lockPattern() {
    Canvas(Modifier.fillMaxSize()) {
        val paint = android.graphics.Paint().apply { textSize = 34f; color = android.graphics.Color.WHITE; alpha = 170 }
        drawIntoCanvas { canvas ->
            var y = 40f
            var row = 0
            while (y < size.height + 40f) {
                var x = if (row % 2 == 0) 0f else 30f
                while (x < size.width) { canvas.nativeCanvas.drawText("🔒", x, y, paint); x += 60f }
                y += 52f; row++
            }
        }
    }
}

private fun tiledPaint(pattern: IntArray) = android.graphics.Paint().apply {
    shader = android.graphics.BitmapShader(
        DebugMask.patternBitmap(pattern), android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT,
    )
}

private fun describeMean(px: IntArray): String {
    fun m(shift: Int) = px.sumOf { it shr shift and 0xFF } / px.size
    val (r, g, b) = Triple(m(16), m(8), m(0))
    return "mean #%02x%02x%02x luma %d".format(r, g, b, (0.299 * r + 0.587 * g + 0.114 * b).toInt())
}

/** A real touch-through overlay band (middle third of the screen): the pattern 1:1, optionally over blur-behind. */
private class RealMaskWindow(private val context: android.content.Context) {
    private val wm = context.getSystemService(android.view.WindowManager::class.java)
    private var view: android.view.View? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoHide = Runnable { hide() }

    val blurSupported get() = android.os.Build.VERSION.SDK_INT >= 31 && wm.isCrossWindowBlurEnabled

    fun show(alpha: Float, blur: Boolean, pattern: IntArray) {
        hide()
        val metrics = context.resources.displayMetrics
        val v = android.view.View(context).apply {
            background = android.graphics.drawable.BitmapDrawable(context.resources, DebugMask.patternBitmap(pattern)).apply {
                isFilterBitmap = false
                setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
            }
        }
        val lp = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            metrics.heightPixels / 3,
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.CENTER
            this.alpha = alpha.coerceAtMost(0.79f) // above 0.8 every touch is blocked (ticket 01)
            if (blur && android.os.Build.VERSION.SDK_INT >= 31) {
                flags = flags or android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = 60
            }
        }
        wm.addView(v, lp)
        view = v
        handler.postDelayed(autoHide, 60_000) // safety net: never leave a debug window behind
    }

    fun hide() {
        handler.removeCallbacks(autoHide)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}

@Composable
private fun realWindowControls(alpha: Float, pattern: IntArray) {
    val context = LocalContext.current
    val window = remember { RealMaskWindow(context.applicationContext) }
    var on by remember { mutableStateOf(false) }
    var blur by remember { mutableStateOf(true) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { window.hide() } }
    androidx.compose.runtime.LaunchedEffect(pattern) { if (on) window.show(alpha, blur, pattern) }
    if (!android.provider.Settings.canDrawOverlays(context)) {
        Text("Real window needs the overlay permission (Protection tab).", fontSize = 13.sp)
        return
    }
    Text("Real overlay window: middle third of the screen, the camo as set above, 1:1 pixels, touch-through. " +
        "Switch on, then open a gallery to see it over real content. Auto-hides after 60 s.", fontSize = 13.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(on, { on = it; if (it) window.show(alpha, blur, pattern) else window.hide() })
        Text("  Real window   ", fontSize = 13.sp)
        Switch(blur, { blur = it; if (on) window.show(alpha, it, pattern) })
        Text("  Blur behind (${if (window.blurSupported) "supported" else "NOT enabled here"})", fontSize = 13.sp)
    }
}
