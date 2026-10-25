package com.sareemarket.app.ui.theme

import androidx.compose.ui.unit.dp

// Spacing scale from DESIGN.md (4 dp multiples).
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 40.dp
    val margin = 16.dp
    val marginMd = 32.dp
    val marginLg = 48.dp
}

// Material 3 minimum touch target; DESIGN.md's 44 px is smaller, so Android uses 48 dp.
val MinTouchTarget = 48.dp

/** Photo grids fit as many columns of at least this width as there is room for: two on a phone, more in landscape or on a tablet. */
val GRID_MIN_CELL = 156.dp
