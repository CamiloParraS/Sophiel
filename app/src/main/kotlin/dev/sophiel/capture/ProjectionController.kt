package dev.sophiel.capture

import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the SPEC.md §4.4 state machine and dispatches the Android side effects (permission
 * requests, starting/stopping [dev.sophiel.capture.ProjectionService]) that move it forward.
 *
 * Lives in [dev.sophiel.AppContainer] as a single process-wide instance, because the service
 * that eventually reports [onServiceStarted]/[onProjectionAcquired] runs independently of
 * whichever `MainActivity` instance is currently alive. [Effects] is re-attached by the current
 * Activity in `onStart()` and cleared in `onStop()` so a destroyed Activity is never leaked.
 */
class ProjectionController {
    private val _state = MutableStateFlow(ControllerState())
    val state: StateFlow<ControllerState> = _state.asStateFlow()

    var effects: Effects? = null

    interface Effects {
        fun requestNotificationPermission()

        /** `Settings.canDrawOverlays(context)`. */
        fun hasOverlayPermission(): Boolean

        /** Opens `ACTION_MANAGE_OVERLAY_PERMISSION`. There is no result callback for it. */
        fun requestOverlayPermission()
        fun launchConsentRequest()
        fun startCaptureService()
        fun stopCaptureService()
    }

    fun start() {
        if (_state.value.phase != ControllerPhase.IDLE) return
        apply(ControllerEvent.StartRequested)
        if (Build.VERSION.SDK_INT >= 33) {
            effects?.requestNotificationPermission()
        } else {
            onNotificationsResult(granted = true)
        }
    }

    fun onNotificationsResult(granted: Boolean) {
        apply(ControllerEvent.NotificationsResult(granted))
        val effects = effects ?: return
        if (effects.hasOverlayPermission()) {
            recheckOverlay() // already granted: advance straight through NEED_OVERLAY
        } else {
            // Open Settings exactly once. Phase stays NEED_OVERLAY until the user comes back
            // and Activity.onStart() calls recheckOverlay() again — see its doc for why that
            // call must NOT re-open Settings on every call (it used to, and looped forever).
            effects.requestOverlayPermission()
        }
    }

    /**
     * Call from `onStart()`/`onResume()` while [ControllerState.phase] is NEED_OVERLAY or
     * BLOCKED: this is the SPEC.md §4.4 "re-check in onResume()" step. Unlike
     * [onNotificationsResult]'s first check, this never opens Settings itself — it only reads
     * the current permission state and transitions: NEED_OVERLAY -> NEED_CONSENT (granted) or
     * -> BLOCKED (still denied); BLOCKED -> NEED_CONSENT if the user granted it via Settings
     * directly. Doing the Settings-launch here too was the bug: every resume re-opened
     * Settings instead of ever reaching BLOCKED.
     */
    fun recheckOverlay() {
        val phase = _state.value.phase
        if (phase != ControllerPhase.NEED_OVERLAY && phase != ControllerPhase.BLOCKED) return
        val effects = effects ?: return
        apply(ControllerEvent.OverlayResult(effects.hasOverlayPermission()))
        if (_state.value.phase == ControllerPhase.NEED_CONSENT) effects.launchConsentRequest()
    }

    fun onConsentResult(granted: Boolean) {
        apply(ControllerEvent.ConsentResult(granted))
        if (_state.value.phase == ControllerPhase.STARTING_SERVICE) effects?.startCaptureService()
    }

    /** The service confirms `startForeground()` succeeded. */
    fun onServiceStarted() = apply(ControllerEvent.ServiceStarted)

    /** The service confirms `getMediaProjection()` succeeded. */
    fun onProjectionAcquired() = apply(ControllerEvent.ProjectionAcquired)

    fun stop() {
        apply(ControllerEvent.StopRequested)
        effects?.stopCaptureService()
    }

    /** [android.media.projection.MediaProjection.Callback.onStop] fired (revoked from the status bar). */
    fun onProjectionStopped() = apply(ControllerEvent.ProjectionStopped)

    /** The service finished releasing the [android.media.projection.MediaProjection] and its display. */
    fun onTeardownComplete() = apply(ControllerEvent.TeardownComplete)

    private fun apply(event: ControllerEvent) {
        _state.value = ProjectionStateMachine.reduce(_state.value, event)
    }
}
