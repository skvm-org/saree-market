package com.sareemarket.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sareemarket.app.R

// Variable fonts bundled in res/font (OFL). One file per family, weight picked by variation.
private fun family(res: Int) = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

val Bricolage = family(R.font.bricolage_grotesque)
val WorkSans = family(R.font.work_sans)

private fun style(f: FontFamily, w: Int, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = f,
    fontWeight = FontWeight(w),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

// Scale from docs/design/DESIGN.md.
val SareeTypography = Typography(
    displayLarge = style(Bricolage, 700, 40, 48, -0.02),
    displayMedium = style(Bricolage, 700, 30, 38, -0.01), // display-lg-mobile
    headlineLarge = style(Bricolage, 600, 28, 36, -0.015),
    headlineMedium = style(Bricolage, 600, 22, 30, -0.01),
    headlineSmall = style(Bricolage, 600, 18, 26),
    titleLarge = style(Bricolage, 600, 18, 26),
    titleMedium = style(WorkSans, 600, 16, 24),
    titleSmall = style(WorkSans, 600, 14, 20, 0.01),
    bodyLarge = style(WorkSans, 400, 16, 24),
    bodyMedium = style(WorkSans, 400, 14, 20),
    bodySmall = style(WorkSans, 400, 12, 18),
    labelLarge = style(WorkSans, 600, 14, 20, 0.01),
    labelMedium = style(WorkSans, 500, 12, 16, 0.02),
    labelSmall = style(WorkSans, 600, 11, 14, 0.04),
)

val PriceHero = style(Bricolage, 700, 24, 32, -0.01)
