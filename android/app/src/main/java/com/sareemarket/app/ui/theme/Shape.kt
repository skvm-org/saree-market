package com.sareemarket.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// v2 rounds everything more: buttons and fields are pills, cards about 28 dp, photos 20 dp.
val SareeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val PillShape = CircleShape

/** Cards and grouped panels. */
val CardShape = RoundedCornerShape(28.dp)

/** Photos inside cards and grids. */
val PhotoShape = RoundedCornerShape(20.dp)
