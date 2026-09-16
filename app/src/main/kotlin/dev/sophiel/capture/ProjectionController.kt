package dev.sophiel.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the SPEC.md §4.4 state machine and dispatches the Android side effects (permission
 * requests, starting/stopping [dev.sophiel.capture.ProjectionService]) that move it forward.
 *
 * Effects run on phase *entry* ([onEnter]), so each fires once per transition — never again on
 * a repeated call (the D17 Settings loop was exactly that).
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
        /** Below API 33 there is nothing to ask: report `onNotificationsResult(true)` directly. */
        fun requestNotificationPermission()

        /** `Settings.canDrawOverlays(context)`. */
        fun hasOverlayPermission(): Boolean

        /** Opens `ACTION_MANAGE_OVERLAY_PERMISSION`. There is no result callback for it. */
        fun requestOverlayPermission()
        fun launchConsentRequest()
        fun startCaptureService()
        fun stopCaptureService()
    }

    fun start() = apply(ControllerEvent.StartRequested)

    fun onNotificationsResult(granted: Boolean) = apply(ControllerEvent.NotificationsResult(granted))

    /**
     * The SPEC.md §4.4 "re-check in onResume()" step: call from `onStart()`. Only reads the
     * permission and transitions (NEED_OVERLAY -> NEED_CONSENT/BLOCKED, BLOCKED -> NEED_CONSENT);
     * a no-op in any other phase. Opening Settings happens once, on entering NEED_OVERLAY.
     */
    fun recheckOverlay() {
        val effects = effects ?: return
        apply(ControllerEvent.OverlayResult(effects.hasOverlayPermission()))
    }

    fun onConsentResult(granted: Boolean) = apply(ControllerEvent.ConsentResult(granted))

    /** The service confirms `startForeground()` succeeded. */
    fun onServiceStarted() = apply(ControllerEvent.ServiceStarted)

    /** The service confirms `getMediaProjection()` succeeded. */
    fun onProjectionAcquired() = apply(ControllerEvent.ProjectionAcquired)

    fun stop() {
        apply(ControllerEvent.StopRequested)
        effects?.stopCaptureService()
    }

    /** The service released its session, however it ended. */
    fun onTeardownComplete() = apply(ControllerEvent.TeardownComplete)

    private fun apply(event: ControllerEvent) {
        val old = _state.value
        val new = ProjectionStateMachine.reduce(old, event)
        _state.value = new
        if (new.phase != old.phase) onEnter(new.phase)
    }

    private fun onEnter(phase: ControllerPhase) {
        val effects = effects ?: return
        when (phase) {
            ControllerPhase.NEED_NOTIFICATIONS -> effects.requestNotificationPermission()
            ControllerPhase.NEED_OVERLAY ->
                if (effects.hasOverlayPermission()) apply(ControllerEvent.OverlayResult(true))
                else effects.requestOverlayPermission()
            ControllerPhase.NEED_CONSENT -> effects.launchConsentRequest()
            ControllerPhase.STARTING_SERVICE -> effects.startCaptureService()
            else -> Unit
        }
    }
}
