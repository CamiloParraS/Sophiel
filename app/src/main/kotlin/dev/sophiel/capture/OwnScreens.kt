package dev.sophiel.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ticket 09 (D28): whether a Sophiel screen fills the display, i.e. some activity of ours is resumed
 * and not in multi-window mode. While true the masks hide and the tracker pauses; in split screen
 * the other app is still on screen, so masks stay up. Keyed by activity, so a pause that was never
 * counted (resumed in split screen) can't drive the count negative.
 */
class OwnScreens {
    private val inFront = HashSet<Any>()
    private val _showing = MutableStateFlow(false)
    val showing: StateFlow<Boolean> = _showing.asStateFlow()

    /** [front]: resumed and not in multi-window mode. Main thread only. */
    fun update(activity: Any, front: Boolean) {
        if (front) inFront += activity else inFront -= activity
        _showing.value = inFront.isNotEmpty()
    }
}
