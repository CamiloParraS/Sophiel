package dev.sophiel

import dev.sophiel.capture.OwnScreens
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

    /** Ticket 09: a Sophiel screen fills the display; capture hides the masks and pauses. */
    val ownScreens = OwnScreens()

    /** Spike (D24): model the live capture judges with, picked on the Status screen (debug builds). */
    @Volatile
    var liveModel = SpikeModel.GANTMAN

    /** Tile grid live capture uses, picked on the Status screen (debug builds) until the Parent setting (M6). */
    @Volatile
    var livePreset = Preset.BALANCED

    /**
     * Ticket 16: judge masked tiles from a window shot instead of lifting the mask (API 34+, needs
     * MaskWindowService). Picked on the Status screen (debug builds) until the Parent setting (M6).
     */
    @Volatile
    var peekUnderMask = false

    /**
     * D35: the Precise preset, NudeNet 320n's boxes masked and refreshed by window shots (API 34+,
     * MaskWindowService on). Elsewhere it runs [livePreset], set to Balanced when Precise is picked.
     * Picked on the Status screen (debug builds) until the Parent setting (M6).
     */
    @Volatile
    var precise = false
}
