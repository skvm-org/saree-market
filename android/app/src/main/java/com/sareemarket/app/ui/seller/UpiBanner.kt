package com.sareemarket.app.ui.seller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing

/** Shown while the shop has no UPI ID: nobody can order until it is set, so the way to fix it sits in the banner. */
@Composable
fun UpiBanner(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = CardShape, color = SareeColors.GoldBg, modifier = modifier.fillMaxWidth().testTag("no-upi-banner")) {
        Row(Modifier.padding(start = Spacing.md, top = Spacing.sm + Spacing.xs, end = Spacing.sm + Spacing.xs, bottom = Spacing.sm + Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = SareeColors.GoldText, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text("Add your UPI ID", style = MaterialTheme.typography.titleSmall, color = SareeColors.GoldText)
                Text("Customers can't order until you do.", style = MaterialTheme.typography.bodySmall, color = SareeColors.GoldText)
            }
            Button(
                onClick = onAdd,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.heightIn(min = MinTouchTarget).testTag("add-upi"),
            ) { Text("Add", style = MaterialTheme.typography.labelLarge) }
        }
    }
}
