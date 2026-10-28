package com.sareemarket.app.ui.reseller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sareemarket.app.data.ItemDetail
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.Spacing

/** What the saree page needs to know about a cart or wishlist change that is on its way. */
data class ShoppingUi(val busy: Boolean = false, val error: String? = null)

private const val LOW_STOCK = 3

/**
 * Quantity stepper and Add to cart, as one block above Call and WhatsApp. A saree already in the cart shows the same
 * stepper bound to the cart quantity (each tap saves) and a Remove button; one that can't be ordered says so.
 */
@Composable
internal fun PurchasePanel(detail: ItemDetail, ui: ShoppingUi, onSetQuantity: (Int) -> Unit, onRemove: () -> Unit) {
    val inCart = detail.inCart
    val max = detail.quantity.coerceAtLeast(1)
    var quantity by remember(detail.id, inCart) { mutableIntStateOf(if (inCart > 0) inCart.coerceAtMost(max) else 1) }
    Column(Modifier.fillMaxWidth().testTag("purchase-panel"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ui.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("shopping-error"))
        }
        if (!detail.inStock) {
            Text(
                "Out of stock",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("out-of-stock"),
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val change = { next: Int ->
                quantity = next
                if (inCart > 0) onSetQuantity(next)
            }
            IconButton(
                onClick = { if (quantity > 1) change(quantity - 1) },
                enabled = quantity > 1 && !ui.busy,
                modifier = Modifier.heightIn(min = MinTouchTarget).size(MinTouchTarget).testTag("qty-minus"),
            ) { Icon(Icons.Filled.Remove, contentDescription = "One less") }
            Text(
                quantity.toString(),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(32.dp).testTag("qty-value"),
            )
            IconButton(
                onClick = { if (quantity < max) change(quantity + 1) },
                enabled = quantity < max && !ui.busy,
                modifier = Modifier.heightIn(min = MinTouchTarget).size(MinTouchTarget).testTag("qty-plus"),
            ) { Icon(Icons.Filled.Add, contentDescription = "One more") }
            if (detail.quantity <= LOW_STOCK) {
                Text("Only ${detail.quantity} left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("low-stock"))
            }
        }
        if (inCart > 0) {
            TextButton(onClick = onRemove, enabled = !ui.busy, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("remove-from-cart")) {
                Text("Remove from cart")
            }
        } else {
            PrimaryButton("Add to cart", onClick = { onSetQuantity(quantity) }, loading = ui.busy, modifier = Modifier.testTag("add-to-cart"))
        }
    }
}
