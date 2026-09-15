package dev.sophiel.capture

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

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
