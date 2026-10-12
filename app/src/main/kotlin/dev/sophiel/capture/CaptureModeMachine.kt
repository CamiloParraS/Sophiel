package dev.sophiel.capture

/** How the screen is being read right now (SPEC.md §4.7). */
enum class CaptureMode { SCREEN_SHARE, ACCESSIBILITY, NONE }

/**
 * @param protecting the Parent's persisted on/off: set by Start (consent) and PIN Stop only, apart from
 *   the switch-off case that ends it with the share (M6 behaviour).
 */
data class ModeState(
    val protecting: Boolean = false,
    val mode: CaptureMode = CaptureMode.NONE,
    val screenOn: Boolean = true,
    val serviceBound: Boolean = false,
    val keepProtecting: Boolean = true, // "Seguir protegiendo siempre"
)

sealed interface ModeEvent {
    data object ShareStarted : ModeEvent
    data object ShareEnded : ModeEvent
    data object ScreenOff : ModeEvent
    data object ScreenOn : ModeEvent
    data object ServiceBound : ModeEvent
    data object ServiceUnbound : ModeEvent
    data object SwitchOff : ModeEvent
    data object Stop : ModeEvent // the Parent's PIN Stop
}

/** Pure transition function for the SPEC.md §4.7 diagram (D54, D55). */
object CaptureModeMachine {
    fun reduce(s: ModeState, e: ModeEvent): ModeState = when (e) {
        ModeEvent.ShareStarted -> s.copy(protecting = true, mode = CaptureMode.SCREEN_SHARE)
        ModeEvent.ShareEnded ->
            if (!s.keepProtecting) s.copy(protecting = false, mode = CaptureMode.NONE)
            else s.copy(mode = CaptureMode.NONE).resume()
        ModeEvent.ScreenOff ->
            s.copy(screenOn = false, mode = if (s.mode == CaptureMode.ACCESSIBILITY) CaptureMode.NONE else s.mode)
        ModeEvent.ScreenOn -> s.copy(screenOn = true).resume()
        ModeEvent.ServiceBound -> s.copy(serviceBound = true).resume()
        ModeEvent.ServiceUnbound ->
            s.copy(serviceBound = false, mode = if (s.mode == CaptureMode.ACCESSIBILITY) CaptureMode.NONE else s.mode)
        ModeEvent.SwitchOff ->
            if (s.mode == CaptureMode.SCREEN_SHARE) s.copy(keepProtecting = false)
            else s.copy(keepProtecting = false, protecting = false, mode = CaptureMode.NONE)
        ModeEvent.Stop -> s.copy(protecting = false, mode = CaptureMode.NONE)
    }

    /** NONE comes back to ACCESSIBILITY when protection is on, the service is bound and the screen is on. */
    private fun ModeState.resume() =
        if (mode == CaptureMode.NONE && protecting && keepProtecting && serviceBound && screenOn) copy(mode = CaptureMode.ACCESSIBILITY)
        else this
}
