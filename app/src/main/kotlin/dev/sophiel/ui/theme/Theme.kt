package dev.sophiel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** D65, D70, D75: one fixed brand ink (Moss, a dark emerald), no Material You. State colours are fixed: Esmeralda for protected, the mockup's orange and red for the rest. */
object Palette {
    val Blue = Color(0xFF3584E4) // Log's LOW band only
    val Moss = Color(0xFF08201A) // the hero's ink: buttons, header, the one primary
    val Green = Color(0xFF3FD08F) // sig2: green on dark, and the Ok tag fill
    val GreenFg = Color(0xFF00804A) // Esmeralda (D75): white on it 5.0:1; green text on white 5.0:1
    val Orange = Color(0xFFE66100)
    val OrangeFg = Color(0xFFB34A00)
    val Red = Color(0xFFE01B24)
    val RedFg = Color(0xFFC01C28)

    // On the dark hero and on Moss: the light inks and fills that sit on them.
    val Mint = Color(0xFF3FD08F)
    val Text0 = Color(0xFFF0F7F3)
    val Text1 = Color(0xFFA9C6BA)
    val OnMoss = Color(0xFFF0F7F3)
    val MossBusy = Color(0xFF2E4A40)
    val MossOff = Color(0xFF5C7168)
    val DarkInk = Color(0xBFDDEBD3) // icon and secondary text on the hero
    val DarkFill = Color(0x12FFFFFF) // chip and icon-button fill on the hero
    val DarkLock = Color(0xFFCFE3C2)
    val HeroOff = Color(0xFF12281F)
    val HeroRed = Color(0xFF2A1517) // hero page behind a broken Protection
    val HeroAmber = Color(0xFF2A2115) // hero page behind a stopped one
    val TileOn = Color(0xFF0F3226) // the hero's tile wall, per page
    val TileRed = Color(0xFF3A1F22)
    val TileAmber = Color(0xFF3B2F1E)
    val RedInk = Color(0xFFFF8A8F)
    val AmberInk = Color(0xFFFFC08A)
    val MintDim = Color(0xFF3A9C6E) // the green held back (Ready)
    val GreenDeep = Color(0xFF0C4A33)
    val Edge = Color(0xFFBFD8A8) // tile outlines on the hero
    val MaskRed = Color(0x99E41826)
    val MaskAmber = Color(0x8CEA6200)
    val Speck = Color(0x14FFFFFF)
    val Quiet = Moss.copy(alpha = .16f) // a day with nothing, in the week strip

    // Neutrals (D70): a dark grey ink, never black, at full strength for text.
    internal val Ink = Color(0xFF1C1E21)
    val Fg = Ink
    val Dim = Ink.copy(alpha = .74f) // ~6.5:1 on Chalk
    const val DISABLED = .7f // a disabled row stays legible; the reason line and the dead tap say why
    val Border = Ink.copy(alpha = .10f)
    val Hover = Ink.copy(alpha = .06f)
    val Btn = Ink.copy(alpha = .07f)
    // D64, D70: two roles. Page is the cool light grey behind everything; Surface is pure white and rises off it
    // (cards, tiles, sheets, dialogs, the selected pill).
    val Surface = Color(0xFFFFFFFF)
    val Chalk = Color(0xFFF1F2F1) // the page: the Status sheet and bottom bar
    val Win = Surface
    val Card = Surface
    val Head = Chalk

    /** Mockup `.tag.ok`, `.ec.ok` and the `.big` disc: a state colour at low alpha. */
    fun tint(c: Color, alpha: Float = .16f) = c.copy(alpha = alpha)
}

/** A card's lift off the page: the border's job now. */
fun Modifier.lift(shape: Shape) = shadow(2.dp, shape, ambientColor = Palette.Ink.copy(alpha = .18f), spotColor = Palette.Ink.copy(alpha = .18f))

/** Light only: the system dark mode is ignored on purpose (D43). */
@Composable
fun SophielTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Palette.Moss,
        onPrimary = Palette.Text0,
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
