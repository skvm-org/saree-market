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
import java.time.Instant
import kotlinx.coroutines.delay

private const val MAX_REASON = 200
private const val MAX_DETAIL = 60

/** Asks for a reason; the button stays off until there is one, unless the reason is optional ([required] false). */
@Composable
internal fun ReasonDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    required: Boolean = true,
    onConfirm: (String) -> Unit,
) {
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
                    label = { Text(if (required) "Reason" else "Reason (optional)") },
                    supportingText = { Text("${reason.length}/$MAX_REASON") },
                    modifier = Modifier.fillMaxWidth().testTag("reason"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim()) }, enabled = !required || reason.isNotBlank(), modifier = Modifier.heightIn(min = MinTouchTarget).testTag("reason-confirm")) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Back") } },
    )
}

/**
 * Courier and tracking number, both required, so the customer can follow the parcel. Used to add them when shipping
 * and, with the current values filled in, to correct them afterwards. The button stays off until both are filled in.
 */
@Composable
internal fun TrackingDialog(
    title: String,
    confirmLabel: String,
    initialCourier: String,
    initialTracking: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var courier by remember { mutableStateOf(initialCourier) }
    var tracking by remember { mutableStateOf(initialTracking) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("tracking-dialog"),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Enter these once the courier has the parcel. The customer is notified and can copy the number.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(courier, { courier = it.take(MAX_DETAIL) }, label = { Text("Courier") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("courier"))
                OutlinedTextField(tracking, { tracking = it.take(MAX_DETAIL) }, label = { Text("Tracking number") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("tracking"))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(courier.trim(), tracking.trim()) },
                enabled = courier.isNotBlank() && tracking.isNotBlank(),
                modifier = Modifier.heightIn(min = MinTouchTarget).testTag("tracking-confirm"),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Back") } },
    )
}
