package dev.sophiel.settings

import android.content.Context
import dev.sophiel.core.Preset
import dev.sophiel.core.Sensitivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/** The Parent's preset (SPEC.md §3.5). Precise runs Balanced tiles where it can't run (D35). */
enum class ParentPreset(val tiles: Preset) { LIGHT(Preset.LIGHT), BALANCED(Preset.BALANCED), PRECISE(Preset.BALANCED) }

data class ParentSettings(
    val preset: ParentPreset = ParentPreset.BALANCED,
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
    val peekUnderMask: Boolean = false, // D34
    val showLabel: Boolean = true, // D28
)

/**
 * The Parent settings (D40): loaded once at app start, held in memory, written through on every
 * change. The capture session reads [value] per frame; Compose observes [state].
 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ParentSettings> = _state.asStateFlow()
    val value: ParentSettings get() = _state.value

    fun update(change: (ParentSettings) -> ParentSettings) {
        val s = _state.updateAndGet(change)
        prefs.edit()
            .putString(PRESET, s.preset.name)
            .putString(SENSITIVITY, s.sensitivity.name)
            .putBoolean(PEEK, s.peekUnderMask)
            .putBoolean(LABEL, s.showLabel)
            .apply()
    }

    private fun load(): ParentSettings {
        val d = ParentSettings()
        // An unknown name (a renamed enum after an update) falls back to the default.
        return ParentSettings(
            preset = ParentPreset.entries.find { it.name == prefs.getString(PRESET, null) } ?: d.preset,
            sensitivity = Sensitivity.entries.find { it.name == prefs.getString(SENSITIVITY, null) } ?: d.sensitivity,
            peekUnderMask = prefs.getBoolean(PEEK, d.peekUnderMask),
            showLabel = prefs.getBoolean(LABEL, d.showLabel),
        )
    }

    private companion object {
        const val PRESET = "preset"
        const val SENSITIVITY = "sensitivity"
        const val PEEK = "peek_under_mask"
        const val LABEL = "show_label"
    }
}
