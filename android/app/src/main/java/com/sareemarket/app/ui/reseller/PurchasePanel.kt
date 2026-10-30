package com.sareemarket.app.ui.reseller

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.sareemarket.app.ui.components.SecondaryButton
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing

/** What the saree page needs to know about a cart or wishlist change that is on its way. */
data class ShoppingUi(val busy: Boolean = false, val error: String? = null)

internal const val LOW_STOCK = 3

/**
 * The buying controls, pinned at the bottom. One quantity row (label and stock note on the left, a pill stepper on the
 * right), then two equal buttons with a single dominant one: Add to cart and Buy now, or, once the saree is in the cart,
 * Remove and Go to cart. The stepper is bound to the cart quantity once the saree is in it (each tap saves).
 * A saree that can't be ordered says so instead.
 */
@Composable
internal fun PurchasePanel(detail: ItemDetail, ui: ShoppingUi, onSetQuantity: (Int) -> Unit, onRemove: () -> Unit, onBuyNow: (Int) -> Unit = {}) {
    val inCart = detail.inCart
    val max = detail.quantity.coerceAtLeast(1)
    var quantity by remember(detail.id, inCart) { mutableIntStateOf(if (inCart > 0) inCart.coerceAtMost(max) else 1) }
    Column(Modifier.fillMaxWidth().testTag("purchase-panel"), verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
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
        // With one piece left there is nothing to choose, so the row is left out.
        if (max > 1) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Quantity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val change = { next: Int ->
                    quantity = next
                    if (inCart > 0) onSetQuantity(next)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, SareeColors.Hairline)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (quantity > 1) change(quantity - 1) },
                            enabled = quantity > 1 && !ui.busy,
                            modifier = Modifier.size(MinTouchTarget).testTag("qty-minus"),
                        ) { Icon(Icons.Filled.Remove, contentDescription = "One less") }
                        Text(
                            quantity.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(28.dp).testTag("qty-value"),
                        )
                        IconButton(
                            onClick = { if (quantity < max) change(quantity + 1) },
                            enabled = quantity < max && !ui.busy,
                            modifier = Modifier.size(MinTouchTarget).testTag("qty-plus"),
                        ) { Icon(Icons.Filled.Add, contentDescription = "One more") }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
            if (inCart > 0) {
                SecondaryButton("Remove", onClick = onRemove, enabled = !ui.busy, modifier = Modifier.weight(1f).testTag("remove-from-cart"))
                PrimaryButton("Go to cart", onClick = { onBuyNow(quantity) }, enabled = !ui.busy, modifier = Modifier.weight(1f).testTag("go-to-cart"))
            } else {
                SecondaryButton("Add to cart", onClick = { onSetQuantity(quantity) }, enabled = !ui.busy, modifier = Modifier.weight(1f).testTag("add-to-cart"))
                PrimaryButton("Buy now", onClick = { onBuyNow(quantity) }, loading = ui.busy, modifier = Modifier.weight(1f).testTag("buy-now"))
            }
        }
    }
}
