package com.sareemarket.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Tokens from docs/design/DESIGN.md (YAML front matter).
val SareeColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF6C002B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8B1E41),
    onPrimaryContainer = Color(0xFFFF9DB2),
    inversePrimary = Color(0xFFFFB1C0),
    secondary = Color(0xFF006D2F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF5DFD8A),
    onSecondaryContainer = Color(0xFF007232),
    tertiary = Color(0xFF003C16),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF005622),
    onTertiaryContainer = Color(0xFF53D171),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF0B1C30),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF0B1C30),
    surfaceVariant = Color(0xFFD3E4FE),
    onSurfaceVariant = Color(0xFF564145),
    surfaceTint = Color(0xFFA63455),
    inverseSurface = Color(0xFF213145),
    inverseOnSurface = Color(0xFFEAF1FF),
    outline = Color(0xFF897175),
    outlineVariant = Color(0xFFDCBFC3),
    surfaceDim = Color(0xFFCBDBF5),
    surfaceBright = Color(0xFFF8F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF4FF),
    surfaceContainer = Color(0xFFE5EEFF),
    surfaceContainerHigh = Color(0xFFDCE9FF),
    surfaceContainerHighest = Color(0xFFD3E4FE),
)

// Fixed colours named in the DESIGN.md prose that have no Material 3 role.
object SareeColors {
    val WhatsApp = Color(0xFF25D366)
    val Available = Color(0xFF16A34A)
    val SlateNeutral = Color(0xFF64748B)
    val SurfaceSubtle = Color(0xFFF8F9FA)
    val SurfaceMuted = Color(0xFFF1F3F5)
    val Hairline = Color(0xFFE2E8F0)
}
