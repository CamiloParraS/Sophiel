package dev.sophiel.remote

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.sophiel.remote.parent.ParentGraph
import dev.sophiel.remote.parent.ParentRole
import dev.sophiel.remote.parent.ParentRoot
import dev.sophiel.ui.Door

/** The one seam from `main` into Remote (D83). Same object and signatures as `src/offline`; no behaviour yet (M9 tickets 11+). */
object Remote {
    @Composable
    fun Gate(app: @Composable () -> Unit) {
        val role by ParentGraph.roles.role.collectAsState()
        if (role == ParentRole.PARENT) ParentRoot() else app() // D81: the Parent role never sees the wizard, protection or the door
    }

    fun attach(app: Application) {
        ParentGraph.init(app)
    }

    @Composable
    fun SettingsRow(door: Door) = Unit

    @Composable
    fun StatusLine() = Unit
}
