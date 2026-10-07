package dev.sophiel.ui

import dev.sophiel.capture.ControllerPhase
import dev.sophiel.core.Sensitivity
import dev.sophiel.log.Entry
import dev.sophiel.log.OffReason
import dev.sophiel.settings.ParentPreset

/** The three things Status checks live (D42). Only the overlay is needed to start (D46); the service and notifications are recommended. */
enum class Need { OVERLAY, SERVICE, NOTIFICATIONS }

enum class Page { PROTECTED, OFF, READY, STARTING, STOPPED }

/** Why Precise is not running as Precise (SPEC M6, D35, D40). */
enum class Fallback { ACCESSIBILITY_OFF, ANDROID_BELOW_14 }

/** The line under Start. */
enum class StartNote { TURN_ON_OVERLAY, PARTIAL_MASK }

class StatusInput(
    val phase: ControllerPhase,
    val serviceBound: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val last: Entry?, // the newest Log entry (ticket 13)
    val preset: ParentPreset,
    val sensitivity: Sensitivity,
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
        val REQUIRED = listOf(Need.OVERLAY)
        val RECOMMENDED = listOf(Need.SERVICE, Need.NOTIFICATIONS)

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
                blocking.isNotEmpty() -> Page.OFF
                else -> Page.READY
            }
            val startEnabled = blocking.isEmpty()
            val note = when {
                page == Page.PROTECTED || page == Page.STARTING -> null
                blocking.isNotEmpty() -> StartNote.TURN_ON_OVERLAY
                Need.SERVICE in missing -> StartNote.PARTIAL_MASK // D46: starts anyway, on the 0.79 overlay
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
