package com.sareemarket.app.ui.reseller

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.semantics.Role
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import com.sareemarket.app.ui.profile.MenuButton
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing
import java.io.File
import kotlinx.coroutines.delay

private const val SEARCH_DELAY_MS = 300L

/** How close to the end of the list the reseller scrolls before the next page is fetched. */
private const val LOAD_MORE_AHEAD = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(onOpenItem: (Long) -> Unit, onMenu: () -> Unit, viewModel: CatalogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(state.query.q) }
    var showFilters by remember { mutableStateOf(false) }
    var showPhotoChoice by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) cameraUri?.let { viewModel.searchByImage(it.toString()) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.searchByImage(it.toString()) }
    }
    // Follows may have changed on another screen while the catalog was out of sight.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshFollowCount()
        onPauseOrDispose {}
    }
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
        onFollowingTab = viewModel::setFollowingOnly,
        onPhotoSearch = { showPhotoChoice = true },
        onClearPhoto = viewModel::clearImage,
        onMenu = onMenu,
        onRefresh = viewModel::refresh,
    )
    if (showPhotoChoice) {
        PhotoSourceDialog(
            onDismiss = { showPhotoChoice = false },
            onCamera = {
                showPhotoChoice = false
                val file = File.createTempFile("search", ".jpg", File(context.cacheDir, "camera").apply { mkdirs() })
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraUri = uri
                camera.launch(uri)
            },
            onGallery = {
                showPhotoChoice = false
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
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
    onFollowingTab: (Boolean) -> Unit,
    onPhotoSearch: () -> Unit,
    onClearPhoto: () -> Unit,
    onMenu: () -> Unit,
    onRefresh: () -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = Spacing.margin)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MenuButton(onMenu)
                Text("Catalog", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            }
            SearchField(searchText, onSearchText)
            FollowTabs(state, onFollowingTab)
            FilterBar(state, onOpenFilters, onSort, onPhotoSearch)
            if (state.imageUri != null) PhotoBanner(state.imageUri, onClearPhoto)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.items.isEmpty() && state.error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
                    }
                    state.items.isEmpty() -> Column(Modifier.align(Alignment.Center).testTag("empty"), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when {
                                state.imageUri != null -> "No similar sarees found.\nTry a clearer photo, or fewer filters."
                                state.query.followingOnly -> "Nothing new from sellers you follow."
                                else -> "No sarees match."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        if (state.query.followingOnly && state.imageUri == null && state.query.activeFilterCount == 0) {
                            TextButton(onClick = { onFollowingTab(false) }, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("show-all")) {
                                Text("Show all sellers")
                            }
                        }
                        if (state.query.activeFilterCount > 0) {
                            TextButton(onClick = onClearFilters, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Clear filters") }
                        }
                    }
                    else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        ResultsGrid(state, onOpenItem, onLoadMore, onRetry)
                    }
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

/** "Following (3)" shows what followed sellers posted; "All sellers" is the whole market. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowTabs(state: CatalogState, onSelect: (Boolean) -> Unit) {
    val tabs = listOf(true to "Following (${state.followedCount})", false to "All sellers (${state.sellers.size})")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        tabs.forEachIndexed { index, (following, label) ->
            SegmentedButton(
                selected = state.query.followingOnly == following,
                onClick = { onSelect(following) },
                shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.testTag(if (following) "tab-following" else "tab-all"),
            ) { Text(label, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(state: CatalogState, onOpenFilters: () -> Unit, onSort: (CatalogSort) -> Unit, onPhotoSearch: () -> Unit) {
    var sortOpen by remember { mutableStateOf(false) }
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
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
        // A photo search lists the closest match first, so there is no sort order to choose.
        if (state.imageUri == null) {
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
        OutlinedButton(
            onClick = onPhotoSearch,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.heightIn(min = MinTouchTarget).testTag("photo-search"),
        ) {
            Text("Search by photo", style = MaterialTheme.typography.labelLarge)
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

/** Reminds the reseller that the list is a photo search, and lets them go back to the full catalog. */
@Composable
private fun PhotoBanner(imageUri: String, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = Spacing.sm).testTag("photo-banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SareeImage(imageUri, contentDescription = "Your photo", modifier = Modifier.width(40.dp))
        Text("Sarees that look like your photo", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onClear, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("clear-photo")) {
            Text("Clear", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Where the search picture comes from: a new photo from the camera, or one already on the phone (even a screenshot). */
@Composable
internal fun PhotoSourceDialog(onDismiss: () -> Unit, onCamera: () -> Unit, onGallery: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search by photo") },
        text = { Text("Find sarees that look like a picture. Take a photo, or pick one from your phone, such as a screenshot someone sent you.") },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onCamera, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("take-photo")) { Text("Take a photo") }
                TextButton(onClick = onGallery, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("choose-photo")) { Text("Choose from phone") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Cancel") } },
    )
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
        items(state.items, key = { it.id }) { Box(Modifier.animateItem()) { CatalogCard(it, onClick = { onOpenItem(it.id) }) } }
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
    Column(Modifier.clickable(onClickLabel = "Open saree", role = Role.Button, onClick = onClick).testTag("card-${item.id}"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
