package dev.sophiel.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** D44: only the accent follows Material You. State colours are fixed, from the mockup's `--green*`, `--orange*`, `--red*`. */
object Palette {
    val Blue = Color(0xFF3584E4)
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
    val Border = Ink.copy(alpha = .12f)
    val Hover = Ink.copy(alpha = .06f)
    val Btn = Ink.copy(alpha = .07f)
    val Win = Color(0xFFFAFAFA)
    val Head = Color(0xFFEBEBEB)
    val Card = Color.White

    /** Mockup `.tag.ok`, `.ec.ok` and the `.big` disc: a state colour at low alpha. */
    fun tint(c: Color, alpha: Float = .16f) = c.copy(alpha = alpha)
}

private val FallbackAccent = lightColorScheme(
    primary = Palette.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFEBFB), // blue at .16 over white
    onPrimaryContainer = Color(0xFF1A5FB4),
)

/** Light only: the system dark mode is ignored on purpose (D43). */
@Composable
fun SophielTheme(content: @Composable () -> Unit) {
    val accent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(LocalContext.current) else FallbackAccent
    val scheme = lightColorScheme(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer,
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
