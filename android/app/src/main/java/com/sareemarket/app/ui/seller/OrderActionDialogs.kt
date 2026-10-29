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

private const val MAX_REASON = 200
private const val MAX_DETAIL = 60

/** Confirming a payment is the one step that is hard to undo, so the seller sees exactly what to check. */
@Composable
internal fun ConfirmPaymentDialog(order: Order, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("confirm-payment-dialog"),
        title = { Text("Payment received?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Did ${formatRupees(order.grandTotal)} arrive in your account with UTR ${order.paymentReference ?: "(none)"}?", style = MaterialTheme.typography.bodyLarge)
                Text("Check your bank or UPI app first. A screenshot can be faked.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("confirm-payment-yes")) { Text("Yes, it arrived") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Not yet") } },
    )
}

/** Asks for the reason the customer will read; the button stays off until there is one. */
@Composable
internal fun ReasonDialog(title: String, message: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("reason-dialog"),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(MAX_REASON) },
                    label = { Text("Reason") },
                    supportingText = { Text("${reason.length}/$MAX_REASON") },
                    modifier = Modifier.fillMaxWidth().testTag("reason"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim()) }, enabled = reason.isNotBlank(), modifier = Modifier.heightIn(min = MinTouchTarget).testTag("reason-confirm")) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Back") } },
    )
}

/** Courier and tracking number are both optional: plenty of sellers hand the parcel to a local transport. */
@Composable
internal fun ShipDialog(onDismiss: () -> Unit, onConfirm: (String?, String?) -> Unit) {
    var courier by remember { mutableStateOf("") }
    var tracking by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ship-dialog"),
        title = { Text("Ship this order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Both are optional. The customer sees them on their order.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(courier, { courier = it.take(MAX_DETAIL) }, label = { Text("Courier") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("courier"))
                OutlinedTextField(tracking, { tracking = it.take(MAX_DETAIL) }, label = { Text("Tracking number") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("tracking"))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(courier.trim().ifEmpty { null }, tracking.trim().ifEmpty { null }) },
                modifier = Modifier.heightIn(min = MinTouchTarget).testTag("ship-confirm"),
            ) { Text("Mark shipped") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Back") } },
    )
}
