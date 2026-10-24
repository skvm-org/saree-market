package com.sareemarket.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

// Light only: DESIGN.md defines no dark palette.
@Composable
fun SareeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SareeColorScheme,
        typography = SareeTypography,
        shapes = SareeShapes,
        content = content,
    )
}
