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
import androidx.compose.ui.platform.testTag
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

/** One order: where it stands, what is in it, and what the customer can still do. */
@Composable
fun OrderDetailScreen(onBack: () -> Unit, viewModel: OrderDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by produceState(Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(30_000L)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> viewModel.onProof(uri?.toString()) }
    OrderDetailContent(
        state, now, onBack, viewModel::reload, viewModel::onUtr,
        onPickProof = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onClearProof = { viewModel.onProof(null) },
        onResubmit = viewModel::resubmit, onCancel = viewModel::cancel,
    )
}

@Composable
fun OrderDetailContent(
    state: OrderDetailState,
    now: Instant,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUtr: (String) -> Unit,
    onPickProof: () -> Unit,
    onClearProof: () -> Unit,
    onResubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    var confirmingCancel by remember { mutableStateOf(false) }
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
                    if (order.status == OrderStatus.PAYMENT_REJECTED) RejectedBanner(order, state, now, onUtr, onPickProof, onClearProof, onResubmit)
                    if (order.status == OrderStatus.CANCELLED) {
                        Text(
                            "Cancelled by ${when (order.cancelledBy) { "CUSTOMER" -> "you"; "SELLER" -> order.shopName; else -> "the app" }}" +
                                (order.cancelReason?.let { ": $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("cancelled-note"),
                        )
                    }
                    if (order.courier != null || order.trackingNumber != null) {
                        Text(
                            listOfNotNull(order.courier?.let { "Courier: $it" }, order.trackingNumber?.let { "Tracking: $it" }).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("tracking"),
                        )
                    }
                    Timeline(order)
                    order.lines.forEach { line ->
                        Row {
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
                    if (order.status == OrderStatus.PAYMENT_SUBMITTED || order.status == OrderStatus.PAYMENT_REJECTED) {
                        TextButton(onClick = { confirmingCancel = true }, enabled = !state.busy, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("cancel-order")) {
                            Text("Cancel order")
                        }
                    }
                }
            }
        }
    }
    if (confirmingCancel) {
        AlertDialog(
            onDismissRequest = { confirmingCancel = false },
            title = { Text("Cancel this order?") },
            text = { Text("If you already paid, ask ${state.order?.shopName ?: "the seller"} for a refund.") },
            confirmButton = {
                TextButton(onClick = { confirmingCancel = false; onCancel() }, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("confirm-cancel-order")) { Text("Cancel order") }
            },
            dismissButton = { TextButton(onClick = { confirmingCancel = false }, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Keep") } },
        )
    }
}

@Composable
private fun RejectedBanner(
    order: Order,
    state: OrderDetailState,
    now: Instant,
    onUtr: (String) -> Unit,
    onPickProof: () -> Unit,
    onClearProof: () -> Unit,
    onResubmit: () -> Unit,
) {
    val deadline = order.paymentDeadline
    val left = if (deadline == null) "" else timeRemaining(deadline, now)
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().testTag("rejected-banner")) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("${order.shopName} could not find your payment", style = MaterialTheme.typography.titleMedium)
            order.rejectionReason?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("rejection-reason")) }
            if (left.isNotEmpty()) {
                Text("Send the correct UTR within $left", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("rejection-deadline"))
                OutlinedTextField(
                    value = state.utr,
                    onValueChange = onUtr,
                    label = { Text("UTR / transaction ID") },
                    supportingText = { Text("12 digits, shown in your UPI app after paying") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("resubmit-utr"),
                )
                if (state.proof == null) {
                    OutlinedButton(onClick = onPickProof, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("pick-proof")) { Text("Add a screenshot (optional)") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Screenshot added", modifier = Modifier.weight(1f))
                        IconButton(onClick = onClearProof, modifier = Modifier.size(MinTouchTarget)) { Icon(Icons.Filled.Close, contentDescription = "Remove screenshot") }
                    }
                }
                PrimaryButton("Send corrected UTR", onClick = onResubmit, loading = state.busy, enabled = state.utr.length == 12, modifier = Modifier.fillMaxWidth().testTag("resubmit"))
            } else {
                Text("The time to send it has passed. The order will be cancelled.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("rejection-ended"))
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
