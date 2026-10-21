package dev.sophiel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.sophiel.R

/** D75: bundled Geist (OFL), replacing D45's Plex. Mockup rem -> sp at 1 rem = 16 sp, so the system font scale applies. */
val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

/** Main and large buttons, one step above labelLarge. */
val ButtonBig = style(16, 22, FontWeight.SemiBold)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Geist, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

// The mockup's 800 maps to Bold (Geist ships 400-700 here); big figures use [Display].
val SophielTypography = Typography(
    displayLarge = style(56, 56, FontWeight.Bold), // Log masked count
    displayMedium = style(46, 46, FontWeight.Bold), // Status today count
    displaySmall = style(40, 42, FontWeight.Bold), // hero title
    headlineMedium = style(28, 32, FontWeight.SemiBold), // status page title
    headlineSmall = style(24, 28), // PIN digits
    titleLarge = style(21, 26, FontWeight.SemiBold), // sheet and alert titles
    titleMedium = style(17, 22, FontWeight.SemiBold), // header title, row title
    titleSmall = style(15, 21, FontWeight.SemiBold), // Fix buttons
    bodyLarge = style(16, 23),
    bodyMedium = style(14, 20), // row description
    bodySmall = style(13, 18),
    labelLarge = style(15, 21, FontWeight.SemiBold), // buttons
    labelMedium = style(14, 19, FontWeight.SemiBold), // group title
    labelSmall = style(12, 16, FontWeight.SemiBold),
)
