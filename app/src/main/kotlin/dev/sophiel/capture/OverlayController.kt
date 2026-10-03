package dev.sophiel.capture

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import dev.sophiel.core.TileRect
import dev.sophiel.feed.Detection
import kotlin.math.ceil
import kotlin.math.floor

// 0.79, not 0.8: margin under the cap, which the research could not pin to a source line.
internal const val OVERLAY_ALPHA = 0.79f

// The Parent show-label toggle (D28) is M6; until then the chip is always on.
private const val SHOW_LABEL = true

/**
 * The one full-screen, touch-through overlay window (D28): every tile mask, drawn with
 * [DebugMask]'s noise, plus the spike's NudeNet boxes in debug builds (D24, outlines only).
 *
 * One window on purpose: Android 12+ blocks touches through another app's overlays when their
 * *combined* opacity at a point exceeds 0.8, so two full-screen windows at 0.8 (1 - 0.2²)
 * swallow every touch on the device, even with nothing drawn (seen on-device, 2026-10-02).
 *
 * Plain [View] on a raw [WindowManager], per D4: no Compose in an overlay window.
 */
class OverlayController(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val view = TileMaskView(context, windowManager)
    private var attached = false

    fun show() = mainHandler.post {
        if (attached || !Settings.canDrawOverlays(context)) return@post
        windowManager.addView(view, touchThroughOverlayParams())
        attached = true
    }

    /** [rects] are fractions of the whole display, not of the frame. */
    fun updateMasks(rects: List<RectF>) = mainHandler.post {
        view.masks = rects
        view.invalidate()
    }

    /** Debug builds only. [detections] boxes must already be fractions of the whole display. */
    fun updateBoxes(detections: List<Detection>) = mainHandler.post {
        if (!context.isDebuggable) return@post
        view.boxes = detections
        view.invalidate()
    }

    fun hide() = mainHandler.post {
        if (!attached) return@post
        windowManager.removeView(view)
        attached = false
    }
}

/** Draws [masks] and [boxes]; converts display fractions to pixels at draw time, so rotation needs no recompute. */
private class TileMaskView(context: Context, private val windowManager: WindowManager) : View(context) {
    var masks: List<RectF> = emptyList()
    var boxes: List<Detection> = emptyList()

    // Shader is in screen space (the canvas is not translated), so the grain stays put while
    // tiles change: the mask never redraws differently (D18).
    private val noise = Paint().apply {
        shader = BitmapShader(DebugMask.noiseBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val chip = Paint().apply { color = 0xCC1B1B1B.toInt(); isAntiAlias = true }
    private val label = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
    }
    private val stroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f }
    private val text = Paint().apply { textSize = 30f; isAntiAlias = true; isFakeBoldText = true }
    private val origin = IntArray(2)
    private val real = DisplayMetrics()

    override fun onDraw(canvas: Canvas) {
        // Fractions are of the whole display; this window may not start at (0,0).
        getLocationOnScreen(origin)
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(real)
        val (w, h) = real.widthPixels to real.heightPixels
        for (m in masks) {
            val r = maskBounds(m.left, m.top, m.right, m.bottom, w, h, origin[0], origin[1])
            canvas.drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), noise)
            if (SHOW_LABEL) drawChip(canvas, r)
        }
        for (d in boxes) {
            stroke.color = if (d.unsafe) Color.RED else Color.YELLOW
            text.color = stroke.color
            val l = d.box.left * w - origin[0]
            val t = d.box.top * h - origin[1]
            canvas.drawRect(l, t, d.box.right * w - origin[0], d.box.bottom * h - origin[1], stroke)
            canvas.drawText("%s %.2f".format(d.label.lowercase(), d.score), l + 6, t + 34, text)
        }
    }

    /**
     * Lock + label centred on the mask, or just the lock if that won't fit. Kept inside the
     * middle half of the tile, which the mask check never samples ([DebugMask.samplePoints]).
     */
    private fun drawChip(canvas: Canvas, r: TileRect) {
        val pad = label.textSize / 2
        val h = label.textSize + 2 * pad
        val text = listOf("🔒 Hidden by Sophiel", "🔒").firstOrNull {
            label.measureText(it) + 2 * pad <= r.width / 2f && h <= r.height / 2f
        } ?: return
        val w = label.measureText(text) + 2 * pad
        val cx = (r.left + r.right) / 2f
        val cy = (r.top + r.bottom) / 2f
        canvas.drawRoundRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, h / 2, h / 2, chip)
        canvas.drawText(text, cx, cy - (label.ascent() + label.descent()) / 2, label)
    }
}

/** A display-fraction rect to window pixels, rounded outward: covering slightly more is the safe direction. */
internal fun maskBounds(left: Float, top: Float, right: Float, bottom: Float, w: Int, h: Int, ox: Int, oy: Int) =
    TileRect(
        floor(left * w).toInt() - ox, floor(top * h).toInt() - oy,
        ceil(right * w).toInt() - ox, ceil(bottom * h).toInt() - oy,
    )

/** Full-screen, touch-through overlay window. Keep to one per app: see [OverlayController]. */
internal fun touchThroughOverlayParams() = WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    PixelFormat.TRANSLUCENT,
).apply {
    // Android 12+ drops touches passing through another app's overlay above 0.8 opacity
    // (untrusted touch occlusion), even with FLAG_NOT_TOUCHABLE.
    alpha = OVERLAY_ALPHA
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }
}
