package dev.sophiel.remote

import android.app.Application
import androidx.compose.runtime.Composable
import dev.sophiel.ui.Door

/** The one seam from `main` into Remote (D83). Same object and signatures as `src/offline`. */
object Remote {
    @Composable
    fun Gate(app: @Composable () -> Unit) = RoleGate(app)

    fun attach(app: Application) = RemoteState.init(app)

    @Composable
    fun SettingsRow(door: Door) = PairingRow(door)

    @Composable
    fun StatusLine() = PairedLine()

    @Composable
    fun StatusRecommended() = BatteryRecommended()
}
