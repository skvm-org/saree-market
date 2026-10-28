package com.sareemarket.app.ui.reseller

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.Address
import com.sareemarket.app.data.Cart
import com.sareemarket.app.data.CartLine
import com.sareemarket.app.data.CartSeller
import com.sareemarket.app.data.CheckoutResult
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.SecondaryButton
import com.sareemarket.app.ui.components.SkeletonRows
import com.sareemarket.app.ui.components.StatusPill
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.formatRupees
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing
import com.sareemarket.app.ui.timeAgo
import com.sareemarket.app.ui.timeLeft
import com.sareemarket.app.ui.typeAndFabric

/** The customer's cart: one card per seller, each paid for (and shipped) on its own. */
@Composable
fun CartScreen(
    onOpenItem: (Long) -> Unit,
    onOpenHold: (Long) -> Unit,
    onAddAddress: () -> Unit,
    onDiscover: () -> Unit,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Stock and prices move while the customer is elsewhere, and a new address may have been added.
    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        viewModel.refreshAddresses()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.openHoldId) {
        state.openHoldId?.let {
            viewModel.holdOpened()
            onOpenHold(it)
        }
    }
    CartContent(
        state, onOpenItem, onDiscover, viewModel::setQuantity, viewModel::remove, viewModel::startCheckout,
        viewModel::reload, viewModel::pullRefresh, onOpenHold,
    )
    state.checkout?.let { ui ->
        CheckoutSheet(ui, viewModel::selectAddress, onAddAddress, viewModel::onNote, viewModel::confirmCheckout, viewModel::dismissCheckout)
    }
}

@Composable
fun CartContent(
    state: CartState,
    onOpenItem: (Long) -> Unit,
    onDiscover: () -> Unit,
    onSetQuantity: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onPay: (Long) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onOpenHold: (Long) -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.margin)) {
            Text("Cart", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = Spacing.md).testTag("title"))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> SkeletonRows()
                    state.cart.sellers.isEmpty() && state.error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
                    }
                    state.cart.sellers.isEmpty() -> Column(Modifier.align(Alignment.Center).testTag("cart-empty"), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Your cart is empty", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Add sarees you like and pay each seller in one go.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = Spacing.sm),
                        )
                        PrimaryButton("Browse sarees", onClick = onDiscover, modifier = Modifier.testTag("browse"))
                    }
                    else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                            contentPadding = PaddingValues(bottom = Spacing.lg),
                            modifier = Modifier.fillMaxSize().testTag("cart"),
                        ) {
                            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error")) } }
                            items(state.cart.sellers, key = { it.sellerId }) { seller ->
                                SellerGroup(seller, state.busyItemId, state.holdBySeller[seller.sellerId], onOpenItem, onSetQuantity, onRemove, onPay, onOpenHold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SellerGroup(
    seller: CartSeller,
    busyItemId: Long?,
    holdId: Long?,
    onOpenItem: (Long) -> Unit,
    onSetQuantity: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onPay: (Long) -> Unit,
    onOpenHold: (Long) -> Unit,
) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = CardBorder, modifier = Modifier.fillMaxWidth().testTag("seller-${seller.sellerId}")) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(seller.shopName, style = MaterialTheme.typography.titleLarge)
            seller.lines.forEach { line -> CartLineRow(line, busyItemId == line.itemId, onOpenItem, onSetQuantity, onRemove) }
            Row {
                Text("Shipping", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(formatRupees(seller.shippingFee), style = MaterialTheme.typography.bodyMedium)
            }
            Row {
                Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(formatRupees(seller.grandTotal), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("total-${seller.sellerId}"))
            }
            when {
                // These sarees are already reserved for the customer's open payment, which is why the cart may show them short.
                holdId != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Payment in progress", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("in-progress-${seller.sellerId}"))
                    PrimaryButton("Continue payment", onClick = { onOpenHold(holdId) }, modifier = Modifier.fillMaxWidth().testTag("continue-hold-${seller.sellerId}"))
                }
                !seller.acceptsOrders -> Text(
                    "Not accepting orders yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("not-accepting-${seller.sellerId}"),
                )
                seller.grandTotal <= 0 -> Text(
                    "Nothing in stock",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("nothing-in-stock-${seller.sellerId}"),
                )
                else -> PrimaryButton("Pay ${seller.shopName}", onClick = { onPay(seller.sellerId) }, modifier = Modifier.fillMaxWidth().testTag("pay-${seller.sellerId}"))
            }
        }
    }
}

@Composable
private fun CartLineRow(line: CartLine, busy: Boolean, onOpenItem: (Long) -> Unit, onSetQuantity: (Long, Int) -> Unit, onRemove: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("line-${line.itemId}"), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SareeImage(
            imageUrl(line.itemId, thumb = true),
            contentDescription = line.colour ?: line.collectionTitle,
            modifier = Modifier.width(72.dp).alpha(if (line.inStock) 1f else 0.5f).clickable(onClickLabel = "Open saree", role = Role.Button) { onOpenItem(line.itemId) },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(line.collectionTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            line.colour?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                formatRupees(line.unitPrice),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primaryContainer,
                textDecoration = if (line.inStock) null else TextDecoration.LineThrough,
            )
            if (!line.inStock) {
                Text("Out of stock", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("out-of-stock-${line.itemId}"))
            } else {
                if (line.quantity > line.maxQuantity) {
                    Text("Only ${line.maxQuantity} available", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("only-available-${line.itemId}"))
                }
                val shown = minOf(line.quantity, line.maxQuantity)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSetQuantity(line.itemId, shown - 1) }, enabled = shown > 1 && !busy, modifier = Modifier.size(MinTouchTarget).testTag("cart-minus-${line.itemId}")) {
                        Icon(Icons.Filled.Remove, contentDescription = "One less")
                    }
                    Text(shown.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp).testTag("cart-qty-${line.itemId}"), textAlign = TextAlign.Center)
                    IconButton(onClick = { onSetQuantity(line.itemId, shown + 1) }, enabled = shown < line.maxQuantity && !busy, modifier = Modifier.size(MinTouchTarget).testTag("cart-plus-${line.itemId}")) {
                        Icon(Icons.Filled.Add, contentDescription = "One more")
                    }
                }
            }
        }
        IconButton(onClick = { onRemove(line.itemId) }, enabled = !busy, modifier = Modifier.size(MinTouchTarget).testTag("cart-remove-${line.itemId}")) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove from cart")
        }
    }
}
