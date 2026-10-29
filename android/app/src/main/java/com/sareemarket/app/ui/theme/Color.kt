package com.sareemarket.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Heritage palette of docs/design/stitch/v2 (ivory canvas, temple crimson, antique gold). It overrides the cool blue-white
// surfaces and the #8B1E41 primary of DESIGN.md; type scale, spacing and component rules still come from DESIGN.md.
val SareeColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF941B2F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF941B2F),
    onPrimaryContainer = Color(0xFFFF9DB2),
    inversePrimary = Color(0xFFFFB1C0),
    secondary = Color(0xFFC59B27),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF92400E),
    tertiary = Color(0xFF003C16),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF005622),
    onTertiaryContainer = Color(0xFF53D171),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFAF8F5),
    onBackground = Color(0xFF0B1C30),
    surface = Color(0xFFFAF8F5),
    onSurface = Color(0xFF0B1C30),
    surfaceVariant = Color(0xFFEAE6DF),
    onSurfaceVariant = Color(0xFF564145),
    surfaceTint = Color(0xFFA63455),
    inverseSurface = Color(0xFF213145),
    inverseOnSurface = Color(0xFFEAF1FF),
    outline = Color(0xFF897175),
    outlineVariant = Color(0xFFEAE6DF),
    surfaceDim = Color(0xFFE4DDD0),
    surfaceBright = Color(0xFFFAF8F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5EFE6),
    surfaceContainer = Color(0xFFF0E9DD),
    surfaceContainerHigh = Color(0xFFEAE2D3),
    surfaceContainerHighest = Color(0xFFE4DBCB),
)

// Fixed colours named in the DESIGN.md prose that have no Material 3 role.
object SareeColors {
    val WhatsApp = Color(0xFF25D366)
    val Available = Color(0xFF16A34A)

    /** Text versions of the status colours, dark enough to read on their own pale tint (6.4:1 and 6.7:1). */
    val AvailableText = Color(0xFF166534)
    /** Amber for something waiting on someone (an order awaiting the seller), with a dark text version. */
    val OnHold = Color(0xFFD97706)
    val OnHoldText = Color(0xFF92400E)
    val SlateText = Color(0xFF475569)
    val SlateNeutral = Color(0xFF64748B)
    val SurfaceSubtle = Color(0xFFFAF8F5)
    val SurfaceMuted = Color(0xFFF5EFE6)
    val Hairline = Color(0xFFEAE6DF)

    /** Antique gold: secondary facts (counts, certification) and the pulse on live stock. */
    val Gold = Color(0xFFC59B27)
    val GoldBg = Color(0xFFFEF3C7)
    val GoldText = Color(0xFF92400E)
}
