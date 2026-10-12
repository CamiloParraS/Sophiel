package dev.sophiel.settings

import android.content.Context

/** Protection on/off (D54): on disk, so it survives process death and reboot. Set only by the Parent's Start and PIN Stop. */
class ProtectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("protection", Context.MODE_PRIVATE)

    var protecting: Boolean
        get() = prefs.getBoolean(KEY, false)
        set(v) = prefs.edit().putBoolean(KEY, v).apply()

    private companion object {
        const val KEY = "protecting"
    }
}
