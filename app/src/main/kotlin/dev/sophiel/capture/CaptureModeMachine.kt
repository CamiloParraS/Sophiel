package dev.sophiel.capture

import dev.sophiel.log.OffReason

/** How the screen is being read right now (SPEC.md §4.7). */
enum class CaptureMode { SCREEN_SHARE, ACCESSIBILITY, NONE }

/**
 * @param protecting the Parent's persisted on/off: on when Start's share is acquired, off at PIN Stop, or
 *   with the switch off when nothing is left to read the screen (a share ending, or SwitchOff outside one).
 * @param shareSurvivesScreenOff platform fact: below API 35 a share outlives screen-off (D55).
 */
data class ModeState(
    val protecting: Boolean = false,
    val mode: CaptureMode = CaptureMode.NONE,
    val screenOn: Boolean = true,
    val serviceBound: Boolean = false,
    val keepProtecting: Boolean = true, // "Seguir protegiendo siempre"
    val shareSurvivesScreenOff: Boolean = false,
) {
    /** Switch on and the service bound: the only way capture goes on without a share (SPEC.md §4.7). */
    val canFallBack get() = keepProtecting && serviceBound

    /** The capture session lives on: capturing, or protection on with a fallback, waiting for screen-on. */
    val holdsSession get() = mode != CaptureMode.NONE || protecting && canFallBack
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
    data object Stop : ModeEvent // the Parent's PIN Stop
}

/** Pure transition function for the SPEC.md §4.7 diagram (D54, D55). */
object CaptureModeMachine {
    fun reduce(s: ModeState, e: ModeEvent): ModeState = when (e) {
        ModeEvent.ShareStarted -> s.copy(protecting = true, mode = CaptureMode.SCREEN_SHARE)
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
            s.copy(serviceBound = false, mode = if (s.mode == CaptureMode.ACCESSIBILITY) CaptureMode.NONE else s.mode)
        ModeEvent.SwitchOn -> s.copy(keepProtecting = true).resume()
        ModeEvent.SwitchOff ->
            if (s.mode == CaptureMode.SCREEN_SHARE) s.copy(keepProtecting = false)
            else s.copy(keepProtecting = false, protecting = false, mode = CaptureMode.NONE)
        ModeEvent.Stop -> s.copy(protecting = false, mode = CaptureMode.NONE)
    }

    /**
     * D39's OFF for [e], or null: only where protection ends (D54) or, until ticket 16 writes a Sin
     * protección stretch instead, where a share ends with nothing to fall back on.
     */
    fun offReason(old: ModeState, new: ModeState, e: ModeEvent): String? {
        val screen = if (new.screenOn) OffReason.SYSTEM else OffReason.SCREEN_OFF
        return when {
            old.protecting && !new.protecting -> if (e == ModeEvent.Stop || e == ModeEvent.SwitchOff) OffReason.USER else screen
            old.mode == CaptureMode.SCREEN_SHARE && new.mode == CaptureMode.NONE && new.protecting && !new.canFallBack -> screen
            else -> null
        }
    }

    /** NONE comes back to ACCESSIBILITY when protection is on, there is a fallback and the screen is on. */
    private fun ModeState.resume() =
        if (mode == CaptureMode.NONE && protecting && canFallBack && screenOn) copy(mode = CaptureMode.ACCESSIBILITY)
        else this
}
