package dev.sophiel.capture

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional (D32): an accessibility service that does nothing but lend [OverlayController] a
 * TYPE_ACCESSIBILITY_OVERLAY window token. Android 12+ treats that window as trusted, so the 0.8
 * opacity cap (D28) does not apply and masks draw opaque. Off: masks fall back to alpha 0.79.
 *
 * No event types, no window content (res/xml/mask_window_service.xml).
 */
class MaskWindowService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        onUnbound?.invoke()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        /** Non-null while the Parent has it enabled in Accessibility settings. */
        @Volatile var instance: MaskWindowService? = null
            private set

        /** Set by the live [OverlayController]: turning the service off mid-session must not drop the masks. */
        @Volatile var onUnbound: (() -> Unit)? = null
    }
}
