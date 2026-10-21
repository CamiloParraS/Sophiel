package dev.sophiel.capture

import dev.sophiel.log.OffReason

/** How the screen is being read right now (SPEC.md §4.7). */
enum class CaptureMode { SCREEN_SHARE, ACCESSIBILITY, NONE }

/**
 * @param protecting the Parent's persisted on/off: on when Start's share is acquired, off at PIN Stop, or
 *   with the switch off when nothing is left to read the screen (a share ending, or SwitchOff outside one).
 * @param interrupted protection is on but the service went away (or was never there when the app opened):
 *   binding it again does not resume, only Start does (SPEC.md §4.7). A reboot is no interruption.
 * @param shareSurvivesScreenOff platform fact: below API 35 a share outlives screen-off (D55).
 */
data class ModeState(
    val protecting: Boolean = false,
    val mode: CaptureMode = CaptureMode.NONE,
    val screenOn: Boolean = true,
    val serviceBound: Boolean = false,
    val keepProtecting: Boolean = true, // "Seguir protegiendo siempre"
    val interrupted: Boolean = false,
    val shareSurvivesScreenOff: Boolean = false,
) {
    /** Switch on and the service bound: the only way capture goes on without a share (SPEC.md §4.7). */
    val canFallBack get() = keepProtecting && serviceBound

    /** The capture session lives on: capturing, or protection on with a fallback, waiting for screen-on. */
    val holdsSession get() = mode != CaptureMode.NONE || protecting && canFallBack && !interrupted

    /** Protection on and nothing to read the screen with until a Start (or a bind, after a kill): Sin protección (D54). */
    val uncovered get() = protecting && !holdsSession
}

sealed interface ModeEvent {
    data object ShareStarted : ModeEvent
    data object ShareEnded : ModeEvent
    data object ScreenOff : ModeEvent
    data object ScreenOn : ModeEvent
    data object ServiceBound : ModeEvent
    data object ServiceUnbound : ModeEvent
    data object SwitchOn : ModeEvent
    data object SwitchOff : ModeEvent
    data object UiOpened : ModeEvent // the app came to the front
    data object Stop : ModeEvent // the Parent's PIN Stop
    data object RemoteStop : ModeEvent // D82: the same Stop, from the Parent phone
    data object RemoteStart : ModeEvent // D82: Start without a share; the caller checks the service and the switch
}

/** Pure transition function for the SPEC.md §4.7 diagram (D54, D55). */
object CaptureModeMachine {
    fun reduce(s: ModeState, e: ModeEvent): ModeState = when (e) {
        ModeEvent.ShareStarted -> s.copy(protecting = true, mode = CaptureMode.SCREEN_SHARE, interrupted = false)
        ModeEvent.ShareEnded -> when {
            s.mode != CaptureMode.SCREEN_SHARE -> s // late: the screen-off already ended it
            !s.keepProtecting -> s.copy(protecting = false, mode = CaptureMode.NONE)
            else -> s.copy(mode = CaptureMode.NONE).resume()
        }
        ModeEvent.ScreenOff -> {
            val off = s.copy(screenOn = false)
            when {
                s.mode == CaptureMode.ACCESSIBILITY -> off.copy(mode = CaptureMode.NONE)
                // D55: below 35 the share pauses, but only with a fallback for when it does end.
                s.mode == CaptureMode.SCREEN_SHARE && !(s.shareSurvivesScreenOff && s.canFallBack) -> reduce(off, ModeEvent.ShareEnded)
                else -> off
            }
        }
        ModeEvent.ScreenOn -> s.copy(screenOn = true).resume()
        ModeEvent.ServiceBound -> s.copy(serviceBound = true).resume()
        ModeEvent.ServiceUnbound ->
            s.copy(
                serviceBound = false, interrupted = s.interrupted || s.protecting,
                mode = if (s.mode == CaptureMode.ACCESSIBILITY) CaptureMode.NONE else s.mode,
            )
        ModeEvent.UiOpened ->
            if (!s.serviceBound && s.mode == CaptureMode.NONE && s.protecting) s.copy(interrupted = true) else s
        ModeEvent.SwitchOn -> s.copy(keepProtecting = true).resume()
        ModeEvent.SwitchOff ->
            if (s.mode == CaptureMode.SCREEN_SHARE) s.copy(keepProtecting = false)
            else s.copy(keepProtecting = false, protecting = false, mode = CaptureMode.NONE, interrupted = false)
        ModeEvent.Stop, ModeEvent.RemoteStop -> s.copy(protecting = false, mode = CaptureMode.NONE, interrupted = false)
        ModeEvent.RemoteStart -> s.copy(protecting = true, interrupted = false).resume()
    }

    /** D39's OFF for [e], or null: only where protection ends (D54); the screen and system reasons need the switch off. */
    fun offReason(old: ModeState, new: ModeState, e: ModeEvent): String? = when {
        !old.protecting || new.protecting -> null
        e == ModeEvent.RemoteStop -> OffReason.REMOTE
        e == ModeEvent.Stop || e == ModeEvent.SwitchOff -> OffReason.USER
        new.screenOn -> OffReason.SYSTEM
        else -> OffReason.SCREEN_OFF
    }

    /**
     * A Sin protección stretch (D54) opens when protection is on, the screen on and nothing will capture.
     * It ends only when capture or protection comes back: one stretch per interruption, screen-offs inside
     * it included. [open]: one is open now.
     */
    fun gapOpens(open: Boolean, s: ModeState) = !open && s.uncovered && s.screenOn

    fun gapEnds(open: Boolean, s: ModeState) = open && !s.uncovered

    /** The service went away and left protection with nothing to capture with: "Protección interrumpida" (D54). */
    fun serviceLost(old: ModeState, new: ModeState) = old.serviceBound && !new.serviceBound && new.uncovered

    /** A share ended with protection on and nothing to fall back on (switch on, no service): D21's resume notice (D46). */
    fun lostShare(old: ModeState, new: ModeState) =
        old.mode == CaptureMode.SCREEN_SHARE && new.mode == CaptureMode.NONE && new.protecting && !new.canFallBack

    /** A share ended with the screen on and a fallback took over (D54): the slow-mode notice and the Log's "Compartir pantalla detenido". */
    fun slowMode(old: ModeState, new: ModeState) = old.mode == CaptureMode.SCREEN_SHARE && new.mode == CaptureMode.ACCESSIBILITY

    /** NONE comes back to ACCESSIBILITY when protection is on, there is a fallback and the screen is on. */
    private fun ModeState.resume() =
        if (mode == CaptureMode.NONE && protecting && canFallBack && screenOn && !interrupted) copy(mode = CaptureMode.ACCESSIBILITY)
        else this
}
