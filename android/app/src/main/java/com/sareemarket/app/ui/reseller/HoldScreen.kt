package com.sareemarket.app.ui.reseller

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.SecondaryButton
import com.sareemarket.app.ui.components.SkeletonRows
import com.sareemarket.app.ui.components.StatusPill
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.formatRupees
import com.sareemarket.app.ui.timeRemaining
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing
import com.sareemarket.app.ui.timeAgo
import com.sareemarket.app.ui.timeLeft
import com.sareemarket.app.ui.typeAndFabric
import java.time.Instant
import kotlinx.coroutines.delay

/** How often the countdown on the hold screen is recomputed. */
private const val TICK_MS = 30_000L

/** Pay the seller by UPI, then enter the UTR (and optionally a screenshot) here to turn the hold into an order. */
@Composable
fun HoldScreen(
    onBack: () -> Unit,
    onPaid: (Long) -> Unit,
    onClosed: () -> Unit,
    onOpenOrders: () -> Unit = {},
    viewModel: HoldViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val now by produceState(Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(TICK_MS)
        }
    }
    LaunchedEffect(state.paidOrderId) {
        state.paidOrderId?.let {
            viewModel.paidHandled()
            onPaid(it)
        }
    }
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    state.hold?.let { hold ->
        LaunchedEffect(now) { if (!now.isBefore(Instant.parse(hold.expiresAt))) viewModel.markExpired() }
    }
    var note by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> viewModel.onProof(uri?.toString()) }
    HoldContent(
        state, now, note,
        onUtr = viewModel::onUtr,
        onCopy = { upi ->
            clipboard.setText(AnnotatedString(upi))
            note = "Copied $upi"
        },
        onPayWithUpi = { hold ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiLink(hold.sellerUpiId.orEmpty(), hold.shopName, hold.grandTotal)))
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                note = "No UPI app found. Copy the ID and pay from your UPI app."
            }
        },
        onPickProof = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onClearProof = { viewModel.onProof(null) },
        onPay = viewModel::pay,
        onCancel = viewModel::cancel,
        onBack = onBack,
        onBackToCart = onClosed,
        onOpenOrders = onOpenOrders,
        onRetry = viewModel::load,
    )
}

@Composable
fun HoldContent(
    state: HoldState,
    now: Instant,
    note: String?,
    onUtr: (String) -> Unit,
    onCopy: (String) -> Unit,
    onPayWithUpi: (com.sareemarket.app.data.Hold) -> Unit,
    onPickProof: () -> Unit,
    onClearProof: () -> Unit,
    onPay: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onBackToCart: () -> Unit,
    onRetry: () -> Unit,
    onOpenOrders: () -> Unit = {},
) {
    var confirmingCancel by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.margin)) {
            BackHeader(state.hold?.let { "Pay ${it.shopName}" } ?: "Payment", onBack)
            val hold = state.hold
            when {
                state.loading -> SkeletonRows()
                hold == null -> Column(Modifier.fillMaxWidth().padding(top = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(state.error ?: "Something went wrong.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.testTag("hold-error"))
                    if (state.expired) {
                        PrimaryButton("My orders", onClick = onOpenOrders, modifier = Modifier.testTag("see-orders"))
                        OutlinedButton(onClick = onBackToCart, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("back-to-cart")) { Text("Back to cart") }
                    }
                    else PrimaryButton("Retry", onClick = onRetry)
                }
                else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("hold"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(formatRupees(hold.grandTotal), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.testTag("hold-total"))
                    Text(
                        "Sarees ${formatRupees(hold.itemsTotal)} + shipping ${formatRupees(hold.shippingFee)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("hold-breakdown"),
                    )
                    // What is actually held: a short line is reserved at the available quantity, so this may differ from the cart.
                    hold.lines.forEachIndexed { index, line ->
                        Row(Modifier.testTag("hold-line-$index")) {
                            Text("${line.quantity} × ${line.title}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(formatRupees(line.unitPrice * line.quantity), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    val left = if (state.expired) "" else timeRemaining(hold.expiresAt, now)
                    if (state.expired || left.isEmpty()) {
                        Text("This payment window has ended.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("hold-ended"))
                        PrimaryButton("Back to cart", onClick = onBackToCart, modifier = Modifier.fillMaxWidth().testTag("back-to-cart"))
                    } else {
                        Text("Complete payment within $left", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("hold-countdown"))
                        PaymentSteps(hold, state, note, onUtr, onCopy, onPayWithUpi, onPickProof, onClearProof, onPay)
                    }
                    TextButton(onClick = { confirmingCancel = true }, enabled = !state.busy, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("cancel-hold")) {
                        Text("Cancel payment")
                    }
                }
            }
        }
    }
    if (confirmingCancel) {
        AlertDialog(
            onDismissRequest = { confirmingCancel = false },
            title = { Text("Release these sarees?") },
            text = { Text("They go back on sale and stay in your cart.") },
            confirmButton = {
                TextButton(onClick = { confirmingCancel = false; onCancel() }, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("confirm-cancel-hold")) { Text("Release") }
            },
            dismissButton = { TextButton(onClick = { confirmingCancel = false }, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Keep") } },
        )
    }
}

@Composable
private fun PaymentSteps(
    hold: com.sareemarket.app.data.Hold,
    state: HoldState,
    note: String?,
    onUtr: (String) -> Unit,
    onCopy: (String) -> Unit,
    onPayWithUpi: (com.sareemarket.app.data.Hold) -> Unit,
    onPickProof: () -> Unit,
    onClearProof: () -> Unit,
    onPay: () -> Unit,
) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = CardBorder, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("1. Pay the seller on UPI", style = MaterialTheme.typography.titleMedium)
            val upi = hold.sellerUpiId
            if (upi == null) {
                Text("This seller has no UPI ID yet.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("no-upi"))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(upi, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).testTag("upi-id"))
                    OutlinedButton(onClick = { onCopy(upi) }, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("copy-upi")) { Text("Copy") }
                }
                PrimaryButton("Pay with UPI app", onClick = { onPayWithUpi(hold) }, modifier = Modifier.fillMaxWidth().testTag("upi-intent"))
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("hold-note")) }
        }
    }
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = CardBorder, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("2. Tell us you paid", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.utr,
                onValueChange = onUtr,
                label = { Text("UTR / transaction ID") },
                supportingText = { Text("12 digits, shown in your UPI app after paying") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("utr"),
            )
            if (state.proof == null) {
                OutlinedButton(onClick = onPickProof, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("pick-proof")) { Text("Add a screenshot (optional)") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Screenshot added", modifier = Modifier.weight(1f).testTag("proof-added"))
                    IconButton(onClick = onClearProof, modifier = Modifier.size(MinTouchTarget).testTag("clear-proof")) { Icon(Icons.Filled.Close, contentDescription = "Remove screenshot") }
                }
            }
            state.payError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pay-error")) }
            PrimaryButton("I have paid", onClick = onPay, loading = state.busy, enabled = state.canPay, modifier = Modifier.fillMaxWidth().testTag("i-have-paid"))
        }
    }
}
