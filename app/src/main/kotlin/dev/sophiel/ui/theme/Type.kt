package dev.sophiel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.sophiel.R

/** D45: bundled IBM Plex Sans. Mockup rem -> sp at 1 rem = 16 sp, so the system font scale applies. */
val Plex = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

/** Main and large buttons, one step above labelLarge. */
val ButtonBig = style(16, 22, FontWeight.SemiBold)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Plex, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

// The mockup's bold (700/800) maps to SemiBold: Plex ships 400/500/600 only.
val SophielTypography = Typography(
    displayLarge = style(56, 56, FontWeight.SemiBold), // Log masked count
    displayMedium = style(40, 40, FontWeight.SemiBold), // Status today count
    displaySmall = style(34, 36, FontWeight.SemiBold), // hero title
    headlineMedium = style(26, 30, FontWeight.SemiBold), // status page title
    headlineSmall = style(24, 28), // PIN digits
    titleLarge = style(19, 24, FontWeight.SemiBold), // sheet and alert titles
    titleMedium = style(16, 22, FontWeight.SemiBold), // header title, row title
    titleSmall = style(15, 21, FontWeight.SemiBold), // Fix buttons
    bodyLarge = style(16, 23),
    bodyMedium = style(14, 20), // row description
    bodySmall = style(13, 18),
    labelLarge = style(15, 21, FontWeight.SemiBold), // buttons
    labelMedium = style(14, 19, FontWeight.SemiBold), // group title
    labelSmall = style(12, 16, FontWeight.SemiBold),
)
