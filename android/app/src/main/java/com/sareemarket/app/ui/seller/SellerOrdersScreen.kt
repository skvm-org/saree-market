package com.sareemarket.app.ui.seller

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.data.Order
import com.sareemarket.app.data.OrderStatus
import com.sareemarket.app.data.OrderSummary
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.components.OrderStatusPill
import com.sareemarket.app.ui.components.PhotoViewer
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.SecondaryButton
import com.sareemarket.app.ui.components.SkeletonRows
import com.sareemarket.app.ui.components.StatusPill
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.components.orderProofUrl
import com.sareemarket.app.ui.formatRupees
import com.sareemarket.app.ui.reseller.ContactButtons
import com.sareemarket.app.ui.reseller.rememberContactActions
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing
import com.sareemarket.app.ui.timeAgo
import com.sareemarket.app.ui.timeLeft
import com.sareemarket.app.ui.timeRemaining
import com.sareemarket.app.ui.typeAndFabric
import java.time.Instant
import kotlinx.coroutines.delay

private const val LOAD_MORE_AHEAD_ORDERS = 4

/** What the seller still has to answer before the server is told. */
private sealed interface Ask {
    val order: Order

    data class ConfirmPayment(override val order: Order) : Ask
    data class Reject(override val order: Order) : Ask
    data class Cancel(override val order: Order) : Ask
    data class Ship(override val order: Order) : Ask
}

/** Orders from customers: check the payment, pack, ship, deliver. */
@Composable
fun SellerOrdersScreen(onOpenSettings: () -> Unit, hasUpiId: Boolean, viewModel: SellerOrdersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val (call, whatsapp) = rememberContactActions()
    // Orders arrive while the seller is elsewhere, so look again whenever this tab comes back.
    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose {}
    }
    val now by produceState(Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(30_000L)
        }
    }
    SellerOrdersContent(
        state, hasUpiId, now, onOpenSettings, call, whatsapp, viewModel::selectFilter, viewModel::confirmPayment,
        viewModel::rejectPayment, viewModel::pack, viewModel::ship, viewModel::deliver, viewModel::cancel,
        viewModel::loadMore, viewModel::reload, viewModel::pullRefresh,
    )
}

@Composable
fun SellerOrdersContent(
    state: SellerOrdersState,
    hasUpiId: Boolean,
    now: Instant,
    onOpenSettings: () -> Unit,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onFilter: (SellerOrderFilter) -> Unit,
    onConfirm: (Long) -> Unit,
    onReject: (Long, String) -> Unit,
    onPack: (Long) -> Unit,
    onShip: (Long, String?, String?) -> Unit,
    onDeliver: (Long) -> Unit,
    onCancel: (Long, String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
) {
    var ask by remember { mutableStateOf<Ask?>(null) }
    var proofOf by remember { mutableStateOf<Order?>(null) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.margin), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Orders", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = Spacing.md).testTag("title"))
            if (!hasUpiId) {
                Surface(shape = CardShape, color = SareeColors.GoldBg, modifier = Modifier.fillMaxWidth().testTag("no-upi-banner")) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Add your UPI ID so customers can pay you", style = MaterialTheme.typography.titleSmall)
                        Text("Until you do, nobody can order from your shop.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("add-upi")) { Text("Add UPI ID") }
                    }
                }
            }
            FilterTiles(state.summary, state.filter, onFilter)
            state.error?.let { if (state.items.isNotEmpty()) Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error")) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> SkeletonRows()
                    state.items.isEmpty() && state.error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
                    }
                    state.items.isEmpty() -> Text(
                        when (state.filter) {
                            SellerOrderFilter.TO_CONFIRM -> "No payments to check.\nWhen a customer pays you, the order shows here."
                            SellerOrderFilter.TO_PACK -> "Nothing to pack."
                            SellerOrderFilter.TO_SHIP -> "Nothing waiting to be shipped."
                            SellerOrderFilter.SHIPPED -> "Nothing on its way."
                            SellerOrderFilter.ALL -> "No orders yet."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).testTag("orders-empty"),
                    )
                    else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        OrderList(state, now, onCall, onWhatsApp, onPack, onDeliver, { ask = it }, { proofOf = it }, onLoadMore)
                    }
                }
            }
        }
    }
    when (val a = ask) {
        is Ask.ConfirmPayment -> ConfirmPaymentDialog(a.order, { ask = null }) { ask = null; onConfirm(a.order.id) }
        is Ask.Reject -> ReasonDialog(
            "The payment was not found", "Tell ${a.order.customer?.name ?: "the customer"} what you could not find. They get two hours to send the right UTR.",
            "Send", { ask = null },
        ) { ask = null; onReject(a.order.id, it) }
        is Ask.Cancel -> ReasonDialog(
            "Cancel this order?", "The sarees go back on sale. If the customer paid, you will need to refund them yourself.",
            "Cancel order", { ask = null },
        ) { ask = null; onCancel(a.order.id, it) }
        is Ask.Ship -> ShipDialog({ ask = null }) { courier, tracking -> ask = null; onShip(a.order.id, courier, tracking) }
        null -> Unit
    }
    proofOf?.let { PhotoViewer(orderProofUrl(it.id, it.updatedAt), "Payment screenshot") { proofOf = null } }
}

/** The five states an order passes through, each a button that filters the list under it. */
@Composable
private fun FilterTiles(summary: OrderSummary, selected: SellerOrderFilter, onFilter: (SellerOrderFilter) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.testTag("summary")) {
        val tiles = listOf(
            Triple(SellerOrderFilter.TO_CONFIRM, "To confirm", summary.toConfirm),
            Triple(SellerOrderFilter.TO_PACK, "To pack", summary.toPack),
            Triple(SellerOrderFilter.TO_SHIP, "To ship", summary.toShip),
            Triple(SellerOrderFilter.SHIPPED, "Shipped", summary.shipped),
            Triple(SellerOrderFilter.ALL, "All", -1),
        )
        items(tiles, key = { it.first.name }) { (filter, label, count) ->
            Tile(label, count, selected == filter, { onFilter(filter) }, Modifier.width(92.dp).testTag("tile-${filter.name.lowercase().replace('_', '-')}"))
        }
    }
}

@Composable
private fun Tile(label: String, count: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    // The chosen tile gets a crimson outline and a tint, the same way the filter chips elsewhere show their choice.
    Surface(
        selected = selected,
        onClick = onClick,
        shape = CardShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primaryContainer) else null,
        modifier = modifier.heightIn(min = MinTouchTarget),
    ) {
        Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (count >= 0) count.toString() else "·", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("count-$label"))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun OrderList(
    state: SellerOrdersState,
    now: Instant,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onPack: (Long) -> Unit,
    onDeliver: (Long) -> Unit,
    ask: (Ask) -> Unit,
    onProof: (Order) -> Unit,
    onLoadMore: () -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(list, state.items.size) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= state.items.size - LOAD_MORE_AHEAD_ORDERS) onLoadMore()
        }
    }
    LazyColumn(
        state = list,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
        contentPadding = PaddingValues(bottom = Spacing.lg),
        modifier = Modifier.fillMaxSize().testTag("orders"),
    ) {
        items(state.items, key = { it.id }) { order ->
            OrderCard(order, state.busyId != null, now, onCall, onWhatsApp, onPack, onDeliver, ask, onProof)
        }
        if (state.loadingMore) {
            item { Box(Modifier.fillMaxWidth().padding(Spacing.md), Alignment.Center) { CircularProgressIndicator() } }
        }
    }
}

@Composable
private fun OrderCard(
    order: Order,
    busy: Boolean,
    now: Instant,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onPack: (Long) -> Unit,
    onDeliver: (Long) -> Unit,
    ask: (Ask) -> Unit,
    onProof: (Order) -> Unit,
) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = CardBorder, modifier = Modifier.fillMaxWidth().testTag("order-${order.id}")) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Column(Modifier.weight(1f)) {
                    Text(order.customer?.name ?: "Customer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("customer-${order.id}"))
                    order.customer?.phone?.let { Text("+91 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (order.unread) Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(10.dp).testTag("unread-${order.id}")) {}
                OrderStatusPill(order.status, Modifier.testTag("status-${order.id}"))
            }
            val first = order.lines.firstOrNull()?.title.orEmpty()
            Text(
                if (order.lines.size > 1) "$first +${order.lines.size - 1} more" else first,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                Text("${order.address.city} ${order.address.pin}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(timeAgo(order.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatRupees(order.grandTotal), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.testTag("total-${order.id}"))
            order.paymentReference?.let { utr ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("UTR $utr", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).testTag("utr-${order.id}"))
                    if (order.hasProof) {
                        TextButton(onClick = { onProof(order) }, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("proof-${order.id}")) { Text("View screenshot") }
                    }
                }
            }
            if (order.courier != null || order.trackingNumber != null) {
                Text(
                    listOfNotNull(order.courier?.let { "Courier: $it" }, order.trackingNumber?.let { "Tracking: $it" }).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("tracking-${order.id}"),
                )
            }
            order.customer?.phone?.let { ContactButtons(it, it, onCall, onWhatsApp) }
            Actions(order, busy, now, onPack, onDeliver, ask)
        }
    }
}

@Composable
private fun Actions(order: Order, busy: Boolean, now: Instant, onPack: (Long) -> Unit, onDeliver: (Long) -> Unit, ask: (Ask) -> Unit) {
    val id = order.id
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        when (order.status) {
            OrderStatus.PAYMENT_SUBMITTED -> {
                PrimaryButton("Payment received", onClick = { ask(Ask.ConfirmPayment(order)) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("act-confirm-$id"))
                OutlinedButton(onClick = { ask(Ask.Reject(order)) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("act-reject-$id")) { Text("Not found") }
            }
            OrderStatus.PAYMENT_REJECTED -> {
                val left = order.paymentDeadline?.let { timeRemaining(it, now) }.orEmpty()
                Text(
                    "Waiting for the customer's corrected UTR" + if (left.isNotEmpty()) ", $left left" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("waiting-$id"),
                )
            }
            OrderStatus.CONFIRMED -> PrimaryButton("Mark packed", onClick = { onPack(id) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("act-pack-$id"))
            OrderStatus.PACKED -> PrimaryButton("Ship", onClick = { ask(Ask.Ship(order)) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("act-ship-$id"))
            OrderStatus.SHIPPED -> PrimaryButton("Mark delivered", onClick = { onDeliver(id) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("act-deliver-$id"))
            OrderStatus.DELIVERED -> Unit
            OrderStatus.CANCELLED -> Text(
                "Cancelled by ${when (order.cancelledBy) { "CUSTOMER" -> "the customer"; "SELLER" -> "you"; else -> "the app" }}" + (order.cancelReason?.let { ": $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("cancelled-$id"),
            )
        }
        val cancellable = order.status in setOf(OrderStatus.PAYMENT_SUBMITTED, OrderStatus.PAYMENT_REJECTED, OrderStatus.CONFIRMED, OrderStatus.PACKED)
        if (cancellable) {
            TextButton(onClick = { ask(Ask.Cancel(order)) }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("act-cancel-$id")) { Text("Cancel order") }
        }
    }
}
