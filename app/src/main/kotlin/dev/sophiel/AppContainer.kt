package dev.sophiel

import android.content.Context
import dev.sophiel.capture.OwnScreens
import dev.sophiel.capture.ProjectionController
import dev.sophiel.core.Sensitivity
import android.os.SystemClock
import android.provider.Settings
import dev.sophiel.feed.SpikeModel
import dev.sophiel.log.EventLog
import dev.sophiel.pin.PinStore
import dev.sophiel.pin.Unlock
import dev.sophiel.settings.SettingsRepository
import java.io.File

/**
 * Manual DI (SPEC.md §2.4/D2): two modules and a handful of app-scoped singletons don't
 * justify Hilt. [dev.sophiel.MainActivity] and [dev.sophiel.capture.ProjectionService] both
 * read [projectionController] off [SophielApp] so they observe/drive the same session even
 * though the service outlives any single Activity instance.
 */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context)

    private val pinPrefs = context.getSharedPreferences("pin", Context.MODE_PRIVATE)

    /** D38: hash and lockout on disk (never the PIN), the unlock window in memory only. */
    val pin = PinStore(
        read = { pinPrefs.getString(it, null) },
        write = { m -> pinPrefs.edit().apply { m.forEach { (k, v) -> putString(k, v) } }.apply() },
        clock = SystemClock::elapsedRealtime,
        bootCount = { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0) },
    )
    val unlock = Unlock(SystemClock::elapsedRealtime)

    private val logPrefs = context.getSharedPreferences("log", Context.MODE_PRIVATE)

    /** D39: the Parent's Log. An ON left open by a dead process is closed here, before any session. */
    val log = EventLog(
        file = File(context.filesDir, "log.csv"),
        wall = System::currentTimeMillis,
        read = { logPrefs.getString(it, null) },
        write = { m -> logPrefs.edit().apply { m.forEach { (k, v) -> if (v == null) remove(k) else putString(k, v) } }.apply() },
        bootCount = { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0) },
    ).also { it.recoverGap() }

    val projectionController = ProjectionController()

    /** Ticket 09: a Sophiel screen fills the display; capture hides the masks and pauses. */
    val ownScreens = OwnScreens()

    /** Spike (D24): model the live capture judges with, picked on the Status screen (debug builds). */
    @Volatile
    var liveModel = SpikeModel.GANTMAN

    /** The debug pill (model, tile, timings), toggled in the debug menu. Debug-signed builds only; in memory. */
    @Volatile
    var debugPill = true

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
