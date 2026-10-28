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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.sareemarket.app.data.Address
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.SareeTextField
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

/**
 * The customer's saved addresses. With [onPicked] set (checkout) a tap on a row chooses that address; without it the
 * screen is for managing them.
 */
@Composable
fun AddressesScreen(onBack: () -> Unit, onPicked: ((Address) -> Unit)? = null, viewModel: AddressesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AddressesContent(
        state, onBack, onPicked, viewModel::startAdd, viewModel::startEdit, viewModel::delete, viewModel::load,
        viewModel::onField, viewModel::setDefault, viewModel::save, viewModel::cancelForm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressesContent(
    state: AddressesState,
    onBack: () -> Unit,
    onPicked: ((Address) -> Unit)?,
    onAdd: () -> Unit,
    onEdit: (Address) -> Unit,
    onDelete: (Long) -> Unit,
    onRetry: () -> Unit,
    onField: (String, String) -> Unit,
    onDefault: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancelForm: () -> Unit,
) {
    var deleting by remember { mutableStateOf<Address?>(null) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.margin)) {
            BackHeader(if (onPicked != null) "Choose an address" else "Delivery addresses", onBack)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> SkeletonRows()
                    state.items.isEmpty() && state.error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
                    }
                    state.items.isEmpty() -> Text(
                        "No saved addresses yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).testTag("addresses-empty"),
                    )
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxSize().testTag("addresses")) {
                        items(state.items, key = { it.id }) { a -> AddressRow(a, onPicked, onEdit) { deleting = a } }
                        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error")) } }
                    }
                }
            }
            PrimaryButton("Add address", onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md).testTag("add-address"))
        }
    }
    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this address?") },
            text = { Text("${a.name}, ${a.line1}, ${a.city}") },
            confirmButton = {
                TextButton(onClick = { onDelete(a.id); deleting = null }, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("confirm-delete")) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Keep") } },
        )
    }
    state.form?.let { form ->
        ModalBottomSheet(onDismissRequest = onCancelForm) {
            AddressForm(form, state, onField, onDefault, onSave, onCancelForm)
        }
    }
}

@Composable
private fun AddressRow(address: Address, onPicked: ((Address) -> Unit)?, onEdit: (Address) -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = CardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onPicked != null) Modifier.clickable(onClickLabel = "Use this address", role = Role.Button) { onPicked(address) } else Modifier)
            .testTag("address-${address.id}"),
    ) {
        Row(Modifier.padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(address.name, style = MaterialTheme.typography.titleMedium)
                    if (address.isDefault) StatusPill("Default", SareeColors.Available, Modifier.testTag("default-${address.id}"))
                }
                Text(address.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    listOfNotNull(address.line1, address.line2, "${address.city}, ${address.state} ${address.pin}").joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = { onEdit(address) }, modifier = Modifier.size(MinTouchTarget).testTag("address-edit-${address.id}")) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit address")
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(MinTouchTarget).testTag("address-delete-${address.id}")) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete address")
            }
        }
    }
}

@Composable
private fun AddressForm(
    form: AddressFormState,
    state: AddressesState,
    onField: (String, String) -> Unit,
    onDefault: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.margin).padding(bottom = Spacing.lg).testTag("address-form"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(if (form.id == null) "New address" else "Edit address", style = MaterialTheme.typography.headlineSmall)
        SareeTextField(form.name, { onField("name", it) }, "Name on the parcel", tag = "address-name", error = state.fieldErrors["name"])
        SareeTextField(form.phone, { onField("phone", it) }, "Mobile number", tag = "address-phone", error = state.fieldErrors["phone"], keyboardType = KeyboardType.Phone)
        SareeTextField(form.line1, { onField("line1", it) }, "House, street", tag = "address-line1", error = state.fieldErrors["line1"])
        SareeTextField(form.line2, { onField("line2", it) }, "Area, landmark (optional)", tag = "address-line2")
        SareeTextField(form.city, { onField("city", it) }, "City", tag = "address-city", error = state.fieldErrors["city"])
        SareeTextField(form.state, { onField("state", it) }, "State", tag = "address-state", error = state.fieldErrors["state"])
        SareeTextField(form.pin, { onField("pin", it) }, "PIN code", tag = "address-pin", error = state.fieldErrors["pin"], keyboardType = KeyboardType.Number)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use as my default address", modifier = Modifier.weight(1f))
            Switch(checked = form.makeDefault, onCheckedChange = onDefault, modifier = Modifier.testTag("address-default"))
        }
        state.formError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("address-form-error")) }
        PrimaryButton("Save address", onClick = onSave, loading = state.saving, modifier = Modifier.fillMaxWidth().testTag("save-address"))
        SecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}
