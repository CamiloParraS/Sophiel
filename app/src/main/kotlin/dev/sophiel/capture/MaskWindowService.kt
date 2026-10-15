package dev.sophiel.capture

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import android.os.SystemClock
import dev.sophiel.R
import dev.sophiel.SophielApp
import dev.sophiel.guard.GuardNode
import dev.sophiel.guard.GuardPages
import dev.sophiel.log.BlockReason
import dev.sophiel.settings.AppRule
import dev.sophiel.settings.excludedApps
import dev.sophiel.settings.trustedInFront
import dev.sophiel.ui.BlockActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The mask window's host (D32): an accessibility service that lends [OverlayController] a
 * TYPE_ACCESSIBILITY_OVERLAY window token. Android 12+ treats that window as trusted, so the 0.8
 * opacity cap (D28) does not apply and masks draw opaque. Release builds need it to start; debug
 * builds may start on the 0.79 app overlay without it. M8 (D54): its [displayShot]s are the frames
 * while no share runs.
 *
 * Window content for [appWindowShot] (ticket 16): ids and bounds; for [appAt] (D57) the root's package only. Window-state events for the tamper guard
 * (D56, [guard]): node text only on Settings windows, only nodes holding the service's label
 * (res/xml/mask_window_service.xml). The same events' package enforces per-app rules (D58, [block]).
 */
class MaskWindowService : AccessibilityService() {
    override fun onServiceConnected() {
        // Ticket 17: shots failed (error 1, ~2 s) after app switches, suspected a stale cached window
        // list: with no event types nothing may refresh it. Ask the system each time instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setCacheEnabled(false)
        current.value = this
        onChange?.invoke()
        modes.serviceChanged() // D54: protection on resumes in accessibility mode, also at the first unlock after a reboot
    }

    override fun onUnbind(intent: Intent?): Boolean {
        current.value = null
        trustedFrontFlow.value = false
        onChange?.invoke()
        modes.serviceChanged()
        return super.onUnbind(intent)
    }

    private val modes get() = (application as SophielApp).container.captureModes

    /** D54: the whole display, our masks included; null on failure (logged). The caller closes the buffer at once. */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun displayShot(): ScreenshotResult? = suspendCancellableCoroutine { cont ->
        takeScreenshot(Display.DEFAULT_DISPLAY, Dispatchers.Default.asExecutor(), object : TakeScreenshotCallback {
            // Cancelled meanwhile (the loop stopped): nobody else will close it.
            override fun onSuccess(shot: ScreenshotResult) = cont.resume(shot) { _, s, _ -> s?.hardwareBuffer?.close() }

            override fun onFailure(errorCode: Int) {
                Log.d("Sophiel", "display shot failed, error $errorCode")
                cont.resume(null)
            }
        })
    }

    private val attemptAt = mutableMapOf<String, Long>()

    // Bound with its page open: the Parent just turned the service on there (the wizard too, its PIN already
    // made), and turning on is never blocked (D56). Cleared by the first window that is not the page.
    private var boundOnPage = true

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        block(event)
        guard(event)
        updateTrusted()
    }

    /** D58: after every window change, whether only De confianza apps are in front. Package names only, as [appAt]. */
    private fun updateTrusted() {
        val container = (application as SophielApp).container
        val rules = container.appRules.state.value
        val front = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.map { it.root?.packageName?.toString() }
        val trusted = rules.containsValue(AppRule.TRUSTED) && trustedInFront(front, rules, excludedApps(this))
        if (trustedFrontFlow.getAndUpdate { trusted } != trusted) Log.i("Sophiel", if (trusted) "trusted app in front: scanning stops" else "scanning resumes")
    }

    /**
     * D58: a Bloqueada app came to the front while protection is on (an Unlock too): Home, then the block screen over
     * it ([resolve] first, unless its window is known fullscreen). Settings is excluded, so this never meets [guard]'s pages.
     */
    private fun block(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val container = (application as SophielApp).container
        if (container.appRules.rule(pkg) != AppRule.BLOCKED || !container.captureModes.state.value.protecting) return
        if (pkg in excludedApps(this)) return // blocked before it became the launcher or dialer: it must not trap the phone
        val window = event.source?.window // null for a splash screen's events, before the app's own
        if (window != null && !smallerThanDisplay(window)) {
            main.removeCallbacksAndMessages(null) // fullscreen: nothing to wait for
            resolving = false
            BlockActivity.block(this, pkg) // Home and the block screen, every time, like the guard
        } else if (!resolving) { // a pop-up's own Backs send more events
            resolving = true
            resolve(pkg, 0, sawPopUp = false)
        }
        if (!newAttempt("block:$pkg")) return
        Log.i("Sophiel", "block: $pkg")
        Dispatchers.IO.asExecutor().execute { container.log.blockedOpen(pkg, BlockReason.RULE) }
    }

    private val main = Handler(Looper.getMainLooper())
    private var resolving = false

    /**
     * A blocked app whose window is not known yet (a splash screen) or is a pop-up window (One UI pop-up view). A pop-up
     * stays above Home and the block screen, usable with a tap: close it first, then Home and the block screen. Back
     * sent while it has focus closes it or sends it behind (A: Calculator at once; Chrome took two, the first went back
     * a page). The block screen must not start before: it takes the focus, and nothing gives it back to the pop-up.
     * A splash screen's events look the same for both, so the window list is polled until the app's window shows (A:
     * fullscreen ~1 s, its first screen visible 0.2-0.3 s before the block screen; a pop-up ~1.5 s, unfocused while it
     * zooms in). Human decision 2026-10-15, over a tap gesture (new capability) or an overlay over the pop-up.
     */
    private fun resolve(pkg: String, tick: Int, sawPopUp: Boolean) {
        // A pop-up also lists its caption handle (One UI "MSHandler", a small window of the same package): the largest is the app.
        val own = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName == pkg }
        val window = own.maxByOrNull { it.boundsInScreen().run { width() * height() } }
        val popUp = window != null && smallerThanDisplay(window)
        // ponytail: gives up after MAX_RESOLVE_TICKS (an app that eats Back), the pop-up then stays over the block screen.
        if ((window == null && sawPopUp) || (window != null && !popUp) || tick == MAX_RESOLVE_TICKS) {
            if (sawPopUp) Log.i("Sophiel", "block: $pkg pop-up ${if (window == null) "closed" else "still open"} after $tick ticks")
            resolving = false
            BlockActivity.block(this, pkg)
            return
        }
        val back = popUp && own.any { it.isFocused } && performGlobalAction(GLOBAL_ACTION_BACK)
        main.postDelayed({ resolve(pkg, tick + 1, sawPopUp || popUp) }, if (back) BACK_LAND_MS else RESOLVE_TICK_MS)
    }

    /** A pop-up (or a split screen half): Back reaches it. */
    private fun smallerThanDisplay(window: AccessibilityWindowInfo): Boolean {
        val b = window.boundsInScreen()
        val display = resources.displayMetrics
        return b.width() < display.widthPixels || b.height() < display.heightPixels
    }

    /** One page or open sends more than one event; one attempt is one count. */
    private fun newAttempt(key: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - (attemptAt[key] ?: -SAME_ATTEMPT_MS) < SAME_ATTEMPT_MS) return false
        attemptAt[key] = now
        return true
    }

    /**
     * D56: armed while a PIN exists and no Unlock is active; this page turns the service off, and it is on, or
     * no event would come. Covers the page with [BlockActivity] and logs the attempt.
     */
    private fun guard(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (!GuardPages.reads(pkg)) {
            boundOnPage = false
            return
        }
        val container = (application as SophielApp).container
        if (!container.pin.exists() || container.unlock.unlocked()) return
        val label = getString(R.string.mask_window_service_label)
        // ponytail: one look at the event; a page whose title draws after its window-state event is missed. Retry
        // once after a short delay if A shows that.
        val root = event.source ?: rootInActiveWindow ?: return
        // The app label ("Sophiel") also matches the service label, which contains it.
        val found = root.findAccessibilityNodeInfosByText(getString(R.string.app_name)) +
            GuardPages.IDS.flatMap { root.findAccessibilityNodeInfosByViewId(it) }
        val nodes = found.map { GuardNode(it.text?.toString(), it.viewIdResourceName, it.isChecked || rowSwitchOn(it)) }
        val target = GuardPages.match(pkg, nodes, label)
        if (target == null) {
            boundOnPage = false
            return
        }
        if (boundOnPage) return
        BlockActivity.guard(this) // every time: a single instance, so a repeat lands on the same screen
        if (!newAttempt(target)) return
        Log.i("Sophiel", "guard: $target in $pkg")
        Dispatchers.IO.asExecutor().execute { container.log.tamper(target) } // off main: it rewrites the file
    }

    /** A list row's title ("Appear on top" on A): its switch sits two levels up, in the row's widget frame. */
    private fun rowSwitchOn(title: AccessibilityNodeInfo): Boolean =
        title.parent?.parent?.findAccessibilityNodeInfosByViewId("android:id/switch_widget")?.any { it.isChecked } == true

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
        val window = appWindow(x, y)
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

    /**
     * D57: the Episode app, the package of the top app window under screen pixel ([x], [y]). Only the root's
     * package is read: no text, titles or other nodes. Null under system UI only, or on Sophiel.
     */
    fun appAt(x: Int, y: Int): String? = appWindow(x, y)?.root?.packageName?.toString()?.takeIf { it != packageName }

    private fun appWindow(x: Int, y: Int) = windows
        .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.boundsInScreen().contains(x, y) }
        .maxByOrNull { it.layer }

    companion object {
        private const val SAME_ATTEMPT_MS = 2_000L
        private const val RESOLVE_TICK_MS = 50L
        private const val BACK_LAND_MS = 300L // a Back lands and the window list updates (A)
        private const val MAX_RESOLVE_TICKS = 60
        private val current = MutableStateFlow<MaskWindowService?>(null)

        /** Non-null while bound (D41: bound, not just ticked in Accessibility settings). */
        val instance get() = current.value

        /**
         * [instance], live (D42): after an update the bind lands ~1.8 s after the app opens, so a
         * one-time read on resume stays stale. Status's Start gate watches this.
         */
        val bound: StateFlow<MaskWindowService?> = current

        private val trustedFrontFlow = MutableStateFlow(false)

        /** D58: only De confianza app windows are on screen (false without the service): no masks, no inference. */
        val trustedFront: StateFlow<Boolean> = trustedFrontFlow.asStateFlow()

        /** Set by the live [OverlayController]: the masks follow the service off and back on (D32, D42). */
        @Volatile var onChange: (() -> Unit)? = null
    }
}

private fun AccessibilityWindowInfo.boundsInScreen() = Rect().also(::getBoundsInScreen)
