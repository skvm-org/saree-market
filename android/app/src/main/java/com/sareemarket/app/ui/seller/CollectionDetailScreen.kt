package com.sareemarket.app.ui.seller

import com.sareemarket.app.ui.components.SkeletonGrid
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import com.sareemarket.app.ui.theme.GRID_MIN_CELL
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.components.PillChip
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.typeAndFabric
import com.sareemarket.app.ui.ShareText
import com.sareemarket.app.ui.share
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.CollectionDetail
import com.sareemarket.app.data.Item
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.data.MAX_PHOTOS
import com.sareemarket.app.ui.components.FormColumn
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.SareeTextField
import com.sareemarket.app.ui.components.SecondaryButton
import com.sareemarket.app.ui.components.ItemStatusPill
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.formatRupees
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(onBack: () -> Unit, viewModel: CollectionDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    var menuOpen by remember { mutableStateOf(false) }
    // "Mark all" flips the whole collection in one tap, so it is followed by an Undo rather than preceded by a dialog.
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(state.undo) {
        val undo = state.undo ?: return@LaunchedEffect
        val result = snackbars.showSnackbar(undo.message, actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoMarkAll() else viewModel.dismissUndo()
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var editingCollection by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Item?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris: List<Uri> ->
        viewModel.addPhotos(uris.map(Uri::toString))
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(Modifier.fillMaxSize()) {
        val detail = state.detail
        when {
            detail == null && state.loading -> SkeletonGrid(Modifier.safeDrawingPadding().padding(Spacing.margin))
            detail == null -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                PrimaryButton("Retry", onClick = viewModel::load, modifier = Modifier.padding(Spacing.md))
                SecondaryButton("Back", onClick = onBack, modifier = Modifier.padding(horizontal = Spacing.md))
            }
            else -> BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                // The same pieces, placed two ways: stacked above the grid on a phone, in a side panel on a wide screen.
                val wide = maxWidth >= WIDE_LAYOUT
                val topBar: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { editingCollection = true }, modifier = Modifier.testTag("edit")) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit details", tint = MaterialTheme.colorScheme.primaryContainer)
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("menu")) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Collection actions")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Edit details") }, modifier = Modifier.heightIn(min = MinTouchTarget), onClick = { menuOpen = false; editingCollection = true })
                                DropdownMenuItem(
                                    text = { Text("Add photos") }, modifier = Modifier.heightIn(min = MinTouchTarget),
                                    onClick = { menuOpen = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") }, modifier = Modifier.heightIn(min = MinTouchTarget),
                                    onClick = {
                                        menuOpen = false
                                        share(context, ShareText.collection(detail.title, detail.sareeType.name, detail.fabric.name, detail.price, detail.items.count { it.status == ItemStatus.AVAILABLE }))
                                    },
                                )
                                DropdownMenuItem(text = { Text("Delete collection") }, modifier = Modifier.heightIn(min = MinTouchTarget), onClick = { menuOpen = false; confirmingDelete = true })
                            }
                        }
                    }
                }
                val titleBlock: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(detail.title, style = MaterialTheme.typography.headlineLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(typeAndFabric(detail.sareeType.name, detail.fabric.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatRupees(detail.price), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primaryContainer)
                        }
                        detail.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        state.progress?.let {
                            LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
                            Text("Uploading ${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.error != null && editingItem == null && !editingCollection) {
                            Text(state.error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error"))
                        }
                    }
                }
                val stockBlock: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (detail.items.isEmpty()) {
                        Text("No photos yet. Use the menu to add photos.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        StockControls(
                            total = detail.items.size,
                            available = state.count(ItemStatus.AVAILABLE),
                            soldOut = state.count(ItemStatus.SOLD_OUT),
                            filter = state.filter,
                            busy = state.busy,
                            onFilter = viewModel::setFilter,
                            onMarkAll = viewModel::markAll,
                        )
                        if (state.visibleItems.isEmpty()) {
                            Text("No sarees match this filter.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    }
                }
                val addPhotos: @Composable () -> Unit = {
                FilledTonalButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("add-photos"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Add more photos", style = MaterialTheme.typography.labelLarge)
                }
                }
                val photoGrid: @Composable (Modifier, Boolean) -> Unit = { modifier, withHeader ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = GRID_MIN_CELL),
                        modifier = modifier,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.margin, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
                    ) {
                        if (withHeader) {
                            item(span = { GridItemSpan(maxLineSpan) }) { topBar() }
                            item(span = { GridItemSpan(maxLineSpan) }) { titleBlock() }
                            item(span = { GridItemSpan(maxLineSpan) }) { stockBlock() }
                        }
                        items(state.visibleItems, key = { it.id }) { item ->
                            Box(Modifier.animateItem()) {
                                ItemCard(item, onClick = { viewModel.clearError(); editingItem = item }, onToggle = {
                                    // Ticking a saree with no pieces means "I have more": ask how many instead of flipping a mark that changes nothing.
                                    if (item.quantity == 0) {
                                        viewModel.clearError()
                                        editingItem = item
                                    } else {
                                        viewModel.toggleStatus(item.id)
                                    }
                                })
                            }
                        }
                    }
                }
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.width(CONTROLS_PANEL_WIDTH).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = Spacing.margin, end = Spacing.sm, bottom = Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            topBar()
                            titleBlock()
                            stockBlock()
                            addPhotos()
                        }
                        photoGrid(Modifier.weight(1f).fillMaxHeight(), false)
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        photoGrid(Modifier.weight(1f), true)
                        Box(Modifier.padding(horizontal = Spacing.margin, vertical = Spacing.sm)) { addPhotos() }
                    }
                }
            }
        }

        val detailForSheets = state.detail
        if (editingCollection && detailForSheets != null) {
            ModalBottomSheet(onDismissRequest = { editingCollection = false; viewModel.clearError() }, containerColor = MaterialTheme.colorScheme.surface) {
                EditCollectionSheet(detailForSheets, state, onSave = { fields -> viewModel.saveCollection(fields) { editingCollection = false } })
            }
        }
        val item = editingItem
        if (item != null && detailForSheets != null) {
            ModalBottomSheet(onDismissRequest = { editingItem = null; viewModel.clearError() }, containerColor = MaterialTheme.colorScheme.surface) {
                EditItemSheet(
                    item, detailForSheets.price, state,
                    onSave = { colour, price, quantity -> viewModel.saveItem(item.id, colour, price, quantity) { editingItem = null } },
                    onDelete = { viewModel.deleteItem(item.id) { editingItem = null } },
                )
            }
        }
        if (confirmingDelete) {
            AlertDialog(
                onDismissRequest = { confirmingDelete = false },
                title = { Text("Delete this collection?") },
                text = { Text("All its photos will be deleted too. This cannot be undone.") },
                confirmButton = { TextButton(onClick = { confirmingDelete = false; viewModel.deleteCollection() }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
            )
        }
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 64.dp, start = Spacing.margin, end = Spacing.margin))
    }
    }
}

/** Filter chips and the "mark all" button from the design. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StockControls(
    total: Int,
    available: Int,
    soldOut: Int,
    filter: ItemFilter,
    busy: Boolean,
    onFilter: (ItemFilter) -> Unit,
    onMarkAll: (ItemStatus) -> Unit,
) {
    // Chips on the left and the bulk action after them; on a narrow phone the action wraps to its own line.
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.Center) {
        listOf(
            Triple(ItemFilter.ALL, "All $total", "chip-all"),
            Triple(ItemFilter.AVAILABLE, "Available $available", "chip-available"),
            Triple(ItemFilter.SOLD_OUT, "Sold out $soldOut", "chip-sold-out"),
        ).forEach { (value, label, tag) -> PillChip(label, filter == value, tag) { onFilter(value) } }
        // With nothing left in stock the useful action is the opposite one.
        TextButton(
            onClick = { onMarkAll(if (available == 0) ItemStatus.AVAILABLE else ItemStatus.SOLD_OUT) },
            modifier = Modifier.heightIn(min = MinTouchTarget).testTag("mark-all").alpha(if (busy) 0.5f else 1f),
        ) {
            Text(if (available == 0) "Mark all available" else "Mark all sold out", style = MaterialTheme.typography.labelLarge, textDecoration = TextDecoration.Underline)
        }
    }
}

@Composable
internal fun ItemCard(item: Item, onClick: () -> Unit, onToggle: () -> Unit) {
    // No pieces left looks sold out whatever the seller's own mark says: checkout lowers the count but not the mark.
    val soldOut = item.status == ItemStatus.SOLD_OUT || item.quantity == 0
    val haptics = LocalHapticFeedback.current
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = CardBorder, modifier = Modifier.testTag("item-${item.id}")) {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Column(Modifier.clickable(onClickLabel = "Open saree", role = Role.Button, onClick = onClick), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box {
                    SareeImage(imageUrl(item.id, thumb = true), contentDescription = item.colour ?: "Saree photo", modifier = Modifier.fillMaxWidth().alpha(if (soldOut) 0.6f else 1f))
                    ItemStatusPill(item.status, Modifier.padding(Spacing.sm).align(Alignment.TopStart))
                }
                Text(item.colour ?: "No colour", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    formatRupees(item.effectivePrice),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    textDecoration = if (soldOut) TextDecoration.LineThrough else null,
                )
            }
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape).padding(start = Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (soldOut) "Out of stock" else "${item.quantity} in stock",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (soldOut) SareeColors.SlateText else SareeColors.AvailableText,
                )
                Checkbox(
                    checked = !soldOut,
                    onCheckedChange = {
                        haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        onToggle()
                    },
                    colors = CheckboxDefaults.colors(checkedColor = SareeColors.Available),
                    modifier = Modifier.testTag("stock-${item.id}"),
                )
            }
        }
    }
}

@Composable
private fun EditCollectionSheet(detail: CollectionDetail, state: DetailState, onSave: (CollectionFields) -> Unit) {
    var fields by remember(detail.id) {
        mutableStateOf(CollectionFields(detail.title, detail.sareeType.id, detail.fabric.id, detail.price.toString(), detail.description.orEmpty()))
    }
    Column(Modifier.imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.margin).padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Edit collection", style = MaterialTheme.typography.headlineSmall)
        CollectionFieldsForm(
            fields, state.reference, state.fieldErrors,
            onTitle = { fields = fields.copy(title = it) },
            onType = { fields = fields.copy(sareeTypeId = it) },
            onFabric = { fields = fields.copy(fabricId = it) },
            onPrice = { fields = fields.copy(price = com.sareemarket.app.ui.auth.digitsOnly(it, MAX_PRICE_DIGITS)) },
            onDescription = { fields = fields.copy(description = it) },
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        PrimaryButton("Save", onClick = { onSave(fields) }, loading = state.busy, modifier = Modifier.testTag("save"))
    }
}

@Composable
internal fun EditItemSheet(item: Item, collectionPrice: Int, state: DetailState, onSave: (String, String, String?) -> Unit, onDelete: () -> Unit) {
    var colour by remember(item.id) { mutableStateOf(item.colour.orEmpty()) }
    var price by remember(item.id) { mutableStateOf(item.priceOverride?.toString().orEmpty()) }
    var quantity by remember(item.id) { mutableStateOf(item.quantity.toString()) }
    var confirming by remember { mutableStateOf(false) }
    Column(Modifier.imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.margin).padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Edit photo", style = MaterialTheme.typography.headlineSmall)
        FormColumn(Modifier.fillMaxWidth()) {
            SareeTextField(colour, { colour = it }, "Colour", tag = "colour")
            SareeTextField(
                price, { price = com.sareemarket.app.ui.auth.digitsOnly(it, MAX_PRICE_DIGITS) }, "Price for this saree (optional)",
                tag = "priceOverride", prefix = "₹", keyboardType = KeyboardType.Number, error = state.fieldErrors["priceOverride"],
            )
            Text("Leave empty to use the collection price, ${formatRupees(collectionPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SareeTextField(
                quantity, { quantity = com.sareemarket.app.ui.auth.digitsOnly(it, MAX_QUANTITY_DIGITS) }, "Pieces in stock",
                tag = "item-quantity", keyboardType = KeyboardType.Number, error = state.fieldErrors["quantity"],
            )
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        PrimaryButton(
            "Save",
            onClick = { onSave(colour, price, quantity.takeIf { it != item.quantity.toString() }) },
            loading = state.busy,
            modifier = Modifier.testTag("save"),
        )
        SecondaryButton("Delete this photo", onClick = { confirming = true })
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Delete this photo?") },
            text = { Text("It will be removed from the collection.") },
            confirmButton = { TextButton(onClick = { confirming = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}

/** Screens at least this wide put the details and actions in a side panel next to the photos. */
private val WIDE_LAYOUT = 600.dp
private val CONTROLS_PANEL_WIDTH = 330.dp
