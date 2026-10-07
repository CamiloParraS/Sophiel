package dev.sophiel.ui

import dev.sophiel.capture.ControllerPhase
import dev.sophiel.core.Sensitivity
import dev.sophiel.log.Entry
import dev.sophiel.log.OffReason
import dev.sophiel.settings.ParentPreset

/** The three things Status checks live (D42). Overlay and the service are needed to start; notifications are only recommended. */
enum class Need { OVERLAY, SERVICE, NOTIFICATIONS }

enum class Page { PROTECTED, OFF, READY, STARTING, STOPPED }

/** Why Precise is not running as Precise (SPEC M6, D35, D40). */
enum class Fallback { ACCESSIBILITY_OFF, ANDROID_BELOW_14 }

/** The line under Start. */
enum class StartNote { TURN_ON_OVERLAY, TURN_ON_SERVICE, TURN_ON_BOTH, DEBUG_ALPHA }

class StatusInput(
    val phase: ControllerPhase,
    val serviceBound: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val last: Entry?, // the newest Log entry (ticket 13)
    val preset: ParentPreset,
    val sensitivity: Sensitivity,
    val debug: Boolean,
    val sdk: Int,
)

/** What Status shows, so the composable only draws. [stopped] is the OFF entry behind "Se detuvo". */
class StatusModel(
    val page: Page,
    val missing: List<Need>,
    val startEnabled: Boolean,
    val note: StartNote?,
    val stopped: Entry?,
    val preset: ParentPreset,
    val sensitivity: Sensitivity,
    val fallback: Fallback?,
    val stopBusy: Boolean,
) {
    companion object {
        val REQUIRED = listOf(Need.OVERLAY, Need.SERVICE)
        val RECOMMENDED = listOf(Need.NOTIFICATIONS)

        private val STARTING = setOf(
            ControllerPhase.NEED_NOTIFICATIONS, ControllerPhase.NEED_OVERLAY, ControllerPhase.NEED_CONSENT,
            ControllerPhase.STARTING_SERVICE, ControllerPhase.ACQUIRING_PROJECTION,
        )

        fun of(i: StatusInput): StatusModel {
            val met = mapOf(Need.OVERLAY to i.overlay, Need.SERVICE to i.serviceBound, Need.NOTIFICATIONS to i.notifications)
            val missing = Need.entries.filter { met[it] != true }
            val blocking = REQUIRED.filter { it in missing }
            // D45: an OFF the user did not cause, until the next start logs an ON.
            val stopped = i.last?.takeIf { it.kind == "OFF" && it.fields.firstOrNull().let { r -> r != null && r != OffReason.USER } }
            val running = i.phase == ControllerPhase.RUNNING || i.phase == ControllerPhase.STOPPING
            val page = when {
                running -> Page.PROTECTED
                i.phase in STARTING -> Page.STARTING
                stopped != null -> Page.STOPPED
                blocking.isNotEmpty() && !i.debug -> Page.OFF // debug builds start on the 0.79 overlay
                else -> Page.READY
            }
            val startEnabled = i.debug || blocking.isEmpty()
            val note = when {
                page == Page.PROTECTED || page == Page.STARTING -> null
                i.debug -> if (Need.SERVICE in missing) StartNote.DEBUG_ALPHA else null
                blocking.size == 2 -> StartNote.TURN_ON_BOTH
                blocking == listOf(Need.OVERLAY) -> StartNote.TURN_ON_OVERLAY
                blocking == listOf(Need.SERVICE) -> StartNote.TURN_ON_SERVICE
                else -> null
            }
            val fallback = when {
                i.preset != ParentPreset.PRECISE -> null
                i.sdk < 34 -> Fallback.ANDROID_BELOW_14
                !i.serviceBound -> Fallback.ACCESSIBILITY_OFF
                else -> null
            }
            return StatusModel(page, missing, startEnabled, note, stopped, i.preset, i.sensitivity, fallback, i.phase == ControllerPhase.STOPPING)
        }
    }
}
