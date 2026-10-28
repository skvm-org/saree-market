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

/** Choose where to send one seller's sarees, add a note, and start the payment hold. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutSheet(
    ui: CheckoutUi,
    onSelect: (Long?) -> Unit,
    onAddAddress: () -> Unit,
    onNote: (String) -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.margin).padding(bottom = Spacing.lg).testTag("checkout-sheet"),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("Pay ${ui.seller.shopName}", style = MaterialTheme.typography.headlineSmall)
            Text("Deliver to", style = MaterialTheme.typography.labelLarge)
            if (!ui.loadingAddresses && ui.addresses.isEmpty()) {
                Text("You have no saved address yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("no-addresses"))
            }
            ui.addresses.forEach { a -> AddressChoice(a, a.id == ui.selectedAddressId) { onSelect(a.id) } }
            TextButton(onClick = onAddAddress, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("checkout-add-address")) { Text("Add a new address") }
            OutlinedTextField(
                value = ui.note,
                onValueChange = onNote,
                label = { Text("Note for the seller (optional)") },
                modifier = Modifier.fillMaxWidth().testTag("checkout-note"),
            )
            Row {
                Text("Total to pay", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(formatRupees(ui.seller.grandTotal), style = MaterialTheme.typography.titleMedium)
            }
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("checkout-error")) }
            ui.result?.let { ResultNotes(it) }
            PrimaryButton(
                "Continue to payment",
                onClick = onContinue,
                loading = ui.busy,
                enabled = ui.selectedAddressId != null,
                modifier = Modifier.fillMaxWidth().testTag("continue-payment"),
            )
            Text(
                "The sarees are held for you for 2 hours while you pay.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddressChoice(address: Address, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = CardBorder,
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Use this address", role = Role.RadioButton, onClick = onClick).testTag("address-choice-${address.id}"),
    ) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f)) {
                Text("${address.name} · ${address.phone}", style = MaterialTheme.typography.titleSmall)
                Text("${address.line1}, ${address.city} ${address.pin}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Why a checkout made no hold: sarees that ran short and sellers that could not be paid. */
@Composable
private fun ResultNotes(result: CheckoutResult) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.testTag("checkout-result")) {
        result.skipped.forEach {
            val why = when (it.reason) {
                "NOT_ACCEPTING_ORDERS" -> "${it.shopName} isn't accepting orders yet."
                "NOTHING_IN_STOCK" -> "Nothing from ${it.shopName} is in stock any more."
                "HOLD_EXISTS" -> "You already have a payment in progress with ${it.shopName}."
                else -> "${it.shopName} can't be ordered from right now."
            }
            Text(why, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        result.adjustments.forEach {
            Text("${it.title}: you wanted ${it.requested}, ${it.reserved} available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
