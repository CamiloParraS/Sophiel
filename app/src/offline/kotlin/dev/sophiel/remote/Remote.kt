package dev.sophiel.remote

import dev.sophiel.SophielApp
import androidx.compose.runtime.Composable
import dev.sophiel.ui.Door

/** The one seam from `main` into Remote (D83). Empty in `offline`; the `remote` flavor has the same object with the same signatures. */
object Remote {
    /** First-launch routing: wraps today's app. `remote` may show the role choice or the Parent screens instead of [app]. */
    @Composable
    fun Gate(app: @Composable () -> Unit) = app()

    /** Entry point called once from `Application.onCreate`: where `remote` wires the socket and the command handler. */
    fun attach(app: SophielApp) = Unit

    /** The Ajustes row "Teléfono del Padre". */
    @Composable
    fun SettingsRow(door: Door) = Unit

    /** The Status line "Vinculado con el teléfono del Padre". */
    @Composable
    fun StatusLine() = Unit

    /** The Recomendado item "Sin restricción de batería" (paired and not exempt). */
    @Composable
    fun StatusRecommended() = Unit
}
