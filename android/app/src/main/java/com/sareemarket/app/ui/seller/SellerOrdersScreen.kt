package com.sareemarket.app.ui.seller

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.Inbox
import com.sareemarket.app.ui.components.TonalButton
import com.sareemarket.app.ui.components.ScreenHeader
import com.sareemarket.app.ui.components.EmptyState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenHeader("Orders")
            Column(Modifier.weight(1f).padding(horizontal = Spacing.margin), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (!hasUpiId) UpiBanner(onOpenSettings)
            FilterTiles(state.summary, state.filter, onFilter)
            state.error?.let { if (state.items.isNotEmpty()) Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error")) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> SkeletonRows()
                    state.items.isEmpty() && state.error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
                    }
                    state.items.isEmpty() -> EmptyState(
                        Icons.Filled.Inbox,
                        when (state.filter) {
                            SellerOrderFilter.TO_CONFIRM -> "No payments to check."
                            SellerOrderFilter.TO_PACK -> "Nothing to pack."
                            SellerOrderFilter.TO_SHIP -> "Nothing waiting to be shipped."
                            SellerOrderFilter.SHIPPED -> "Nothing on its way."
                            SellerOrderFilter.ALL -> "No orders yet."
                        },
                        if (state.filter == SellerOrderFilter.TO_CONFIRM) "When a customer pays you, the order shows here." else null,
                        Modifier.align(Alignment.Center),
                        titleTag = "orders-empty",
                    )
                    else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        OrderList(state, now, onCall, onWhatsApp, onPack, onDeliver, { ask = it }, { proofOf = it }, onLoadMore)
                    }
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

/** The five states an order passes through, as chips with their counts; each filters the list under it. They wrap, so none is hidden off the edge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterTiles(summary: OrderSummary, selected: SellerOrderFilter, onFilter: (SellerOrderFilter) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().testTag("summary"), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf(
            Triple(SellerOrderFilter.TO_CONFIRM, "To confirm", summary.toConfirm),
            Triple(SellerOrderFilter.TO_PACK, "To pack", summary.toPack),
            Triple(SellerOrderFilter.TO_SHIP, "To ship", summary.toShip),
            Triple(SellerOrderFilter.SHIPPED, "Shipped", summary.shipped),
            Triple(SellerOrderFilter.ALL, "All", -1),
        ).forEach { (filter, label, count) ->
            // "All" has no count of its own. The count is its own text so it can be read apart from the label.
            FilterChip(
                selected = selected == filter,
                onClick = { onFilter(filter) },
                label = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        if (count >= 0) Text(count.toString(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("count-$label"))
                    }
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
                    selectedLabelColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                border = null,
                modifier = Modifier.heightIn(min = MinTouchTarget).testTag("tile-${filter.name.lowercase().replace('_', '-')}"),
            )
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
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth().testTag("order-${order.id}")) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Column(Modifier.weight(1f)) {
                    Text(order.customer?.name ?: "Customer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("customer-${order.id}"))
                    order.customer?.phone?.let { Text("+91 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (order.unread) Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(10.dp).semantics { contentDescription = "Updated" }.testTag("unread-${order.id}")) {}
                OrderStatusPill(order.status, Modifier.testTag("status-${order.id}"))
            }
            val first = order.lines.firstOrNull()?.title.orEmpty()
            Text(
                if (order.lines.size > 1) "$first +${order.lines.size - 1} more" else first,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // A finished order needs nothing from the seller, so it shrinks to the total and the date.
            val closed = order.status == OrderStatus.DELIVERED || order.status == OrderStatus.CANCELLED
            if (!closed) {
                Row {
                    Text("${order.address.city} ${order.address.pin}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(timeAgo(order.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatRupees(order.grandTotal), Modifier.weight(1f).testTag("total-${order.id}"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primaryContainer)
                if (closed) Text(timeAgo(order.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                order.customer?.phone?.let { phone ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onCall(phone) }, modifier = Modifier.testTag("call")) { Icon(Icons.Filled.Phone, contentDescription = "Call ${order.customer?.name ?: "customer"}", tint = MaterialTheme.colorScheme.primaryContainer) }
                        IconButton(onClick = { onWhatsApp(phone) }, modifier = Modifier.testTag("whatsapp")) { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp ${order.customer?.name ?: "customer"}", tint = MaterialTheme.colorScheme.primaryContainer) }
                    }
                }
            }
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
                TonalButton("Not found", onClick = { ask(Ask.Reject(order)) }, enabled = !busy, modifier = Modifier.testTag("act-reject-$id"))
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
