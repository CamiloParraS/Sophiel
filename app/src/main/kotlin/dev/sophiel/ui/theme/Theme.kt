package dev.sophiel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** D65: one fixed brand ink (Moss), no Material You. State colours are fixed, from the mockup's `--green*`, `--orange*`, `--red*`. */
object Palette {
    val Blue = Color(0xFF3584E4) // Log's LOW band only
    val Moss = Color(0xFF12201A) // the hero's ink: buttons, header, the one primary
    val Green = Color(0xFF2EC27E)
    val GreenFg = Color(0xFF1B8553)
    val Orange = Color(0xFFE66100)
    val OrangeFg = Color(0xFFB34A00)
    val Red = Color(0xFFE01B24)
    val RedFg = Color(0xFFC01C28)

    // Neutrals: rgba(0,0,6,a) over the light window.
    private val Ink = Color(0xFF000006)
    val Fg = Ink.copy(alpha = .8f)
    val Dim = Ink.copy(alpha = .55f)
    val Border = Color(0xFF281E0A).copy(alpha = .12f) // warm, like the surfaces it divides
    val Hover = Ink.copy(alpha = .06f)
    val Btn = Ink.copy(alpha = .07f)
    // D64: two roles. Page is the greige behind everything; Surface is the one off-white that rises off it
    // (cards, tiles, sheets, dialogs, the selected pill). Pure white glared under the dark hero.
    val Surface = Color(0xFFF9F8F5)
    val Chalk = Color(0xFFEAE8E3) // the page: the Status sheet and bottom bar
    val Win = Surface
    val Card = Surface
    val Head = Chalk

    /** Mockup `.tag.ok`, `.ec.ok` and the `.big` disc: a state colour at low alpha. */
    fun tint(c: Color, alpha: Float = .16f) = c.copy(alpha = alpha)
}

/** A card's one-pixel lift off the page, warm like the surfaces: the border's job now. */
fun Modifier.lift(shape: Shape) = shadow(1.dp, shape, ambientColor = Color(0x33281E0A), spotColor = Color(0x33281E0A))

/** Light only: the system dark mode is ignored on purpose (D43). */
@Composable
fun SophielTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Palette.Moss,
        onPrimary = Color(0xFFF2F7EF),
        primaryContainer = Palette.tint(Palette.GreenFg, .14f),
        onPrimaryContainer = Palette.GreenFg,
        background = Palette.Win,
        onBackground = Palette.Fg,
        surface = Palette.Win,
        onSurface = Palette.Fg,
        onSurfaceVariant = Palette.Dim,
        surfaceVariant = Palette.Head,
        surfaceContainer = Palette.Card,
        surfaceContainerHigh = Palette.Head,
        outline = Palette.Border,
        outlineVariant = Palette.Border,
        error = Palette.RedFg,
    )
    MaterialTheme(colorScheme = scheme, typography = SophielTypography, content = content)
}
