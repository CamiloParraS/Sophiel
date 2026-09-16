package dev.sophiel.capture

/** Phases of the SPEC.md §4.4 permission/session state machine. Order is not meaningful. */
enum class ControllerPhase {
    IDLE, NEED_NOTIFICATIONS, NEED_OVERLAY, NEED_CONSENT,
    STARTING_SERVICE, ACQUIRING_PROJECTION, RUNNING, STOPPING, BLOCKED,
}

/**
 * @param degraded true if POST_NOTIFICATIONS was denied; the service still runs, but the OS
 *   hides its status notification, so the UI warns that status is Logcat-only
 *   (SPEC.md §4.4 NEED_NOTIFICATIONS).
 */
data class ControllerState(
    val phase: ControllerPhase = ControllerPhase.IDLE,
    val degraded: Boolean = false,
)

sealed interface ControllerEvent {
    data object StartRequested : ControllerEvent
    data class NotificationsResult(val granted: Boolean) : ControllerEvent
    data class OverlayResult(val granted: Boolean) : ControllerEvent
    data class ConsentResult(val granted: Boolean) : ControllerEvent
    data object ServiceStarted : ControllerEvent
    data object ProjectionAcquired : ControllerEvent
    data object StopRequested : ControllerEvent
    data object TeardownComplete : ControllerEvent
}

/**
 * Pure transition function for the SPEC.md §4.4 diagram. Kept free of Android types so it's
 * unit-testable on the JVM; [dev.sophiel.capture.ProjectionController] drives the actual
 * permission requests and service lifecycle around it.
 */
object ProjectionStateMachine {
    fun reduce(state: ControllerState, event: ControllerEvent): ControllerState {
        val phase = state.phase
        return when {
            // The service reports this exactly once per session, however it ended: user stop,
            // status-bar revocation, screen-off, or a failed start. A session is over once it's
            // torn down, whatever phase it reached.
            event is ControllerEvent.TeardownComplete -> ControllerState()

            phase == ControllerPhase.IDLE && event is ControllerEvent.StartRequested ->
                ControllerState(ControllerPhase.NEED_NOTIFICATIONS)

            phase == ControllerPhase.NEED_NOTIFICATIONS && event is ControllerEvent.NotificationsResult ->
                state.copy(phase = ControllerPhase.NEED_OVERLAY, degraded = !event.granted)

            phase == ControllerPhase.NEED_OVERLAY && event is ControllerEvent.OverlayResult ->
                state.copy(phase = if (event.granted) ControllerPhase.NEED_CONSENT else ControllerPhase.BLOCKED)

            // Re-check after the user visits Settings from a BLOCKED overlay explanation.
            phase == ControllerPhase.BLOCKED && event is ControllerEvent.OverlayResult && event.granted ->
                state.copy(phase = ControllerPhase.NEED_CONSENT)

            phase == ControllerPhase.NEED_CONSENT && event is ControllerEvent.ConsentResult ->
                if (event.granted) state.copy(phase = ControllerPhase.STARTING_SERVICE)
                else ControllerState() // cancelling is a valid choice: back to a clean IDLE, no toast

            phase == ControllerPhase.STARTING_SERVICE && event is ControllerEvent.ServiceStarted ->
                state.copy(phase = ControllerPhase.ACQUIRING_PROJECTION)

            phase == ControllerPhase.ACQUIRING_PROJECTION && event is ControllerEvent.ProjectionAcquired ->
                state.copy(phase = ControllerPhase.RUNNING)

            phase == ControllerPhase.RUNNING && event is ControllerEvent.StopRequested ->
                state.copy(phase = ControllerPhase.STOPPING)

            else -> state // event does not apply to this phase; ignore
        }
    }
}
