package dev.sophiel.remote

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

    fun init(application: SophielApp) {
        app = application
        val prefs = app.getSharedPreferences("remote_child", Context.MODE_PRIVATE)
        child = ChildStore(
            read = { prefs.getString(it, null) },
            write = { m -> prefs.edit().apply { m.forEach { (k, v) -> if (v == null) remove(k) else putString(k, v) } }.apply() },
        )
        _role.value = child.role
        _paired.value = child.paired
    }

    fun chooseRole(r: Role) { child.role = r; _role.value = r }

    /** The relay said `paired`: a new pairing keeps token and id and logs Vinculado; a Parent phone joining an existing one changes nothing. */
    fun onPaired(f: ChildFrame.Paired) {
        if (!child.onPaired(f)) return
        _paired.value = true
        app.container.log.paired()
    }

    /** [ChildLink] already deleted key and token and wrote the Log line: the Status line goes, the socket closes. */
    fun unpaired() { _paired.value = false }

    /** Desvincular on this phone: key and token go now; [ChildSocket] tells the relay now or on the next contact. */
    fun unpairFromHere() { if (unpairHere(child, app.container.log)) _paired.value = false }

    fun isBatteryExempt(ctx: Context) = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
}
