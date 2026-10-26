package dev.sophiel.remote

import dev.sophiel.SophielApp
import androidx.compose.runtime.Composable
import dev.sophiel.remote.parent.ParentGraph
import dev.sophiel.ui.Door

/** The one seam from `main` into Remote (D83). Same object and signatures as `src/offline`. */
object Remote {
    @Composable
    fun Gate(app: @Composable () -> Unit) = RoleGate(app)

    fun attach(app: SophielApp) {
        RemoteState.init(app)
        ParentGraph.init(app)
        ChildSocket.start(app) // opens nothing until paired
    }

    @Composable
    fun SettingsRow(door: Door) = PairingRow(door)

    @Composable
    fun StatusLine() = PairedLine()

    @Composable
    fun StatusRecommended() = BatteryRecommended()
}
