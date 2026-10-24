package com.sareemarket.app.ui.reseller

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.CatalogItem
import com.sareemarket.app.data.CatalogSort
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.StatusPill
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.components.sareeFieldColors
import com.sareemarket.app.ui.formatRupees
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing
import kotlinx.coroutines.delay

private const val SEARCH_DELAY_MS = 300L

/** How close to the end of the list the reseller scrolls before the next page is fetched. */
private const val LOAD_MORE_AHEAD = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(onOpenItem: (Long) -> Unit, onLogout: () -> Unit, viewModel: CatalogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(state.query.q) }
    var showFilters by remember { mutableStateOf(false) }
    // Search as the reseller types, but wait for a short pause so each letter does not hit the server.
    LaunchedEffect(text) {
        delay(SEARCH_DELAY_MS)
        viewModel.setKeyword(text)
    }
    CatalogContent(
        state = state,
        searchText = text,
        onSearchText = { text = it },
        onOpenFilters = { showFilters = true },
        onSort = { viewModel.apply(state.query.copy(sort = it)) },
        onOpenItem = onOpenItem,
        onLoadMore = viewModel::loadNextPage,
        onRetry = viewModel::retry,
        onClearFilters = viewModel::clearFilters,
        onLogout = onLogout,
    )
    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            FilterSheetContent(
                query = state.query,
                reference = state.reference,
                sellers = state.sellers,
                onApply = { viewModel.apply(it); showFilters = false },
            )
        }
    }
}

@Composable
fun CatalogContent(
    state: CatalogState,
    searchText: String,
    onSearchText: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onSort: (CatalogSort) -> Unit,
    onOpenItem: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onClearFilters: () -> Unit,
    onLogout: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = Spacing.margin)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Catalog", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onLogout, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                    Text("Log out", style = MaterialTheme.typography.labelLarge)
                }
            }
            SearchField(searchText, onSearchText)
            FilterBar(state, onOpenFilters, onSort)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.items.isEmpty() && state.error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
                    }
                    state.items.isEmpty() -> Column(Modifier.align(Alignment.Center).testTag("empty"), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No sarees match.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.query.activeFilterCount > 0) {
                            TextButton(onClick = onClearFilters, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Clear filters") }
                        }
                    }
                    else -> ResultsGrid(state, onOpenItem, onLoadMore, onRetry)
                }
            }
        }
    }
}

@Composable
private fun SearchField(text: String, onText: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onText,
        placeholder = { Text("Search sarees, colours, collections") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onText("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = sareeFieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("search"),
    )
}

@Composable
private fun FilterBar(state: CatalogState, onOpenFilters: () -> Unit, onSort: (CatalogSort) -> Unit) {
    var sortOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val count = state.query.activeFilterCount
        OutlinedButton(
            onClick = onOpenFilters,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.heightIn(min = MinTouchTarget).testTag("filters"),
        ) {
            Text(if (count > 0) "Filters ($count)" else "Filters", style = MaterialTheme.typography.labelLarge)
        }
        Box {
            OutlinedButton(
                onClick = { sortOpen = true },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.heightIn(min = MinTouchTarget).testTag("sort"),
            ) {
                Text(state.query.sort.label, style = MaterialTheme.typography.labelLarge)
            }
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                CatalogSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.label) },
                        onClick = { sortOpen = false; onSort(sort) },
                        modifier = Modifier.heightIn(min = MinTouchTarget),
                    )
                }
            }
        }
    }
    if (!state.loading && state.items.isNotEmpty()) {
        Text(
            "${state.items.size} of ${state.totalItems} sarees",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm).testTag("count"),
        )
    }
}

@Composable
private fun ResultsGrid(state: CatalogState, onOpenItem: (Long) -> Unit, onLoadMore: () -> Unit, onRetry: () -> Unit) {
    val grid = rememberLazyGridState()
    LaunchedEffect(grid, state.items.size) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= state.items.size - LOAD_MORE_AHEAD) onLoadMore()
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = grid,
        contentPadding = PaddingValues(bottom = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxSize().testTag("results"),
    ) {
        items(state.items, key = { it.id }) { CatalogCard(it, onClick = { onOpenItem(it.id) }) }
        if (state.loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(Spacing.md), Alignment.Center) { CircularProgressIndicator() }
            }
        }
        if (state.error != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Could not load more. ${state.error}", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Try again") }
                }
            }
        }
    }
}

@Composable
internal fun CatalogCard(item: CatalogItem, onClick: () -> Unit) {
    val soldOut = item.status == ItemStatus.SOLD_OUT
    Column(Modifier.clickable(onClick = onClick).testTag("card-${item.id}"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box {
            SareeImage(imageUrl(item.id, thumb = true), contentDescription = item.colour ?: item.collectionTitle, modifier = Modifier.fillMaxWidth().alpha(if (soldOut) 0.6f else 1f))
            StatusPill(
                if (soldOut) "Sold out" else "Available",
                if (soldOut) SareeColors.SlateNeutral else SareeColors.Available,
                Modifier.padding(Spacing.sm).align(Alignment.TopStart),
            )
        }
        Text(
            formatRupees(item.price),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            textDecoration = if (soldOut) TextDecoration.LineThrough else null,
        )
        Text("${item.sareeType} · ${item.fabric}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.shopName, style = MaterialTheme.typography.labelMedium, color = SareeColors.SlateNeutral, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
