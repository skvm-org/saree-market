package com.sareemarket.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sareemarket.app.ui.theme.PillShape
import com.sareemarket.app.ui.theme.Spacing

/** Pill badge from DESIGN.md: tinted background, solid dot, label. */
@Composable
fun StatusPill(text: String, target: Color, modifier: Modifier = Modifier) {
    // The pill eases between its colours when an item flips between available and sold out.
    val color by animateColorAsState(target, label = "pill")
    Surface(
        shape = PillShape,
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm + Spacing.xs, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs + Spacing.xs),
        ) {
            Surface(modifier = Modifier.size(6.dp), shape = CircleShape, color = color) {}
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
