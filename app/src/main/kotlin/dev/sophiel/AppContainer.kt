package dev.sophiel

import android.content.Context
import dev.sophiel.capture.OwnScreens
import dev.sophiel.capture.ProjectionController
import dev.sophiel.core.Sensitivity
import dev.sophiel.feed.SpikeModel
import dev.sophiel.settings.SettingsRepository

/**
 * Manual DI (SPEC.md §2.4/D2): two modules and a handful of app-scoped singletons don't
 * justify Hilt. [dev.sophiel.MainActivity] and [dev.sophiel.capture.ProjectionService] both
 * read [projectionController] off [SophielApp] so they observe/drive the same session even
 * though the service outlives any single Activity instance.
 */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context)

    val projectionController = ProjectionController()

    /** Ticket 09: a Sophiel screen fills the display; capture hides the masks and pauses. */
    val ownScreens = OwnScreens()

    /** Spike (D24): model the live capture judges with, picked on the Status screen (debug builds). */
    @Volatile
    var liveModel = SpikeModel.GANTMAN

    /** Debug raw cutoff (D40), in memory: cleared by [pickSensitivity] or process death. */
    @Volatile
    var thresholdOverride: Float? = null

    /** Live [dev.sophiel.core.Severity.EXPLICIT] cutoff, read by the detector per tile (D40). */
    fun threshold() = thresholdOverride ?: settings.value.sensitivity.threshold

    fun pickSensitivity(s: Sensitivity) {
        thresholdOverride = null
        settings.update { it.copy(sensitivity = s) }
    }
}
