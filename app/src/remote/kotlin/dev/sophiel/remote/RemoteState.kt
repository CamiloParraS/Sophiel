package dev.sophiel.remote

import android.app.Application
import android.content.Context
import android.os.PowerManager
import dev.sophiel.SophielApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-wide Remote state for the Child role, filled by [Remote.attach]. App-private prefs `remote_child`. */
object RemoteState {
    private lateinit var app: SophielApp
    lateinit var child: ChildStore
        private set
    private val _role = MutableStateFlow<Role?>(null)
    private val _paired = MutableStateFlow(false)
    val role: StateFlow<Role?> get() = _role
    val paired: StateFlow<Boolean> get() = _paired

    fun init(application: Application) {
        app = application as SophielApp
        val prefs = app.getSharedPreferences("remote_child", Context.MODE_PRIVATE)
        child = ChildStore(
            read = { prefs.getString(it, null) },
            write = { m -> prefs.edit().apply { m.forEach { (k, v) -> if (v == null) remove(k) else putString(k, v) } }.apply() },
        )
        _role.value = child.role
        _paired.value = child.paired
    }

    fun chooseRole(r: Role) { child.role = r; _role.value = r }

    /** The relay said `paired`: token and id kept (the key was made before the QR), a Vinculado line in the Log. */
    fun onPaired(pairingId: String, deviceToken: String) {
        child.savePaired(pairingId, deviceToken)
        _paired.value = true
        app.container.log.paired()
    }

    fun isBatteryExempt(ctx: Context) = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
}
