package dev.sophiel.remote.parent

import dev.sophiel.log.Entry
import dev.sophiel.remote.Apps
import dev.sophiel.remote.Rule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One Child phone's screen (D81): its fetched Log and Apps. Memory only: nothing here is ever stored. */
sealed interface PhoneState {
    data object Loading : PhoneState
    data object Offline : PhoneState // "Sin conexión"
    data object Gone : PhoneState // "Se desvinculó"
    data object Failed : PhoneState

    /** [unsaved]: the package whose last rule change did not reach the phone; its row is as it was. [saving]: one in flight. */
    data class Ready(
        val entries: List<Entry>,
        val apps: List<Apps>,
        val protecting: Boolean,
        val accessibilityOff: Boolean,
        val at: Long,
        val saving: String? = null,
        val unsaved: String? = null,
    ) : PhoneState {
        /** D58: without the service nothing is enforced, so the rows are disabled. */
        val appsEnabled get() = !accessibilityOff

        /** Package -> label for the Historial rows; an app not in the list shows its package. */
        val labels get() = apps.associate { it.pkg to it.label }
    }
}

class PhoneView(private val api: Api, private val session: ParentSession, val id: String, private val clock: () -> Long = System::currentTimeMillis) {
    private val _state = MutableStateFlow<PhoneState>(PhoneState.Loading)
    val state: StateFlow<PhoneState> = _state

    /** A `log` request: the 7-day rows and the Apps list. */
    suspend fun fetch() {
        _state.value = PhoneState.Loading
        _state.value = when (val a = session.ask(api, id, "log", clock = clock)) {
            is Answer.Got -> a.reply.run {
                PhoneState.Ready(logCsv.orEmpty().lineSequence().mapNotNull(Entry::parse).toList(), apps.orEmpty(), protecting, accessibilityOff, clock())
            }
            is Answer.No -> gone(a.why) ?: PhoneState.Failed
        }
    }

    /** A `rules` request for one app; the rows are redrawn from the reply, kept in the order they had. */
    suspend fun setRule(pkg: String, rule: Rule) {
        val before = _state.value as? PhoneState.Ready ?: return
        if (before.saving != null) return
        _state.value = before.copy(saving = pkg, unsaved = null)
        _state.value = when (val a = session.ask(api, id, "rules", mapOf(pkg to rule), clock)) {
            is Answer.Got -> a.reply.run {
                val order = before.apps.withIndex().associate { (i, app) -> app.pkg to i }
                before.copy(
                    apps = apps.orEmpty().sortedBy { order[it.pkg] ?: Int.MAX_VALUE }, protecting = protecting,
                    accessibilityOff = accessibilityOff, at = clock(), saving = null, unsaved = null,
                )
            }
            is Answer.No -> if (a.why == Failure.GONE) PhoneState.Gone else before.copy(saving = null, unsaved = pkg)
        }
    }

    private fun gone(why: Failure) = when (why) {
        Failure.OFFLINE -> PhoneState.Offline
        Failure.GONE -> PhoneState.Gone
        else -> null
    }
}
