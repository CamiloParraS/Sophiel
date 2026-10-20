package dev.sophiel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** D65, D70: one fixed brand ink (Moss, a dark grey since D70), no Material You. State colours are fixed, from the mockup's `--green*`, `--orange*`, `--red*`. */
object Palette {
    val Blue = Color(0xFF3584E4) // Log's LOW band only
    val Moss = Color(0xFF26282C) // the hero's ink: buttons, header, the one primary
    val Green = Color(0xFF2EC27E)
    val GreenFg = Color(0xFF1B8553)
    val Orange = Color(0xFFE66100)
    val OrangeFg = Color(0xFFB34A00)
    val Red = Color(0xFFE01B24)
    val RedFg = Color(0xFFC01C28)

    // On the dark hero and on Moss: the light inks and fills that sit on them.
    val Mint = Color(0xFF6FE6A8)
    val Text0 = Color(0xFFFFFFFF)
    val Text1 = Color(0xFFC2C6CC)
    val OnMoss = Color(0xFFFFFFFF)
    val MossBusy = Color(0xFF45484E)
    val MossOff = Color(0xFF5F636A)
    val DarkInk = Color(0xD9FFFFFF) // icon and secondary text on the hero
    val DarkFill = Color(0x1AFFFFFF) // chip and icon-button fill on the hero
    val DarkLock = Color(0xFFE4E6EA)
    val HeroOff = Color(0xFF32353A)
    val HeroRed = Color(0xFF2E2425) // hero page behind a broken Protection
    val HeroAmber = Color(0xFF2E2922) // hero page behind a stopped one
    val TileOn = Color(0xFF2E3833) // the hero's tile wall, per page
    val TileRed = Color(0xFF3B2E2F)
    val TileAmber = Color(0xFF3B342A)
    val RedInk = Color(0xFFFF8A8F)
    val AmberInk = Color(0xFFFFC08A)
    val MintDim = Color(0xFF53AB7D) // the green held back (Ready)
    val GreenDeep = Color(0xFF287548)
    val Edge = Color(0xFFD3D7DC) // tile outlines on the hero
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
    val Chalk = Color(0xFFECEDEF) // the page: the Status sheet and bottom bar
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
