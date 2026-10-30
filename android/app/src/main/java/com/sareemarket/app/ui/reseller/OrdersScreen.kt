package com.sareemarket.app.ui.reseller

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Inventory2
import com.sareemarket.app.ui.components.EmptyState
import com.sareemarket.app.ui.components.ScreenHeader
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.Hold
import com.sareemarket.app.data.Order
import com.sareemarket.app.data.OrderStatus
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.components.OrderStatusPill
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
import com.sareemarket.app.ui.timeRemaining
import com.sareemarket.app.ui.typeAndFabric
import java.time.Instant
import kotlinx.coroutines.delay

private const val LOAD_MORE_AHEAD_ORDERS = 4

/** The customer's payments in progress and their orders. */
@Composable
fun OrdersScreen(onOpenOrder: (Long) -> Unit, onOpenHold: (Long) -> Unit, onBrowse: () -> Unit = {}, viewModel: OrdersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Sellers answer while another tab is open, so look again whenever this tab comes back.
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
    OrdersContent(state, now, onOpenOrder, onOpenHold, viewModel::loadMore, viewModel::reload, viewModel::pullRefresh, onBrowse)
}

@Composable
fun OrdersContent(
    state: OrdersState,
    now: Instant,
    onOpenOrder: (Long) -> Unit,
    onOpenHold: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onBrowse: () -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenHeader("My orders")
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.margin)) {
                when {
                    state.loading -> SkeletonRows()
                    state.items.isEmpty() && state.holds.isEmpty() && state.error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
                    }
                    state.items.isEmpty() && state.holds.isEmpty() -> EmptyState(
                        Icons.Filled.Inventory2,
                        "No orders yet",
                        "Add a saree to your cart and pay the seller. Orders show up here.",
                        Modifier.align(Alignment.Center).testTag("orders-empty"),
                        actionLabel = "Browse sarees",
                        onAction = onBrowse,
                    )
                    else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        OrdersList(state, now, onOpenOrder, onOpenHold, onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrdersList(state: OrdersState, now: Instant, onOpenOrder: (Long) -> Unit, onOpenHold: (Long) -> Unit, onLoadMore: () -> Unit) {
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
        items(state.holds, key = { "hold-${it.id}" }) { hold -> HoldCard(hold, now, onOpenHold) }
        // Orders that need the customer come first; the sort is stable, so the rest keep the server's order.
        items(state.items.sortedBy { it.status != OrderStatus.PAYMENT_REJECTED }, key = { it.id }) { order -> OrderRow(order, now, onOpenOrder) }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error")) } }
        if (state.loadingMore) {
            item { Box(Modifier.fillMaxWidth().padding(Spacing.md), Alignment.Center) { CircularProgressIndicator() } }
        }
    }
}

@Composable
private fun HoldCard(hold: Hold, now: Instant, onOpenHold: (Long) -> Unit) {
    val left = timeRemaining(hold.expiresAt, now)
    Surface(
        shape = CardShape,
        color = SareeColors.GoldBg,
        border = CardBorder,
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Finish paying", role = Role.Button) { onOpenHold(hold.id) }.testTag("hold-${hold.id}"),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Pay ${hold.shopName}", style = MaterialTheme.typography.titleMedium)
            Text("${formatRupees(hold.grandTotal)} · ${if (left.isEmpty()) "time is up" else "pay within $left"}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun OrderRow(order: Order, now: Instant, onOpenOrder: (Long) -> Unit) {
    val needsYou = order.status == OrderStatus.PAYMENT_REJECTED
    Surface(
        shape = CardShape,
        color = if (needsYou) SareeColors.GoldBg else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = CardBorder,
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open order", role = Role.Button) { onOpenOrder(order.id) }.testTag("order-${order.id}"),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(order.shopName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (order.unread) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(10.dp).semantics { contentDescription = "Updated" }.testTag("unread-${order.id}")) {}
                }
            }
            val first = order.lines.firstOrNull()?.title.orEmpty()
            Text(
                if (order.lines.size > 1) "$first +${order.lines.size - 1} more" else first,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // What happens next, in words, so the list answers "does this need me?" without opening the order.
            val next = when (order.status) {
                OrderStatus.PAYMENT_REJECTED -> "Send the right UTR" + (order.paymentDeadline?.let { timeRemaining(it, now) }?.takeIf { it.isNotEmpty() }?.let { " within $it" } ?: "")
                OrderStatus.PAYMENT_SUBMITTED -> "Waiting for the seller to check your payment"
                OrderStatus.SHIPPED -> "On its way"
                else -> null
            }
            next?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (needsYou) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("next-${order.id}")) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OrderStatusPill(order.status, Modifier.testTag("status-${order.id}"))
                Text(timeAgo(order.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(formatRupees(order.grandTotal), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primaryContainer)
            }
        }
    }
}
