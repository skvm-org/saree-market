package com.sareemarket.app.ui.reseller

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.Role
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
import com.sareemarket.app.ui.components.OrderThumb
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.SecondaryButton
import com.sareemarket.app.ui.components.SkeletonRows
import com.sareemarket.app.ui.components.StatusPill
import com.sareemarket.app.ui.seller.ReasonDialog
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.formatRupees
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing
import com.sareemarket.app.ui.timeAgo
import com.sareemarket.app.ui.timeLeft
import com.sareemarket.app.ui.timeRemaining
import java.time.Instant
import kotlinx.coroutines.delay

/** One order: where it stands, its tracking number, what is in it, and what the customer can still do. */
@Composable
fun OrderDetailScreen(onBack: () -> Unit, viewModel: OrderDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    OrderDetailContent(
        state, onBack, viewModel::reload,
        onCopy = { clipboard.setText(AnnotatedString(it)) },
        onReceive = viewModel::receive, onCancel = viewModel::cancel,
    )
}

@Composable
fun OrderDetailContent(
    state: OrderDetailState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
    onReceive: () -> Unit,
    onCancel: (String?) -> Unit,
) {
    var confirmingCancel by remember { mutableStateOf(false) }
    var confirmingReceive by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.margin)) {
            BackHeader(state.order?.let { "Order #${it.id}" } ?: "Order", onBack)
            val order = state.order
            when {
                state.loading -> SkeletonRows()
                order == null -> Column(Modifier.fillMaxWidth().padding(top = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "Something went wrong.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
                }
                else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("order"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OrderStatusPill(order.status, Modifier.testTag("order-status"))
                        Text(order.shopName, style = MaterialTheme.typography.titleLarge)
                    }
                    if (order.status == OrderStatus.CANCELLED) {
                        Text(
                            "Cancelled by ${when (order.cancelledBy) { "CUSTOMER" -> "you"; "SELLER" -> order.shopName; else -> "the app" }}" +
                                (order.cancelReason?.let { ": $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("cancelled-note"),
                        )
                    }
                    if (order.courier != null || order.trackingNumber != null) TrackingCard(order, onCopy)
                    Timeline(order)
                    order.lines.forEach { line ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OrderThumb(line.itemId, width = 40.dp)
                            Text("${line.quantity} × ${line.title}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(formatRupees(line.unitPrice * line.quantity), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row {
                        Text("Shipping", Modifier.weight(1f))
                        Text(formatRupees(order.shippingFee))
                    }
                    Row {
                        Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(formatRupees(order.grandTotal), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("order-total"))
                    }
                    val a = order.address
                    Text(
                        "Deliver to ${a.name}, ${a.phone}\n${listOfNotNull(a.line1, a.line2, "${a.city}, ${a.state} ${a.pin}").joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    order.paymentReference?.let { Text("UTR $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("action-error")) }
                    if (order.status == OrderStatus.SHIPPED) {
                        PrimaryButton("Received", onClick = { confirmingReceive = true }, loading = state.busy, modifier = Modifier.fillMaxWidth().testTag("receive-order"))
                    }
                    // Until the seller ships it the customer can still cancel; the app does not refund.
                    if (order.status == OrderStatus.CONFIRMED) {
                        TextButton(onClick = { confirmingCancel = true }, enabled = !state.busy, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("cancel-order")) {
                            Text("Cancel order")
                        }
                    }
                }
            }
        }
    }
    if (confirmingCancel) {
        ReasonDialog(
            title = "Cancel this order?",
            message = "The sarees go back on sale and ${state.order?.shopName ?: "the seller"} is told. The app does not refund you: " +
                "ask ${state.order?.shopName ?: "the seller"} to send your money back.",
            confirmLabel = "Cancel order",
            onDismiss = { confirmingCancel = false },
            required = false,
        ) { reason -> confirmingCancel = false; onCancel(reason.ifEmpty { null }) }
    }
    if (confirmingReceive) {
        AlertDialog(
            onDismissRequest = { confirmingReceive = false },
            modifier = Modifier.testTag("receive-dialog"),
            title = { Text("Did the parcel arrive?") },
            text = { Text("Mark it received once you have it with you.") },
            confirmButton = {
                TextButton(onClick = { confirmingReceive = false; onReceive() }, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("confirm-receive-order")) { Text("Yes, received") }
            },
            dismissButton = { TextButton(onClick = { confirmingReceive = false }, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Not yet") } },
        )
    }
}

/** The courier and tracking number the seller entered, with a button that copies the number to track it elsewhere. */
@Composable
private fun TrackingCard(order: Order, onCopy: (String) -> Unit) {
    var copied by remember(order.trackingNumber) { mutableStateOf(false) }
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = CardBorder, modifier = Modifier.fillMaxWidth().testTag("tracking-card")) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Tracking", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            order.courier?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("tracking-courier")) }
            order.trackingNumber?.let { number ->
                Text(number, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("tracking-number"))
                OutlinedButton(
                    onClick = { onCopy(number); copied = true },
                    modifier = Modifier.heightIn(min = MinTouchTarget).testTag("copy-tracking"),
                ) { Text(if (copied) "Copied" else "Copy tracking number") }
            }
        }
    }
}

@Composable
private fun Timeline(order: Order) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.testTag("timeline")) {
        order.history.forEach { entry ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(8.dp)) {}
                Text(entry.status.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(timeAgo(entry.at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
