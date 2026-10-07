package dev.sophiel.pin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Parent's unlock window (D38), in memory: dies with the process. [endsAt] is `elapsedRealtime` ms,
 * null = locked. Expiry is read, not scheduled: the banner's countdown and every gate call [unlocked].
 */
class Unlock(private val clock: () -> Long) {
    private val _endsAt = MutableStateFlow<Long?>(null)
    val endsAt: StateFlow<Long?> = _endsAt.asStateFlow()

    /** A correct PIN, or any PIN-gated action while unlocked: the window runs 2 min from now. */
    fun extend() { _endsAt.value = clock() + WINDOW_MS }

    fun lockNow() { _endsAt.value = null }

    fun unlocked(): Boolean {
        val end = _endsAt.value ?: return false
        if (clock() < end) return true
        lockNow()
        return false
    }

    /** `MainActivity.onStop`: rotation recreates the activity, leaving or screen off does not. */
    fun onActivityStop(changingConfigurations: Boolean) { if (!changingConfigurations) lockNow() }

    companion object { const val WINDOW_MS = 120_000L }
}
