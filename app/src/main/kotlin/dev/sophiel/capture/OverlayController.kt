package dev.sophiel.capture

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import dev.sophiel.core.TileRect
import dev.sophiel.feed.Detection
import kotlin.math.ceil
import kotlin.math.floor

// 0.79, not 0.8: margin under the cap, which the research could not pin to a source line.
internal const val OVERLAY_ALPHA = 0.79f

/**
 * The one full-screen, touch-through overlay window (D28): every tile mask, drawn with
 * [DebugMask]'s camo, plus the spike's NudeNet boxes in debug builds (D24, outlines only).
 *
 * One window on purpose: Android 12+ blocks touches through another app's overlays when their
 * *combined* opacity at a point exceeds 0.8, so two full-screen windows at 0.8 (1 - 0.2²)
 * swallow every touch on the device, even with nothing drawn (seen on-device, 2026-10-02).
 *
 * Plain [View] on a raw [WindowManager], per D4: no Compose in an overlay window.
 */
class OverlayController(private val context: Context, private val showLabel: () -> Boolean) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view = TileMaskView(context, windowManager, showLabel)
    private var host: WindowManager? = null

    fun show() = mainHandler.post {
        MaskWindowService.onChange = { mainHandler.post(::followService) }
        attach()
    }

    // D32: MaskWindowService's trusted window draws the masks opaque. Starting on the 0.79 app
    // overlay without it is a debug-build option; release needs the service (Status gates Start).
    private fun attach(fallback: Boolean = context.isDebuggable) {
        if (host != null) return
        val a11y = MaskWindowService.instance
        if (a11y == null && !(fallback && Settings.canDrawOverlays(context))) {
            Log.w("Sophiel", "mask window: none (accessibility service off)")
            return
        }
        val wm = a11y?.getSystemService(WindowManager::class.java) ?: windowManager
        wm.addView(view, touchThroughOverlayParams(trusted = a11y != null))
        host = wm
        Log.i("Sophiel", "mask window: ${if (a11y != null) "accessibility, alpha 1.0" else "app overlay, alpha $OVERLAY_ALPHA"}")
    }

    /**
     * The service went off or came back mid-session. Off: its window goes with it, so the masks move
     * to a plain overlay in every build, or one toggle would drop every mask. Back on (D42, amends
     * D32): the masks move back to the opaque window, a new view added before the old one is removed
     * (a view lives in one window), so no mask is down in between.
     */
    private fun followService() {
        // Off and back on can both land before this runs: compare against the live service's window
        // manager (one per service context), not just "not ours".
        val a11yWm = MaskWindowService.instance?.getSystemService(WindowManager::class.java)
        when {
            host == null || host === a11yWm -> return // hidden (or nothing to move), or already there
            host !== windowManager -> {
                // A dead service's window: the system may already have removed it with the token.
                runCatching { host?.removeViewImmediate(view) }
                host = null
                attach(fallback = true)
            }
            a11yWm != null -> {
                val old = view
                view = TileMaskView(context, windowManager, showLabel).apply {
                    masks = old.masks
                    cover = old.cover
                    boxes = old.boxes
                    visibility = old.visibility
                }
                host = null
                // Bad token (gone again) or no window at all: put the old view back as the host.
                runCatching { attach() }
                when (host) {
                    null -> { view = old; host = windowManager }
                    else -> windowManager.removeView(old)
                }
            }
        }
    }

    /** [rects] are fractions of the whole display, not of the frame. */
    fun updateMasks(rects: List<RectF>) = mainHandler.post {
        view.masks = rects
        view.invalidate()
    }

    /**
     * Ticket 08: one mask over [rect] (display fractions) in place of the tile masks until
     * [uncover]. Kept apart from [updateMasks] so a publish from a pre-rotation frame still in
     * flight cannot take it down; both run on the main thread, so their order holds.
     */
    fun cover(rect: RectF) = mainHandler.post {
        view.cover = rect
        view.invalidate()
    }

    fun uncover() = mainHandler.post {
        if (view.cover == null) return@post
        view.cover = null
        view.invalidate()
    }

    /**
     * Ticket 09: our own screen is in front. Masks are kept, just not drawn. Showing again redraws,
     * which picks up a show-label change made there (D40).
     */
    fun setHidden(hidden: Boolean) = mainHandler.post {
        view.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    /** Debug builds only. [detections] boxes must already be fractions of the whole display. */
    fun updateBoxes(detections: List<Detection>) = mainHandler.post {
        if (!context.isDebuggable) return@post
        view.boxes = detections
        view.invalidate()
    }

    fun hide() = mainHandler.post {
        MaskWindowService.onChange = null
        if (view.isAttachedToWindow) host?.removeView(view)
        host = null
    }
}

/** Draws [masks] and [boxes]; converts display fractions to pixels at draw time, so rotation needs no recompute. */
private class TileMaskView(context: Context, private val windowManager: WindowManager, private val showLabel: () -> Boolean) : View(context) {
    var masks: List<RectF> = emptyList()
    var cover: RectF? = null // drawn instead of [masks] while set
    var boxes: List<Detection> = emptyList()

    // Shader anchored to the screen, not the window or the tile: the pattern stays put while tiles
    // change (D18), and the mask check knows what it drew at every screen pixel (D30).
    private val shader = BitmapShader(DebugMask.patternBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val noise = Paint().apply { shader = this@TileMaskView.shader }
    private val anchor = Matrix()
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
        anchor.setTranslate(-origin[0].toFloat(), -origin[1].toFloat())
        shader.setLocalMatrix(anchor)
        val labelOn = showLabel() // D28 Parent setting
        for (m in cover?.let(::listOf) ?: masks) {
            val r = maskBounds(m.left, m.top, m.right, m.bottom, w, h, origin[0], origin[1])
            canvas.drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), noise)
            if (labelOn) drawChip(canvas, r)
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
internal fun touchThroughOverlayParams(trusted: Boolean = false) = WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.MATCH_PARENT,
    if (trusted) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    PixelFormat.TRANSLUCENT,
).apply {
    // Android 12+ drops touches passing through another app's overlay above 0.8 opacity
    // (untrusted touch occlusion), even with FLAG_NOT_TOUCHABLE. Accessibility overlays are trusted.
    alpha = if (trusted) 1f else OVERLAY_ALPHA
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }
}
