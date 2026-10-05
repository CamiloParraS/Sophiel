package dev.sophiel

import dev.sophiel.capture.ProjectionController
import dev.sophiel.core.Preset
import dev.sophiel.feed.SpikeModel

/**
 * Manual DI (SPEC.md §2.4/D2): two modules and a handful of app-scoped singletons don't
 * justify Hilt. [dev.sophiel.MainActivity] and [dev.sophiel.capture.ProjectionService] both
 * read [projectionController] off [SophielApp] so they observe/drive the same session even
 * though the service outlives any single Activity instance.
 */
class AppContainer {
    val projectionController = ProjectionController()

    /** Spike (D24): model the live capture judges with, picked on the Protection screen. */
    @Volatile
    var liveModel = SpikeModel.GANTMAN

    /** Tile grid live capture uses, picked on the Protection screen until the Parent setting (M6). */
    @Volatile
    var livePreset = Preset.LIGHT

    /**
     * Ticket 16: judge masked tiles from a window shot instead of lifting the mask (API 34+, needs
     * MaskWindowService). Picked on the Protection screen until the Parent setting (M6).
     */
    @Volatile
    var peekUnderMask = false
}
