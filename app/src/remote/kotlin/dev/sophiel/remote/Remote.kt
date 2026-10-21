package dev.sophiel.remote

import android.app.Application
import androidx.compose.runtime.Composable
import dev.sophiel.ui.Door

/** The one seam from `main` into Remote (D83). Same object and signatures as `src/offline`; no behaviour yet (M9 tickets 11+). */
object Remote {
    @Composable
    fun Gate(app: @Composable () -> Unit) = app()

    fun attach(app: Application) = Unit

    @Composable
    fun SettingsRow(door: Door) = Unit

    @Composable
    fun StatusLine() = Unit
}
