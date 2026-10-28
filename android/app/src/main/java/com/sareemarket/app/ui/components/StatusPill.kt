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
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.ui.theme.PillShape
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing

/** Pill badge from DESIGN.md: tinted background, solid dot, label. */
@Composable
fun StatusPill(text: String, target: Color, modifier: Modifier = Modifier) {
    // The pill eases between its colours when an item flips between available and sold out.
    val color by animateColorAsState(target, label = "pill")
    // The dot and tint use the status colour; the words use a darker version so they pass contrast on the tint.
    val textColor by animateColorAsState(readableOn(target), label = "pill-text")
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
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = textColor)
        }
    }
}

/** The pill for a saree's stock status. */
@Composable
fun ItemStatusPill(status: ItemStatus, modifier: Modifier = Modifier) = when (status) {
    ItemStatus.AVAILABLE -> StatusPill("Available", SareeColors.Available, modifier)
    ItemStatus.ON_HOLD -> StatusPill("On hold", SareeColors.OnHold, modifier)
    ItemStatus.SOLD_OUT -> StatusPill("Sold out", SareeColors.SlateNeutral, modifier)
}

private fun readableOn(status: Color) = when (status) {
    SareeColors.Available -> SareeColors.AvailableText
    SareeColors.OnHold -> SareeColors.OnHoldText
    SareeColors.SlateNeutral -> SareeColors.SlateText
    else -> status
}

/** The state of an order, in the customer's words: waiting, problem, moving, done. */
@Composable
fun OrderStatusPill(status: com.sareemarket.app.data.OrderStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        com.sareemarket.app.data.OrderStatus.PAYMENT_SUBMITTED -> SareeColors.OnHold
        com.sareemarket.app.data.OrderStatus.PAYMENT_REJECTED, com.sareemarket.app.data.OrderStatus.CANCELLED -> MaterialTheme.colorScheme.error
        com.sareemarket.app.data.OrderStatus.CONFIRMED, com.sareemarket.app.data.OrderStatus.PACKED, com.sareemarket.app.data.OrderStatus.SHIPPED -> SareeColors.SlateNeutral
        com.sareemarket.app.data.OrderStatus.DELIVERED -> SareeColors.Available
    }
    StatusPill(status.label, color, modifier)
}
