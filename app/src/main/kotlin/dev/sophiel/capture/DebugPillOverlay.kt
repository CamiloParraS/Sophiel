package dev.sophiel.capture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import dev.sophiel.feed.Detection

/**
 * Debug-only overlay pill showing the live verdict, so a human can see what M3's capture
 * pipeline is doing without reading Logcat. Human-approved exception to SPEC.md's M3/M4
 * separation (DECISIONS.md D15/D18) — not the M4 masking overlay, and not part of §1.2 scope,
 * so it's gated on [Context.isDebuggable] and never shown otherwise.
 *
 * Plain [TextView] on a raw [WindowManager], per D4 — no Compose in an overlay window.
 */
class DebugPillOverlay(private val context: Context) {
    // ProjectionService updates this from a background coroutine (Dispatchers.Default) on
    // every frame; View mutations must happen on the main thread or they crash with
    // CalledFromWrongThreadException — caught on-device (2026-09-14, Device B).
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val pill = TextView(context).apply {
        setTextColor(Color.WHITE)
        setBackgroundColor(0xAA1B1B1B.toInt())
        setPadding(24, 12, 24, 12)
        textSize = 12f
    }
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = 16
        y = 120
    }
    private var attached = false

    fun show() = mainHandler.post {
        if (attached || !Settings.canDrawOverlays(context)) return@post
        windowManager.addView(pill, params)
        attached = true
    }

    fun update(text: String) = mainHandler.post { pill.text = text }

    fun hide() = mainHandler.post {
        if (!attached) return@post
        windowManager.removeView(pill)
        attached = false
    }
}

/** True for a debug-signed build. Gates [DebugPillOverlay] out of any hypothetical release build. */
val Context.isDebuggable: Boolean
    get() = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

/**
 * Debug-only spike (D24): NudeNet boxes drawn live over the screen. Outlines only, so the
 * next captured frame sees thin lines rather than covered content. Touch-transparent.
 */
class DebugBoxOverlay(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var boxes: List<Detection> = emptyList()

    private val view = object : View(context) {
        private val stroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f }
        private val text = Paint().apply { textSize = 30f; isAntiAlias = true; isFakeBoldText = true }
        private val origin = IntArray(2)
        private val real = DisplayMetrics()

        override fun onDraw(canvas: Canvas) {
            // Boxes are fractions of the whole display; this window may not start at (0,0).
            getLocationOnScreen(origin)
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(real)
            for (d in boxes) {
                stroke.color = if (d.unsafe) Color.RED else Color.YELLOW
                text.color = stroke.color
                val l = d.box.left * real.widthPixels - origin[0]
                val t = d.box.top * real.heightPixels - origin[1]
                canvas.drawRect(l, t, d.box.right * real.widthPixels - origin[0], d.box.bottom * real.heightPixels - origin[1], stroke)
                canvas.drawText("%s %.2f".format(d.label.lowercase(), d.score), l + 6, t + 34, text)
            }
        }
    }

    private val params = WindowManager.LayoutParams(
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
        alpha = 0.8f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }
    private var attached = false

    fun show() = mainHandler.post {
        if (attached || !Settings.canDrawOverlays(context)) return@post
        windowManager.addView(view, params)
        attached = true
    }

    /** [detections] boxes must already be fractions of the whole display, not of the frame. */
    fun update(detections: List<Detection>) = mainHandler.post {
        boxes = detections
        view.invalidate()
    }

    fun hide() = mainHandler.post {
        if (!attached) return@post
        windowManager.removeView(view)
        attached = false
    }
}
