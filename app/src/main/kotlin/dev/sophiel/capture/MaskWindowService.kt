package dev.sophiel.capture

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The mask window's host (D32): an accessibility service that lends [OverlayController] a
 * TYPE_ACCESSIBILITY_OVERLAY window token. Android 12+ treats that window as trusted, so the 0.8
 * opacity cap (D28) does not apply and masks draw opaque. Release builds need it to start; debug
 * builds may start on the 0.79 app overlay without it.
 *
 * No event types. Window content only for [appWindowShot] (ticket 16): ids and bounds, never nodes
 * (res/xml/mask_window_service.xml).
 */
class MaskWindowService : AccessibilityService() {
    override fun onServiceConnected() {
        // Ticket 17: shots failed (error 1, ~2 s) after app switches, suspected a stale cached window
        // list: with no event types nothing may refresh it. Ask the system each time instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setCacheEnabled(false)
        current.value = this
        onChange?.invoke()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        current.value = null
        onChange?.invoke()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    /**
     * Ticket 16: the top app window under screen pixel ([x], [y]), shot without the windows above it,
     * our mask included (what the SDK documents takeScreenshotOfWindow for). A software bitmap and
     * the window's screen bounds, or null: no app window there, a secure window, or the 333 ms limit.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    suspend fun appWindowShot(x: Int, y: Int): Pair<Bitmap, Rect>? {
        // ponytail: one window per shot; a tile under two (split screen, a dialog over part of it) is
        // judged on the top one only. Shoot each window under the tile if that shows up on device.
        val window = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.boundsInScreen().contains(x, y) }
            .maxByOrNull { it.layer }
        if (window == null) {
            Log.d("Sophiel", "peek: no app window at $x,$y")
            return null
        }
        val bounds = window.boundsInScreen()
        Log.d("Sophiel", "shot window id=${window.id} title=${window.title} layer=${window.layer} focused=${window.isFocused}")
        return suspendCancellableCoroutine { cont ->
            takeScreenshotOfWindow(window.id, Dispatchers.Default.asExecutor(), object : TakeScreenshotCallback {
                override fun onSuccess(shot: ScreenshotResult) {
                    // Software copy here, off the frame lane: the detector reads pixels.
                    val hardware = Bitmap.wrapHardwareBuffer(shot.hardwareBuffer, shot.colorSpace)
                    val bitmap = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                    hardware?.recycle()
                    shot.hardwareBuffer.close()
                    cont.resume(bitmap?.let { it to bounds })
                }

                override fun onFailure(errorCode: Int) {
                    Log.d("Sophiel", "peek: window shot failed, error $errorCode")
                    cont.resume(null)
                }
            })
        }
    }

    companion object {
        private val current = MutableStateFlow<MaskWindowService?>(null)

        /** Non-null while bound (D41: bound, not just ticked in Accessibility settings). */
        val instance get() = current.value

        /**
         * [instance], live (D42): after an update the bind lands ~1.8 s after the app opens, so a
         * one-time read on resume stays stale. Status's Start gate watches this.
         */
        val bound: StateFlow<MaskWindowService?> = current

        /** Set by the live [OverlayController]: the masks follow the service off and back on (D32, D42). */
        @Volatile var onChange: (() -> Unit)? = null
    }
}

private fun AccessibilityWindowInfo.boundsInScreen() = Rect().also(::getBoundsInScreen)
