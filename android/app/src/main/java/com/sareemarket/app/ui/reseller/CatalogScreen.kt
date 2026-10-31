package com.sareemarket.app.ui.reseller

import com.sareemarket.app.ui.components.ItemStatusPill
import com.sareemarket.app.ui.components.sharedPhoto
import com.sareemarket.app.ui.components.SkeletonGrid
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import com.sareemarket.app.ui.seller.CollectionCard
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import com.sareemarket.app.data.CatalogQuery
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import com.sareemarket.app.ui.components.ScreenHeader
import com.sareemarket.app.ui.components.EmptyState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Storefront
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.components.FieldShape
import com.sareemarket.app.ui.components.PillChip
import com.sareemarket.app.ui.components.PillSegmented
import com.sareemarket.app.ui.theme.CardShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sareemarket.app.ui.typeAndFabric
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
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.sareemarket.app.data.NotifyMode
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
import java.io.File
import kotlinx.coroutines.delay

private const val SEARCH_DELAY_MS = 300L

/** A phone gets one collection card per row; a tablet or landscape gets several. */
private val COLLECTION_CARD_MIN = 320.dp

/** How close to the end of the list the reseller scrolls before the next page is fetched. */
private const val LOAD_MORE_AHEAD = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onOpenItem: (Long) -> Unit,
    onOpenCollection: (Long) -> Unit,
    alertsUnread: Long = 0,
    onOpenSellers: () -> Unit = {},
    onOpenAlerts: () -> Unit = {},
    viewModel: CatalogViewModel = hiltViewModel(),
) {
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
        onOpenCollection = onOpenCollection,
        onLoadMore = viewModel::loadNextPage,
        onRetry = viewModel::retry,
        onClearFilters = viewModel::clearFilters,
        onFollowingTab = viewModel::setFollowingOnly,
        onPhotoSearch = { showPhotoChoice = true },
        onClearPhoto = viewModel::clearImage,
        onRefresh = viewModel::refresh,
        onQueryChange = viewModel::apply,
        followModes = state.followModes,
        onToggleFollow = viewModel::toggleFollow,
        alertsUnread = alertsUnread,
        onOpenSellers = onOpenSellers,
        onOpenAlerts = onOpenAlerts,
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
        // Open all the way, so the Show results button at the foot is on screen from the start.
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
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
    onRefresh: () -> Unit = {},
    onQueryChange: (CatalogQuery) -> Unit = {},
    onOpenCollection: (Long) -> Unit = {},
    followModes: Map<Long, NotifyMode> = emptyMap(),
    onToggleFollow: (Long) -> Unit = {},
    alertsUnread: Long = 0,
    onOpenSellers: () -> Unit = {},
    onOpenAlerts: () -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            // On a wide screen (landscape, tablet) the controls move to a side panel, so the sarees get the height.
            val wide = maxWidth >= WIDE_LAYOUT
            val controls: @Composable ColumnScope.() -> Unit = {
                SearchField(searchText, onSearchText, onPhotoSearch)
                FilterChips(state, onFollowingTab, onOpenFilters, onSort)
                ActiveFilterRow(state, onQueryChange)
                if (state.imageUri != null) PhotoBanner(state.imageUri, state.totalItems.toInt(), onPhotoSearch, onClearPhoto)
            }
            val results: @Composable BoxScope.() -> Unit = {
        when {
            state.loading -> SkeletonGrid()
            state.noResults && state.error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
            }
            state.noResults -> {
                val hasFilters = state.query.activeFilterCount > 0
                val offerAll = state.query.followingOnly && state.imageUri == null && !hasFilters
                EmptyState(
                    Icons.Filled.Search,
                    when {
                        state.imageUri != null -> "No similar sarees found."
                        state.query.followingOnly -> "Nothing new from sellers you follow."
                        else -> "No sarees match."
                    },
                    if (state.imageUri != null) "Try a clearer photo, or fewer filters." else null,
                    Modifier.align(Alignment.Center).testTag("empty"),
                    actionLabel = when {
                        hasFilters -> "Clear filters"
                        offerAll -> "Show all sellers"
                        else -> null
                    },
                    actionTag = if (offerAll) "show-all" else "clear-filters",
                    onAction = if (hasFilters) onClearFilters else ({ onFollowingTab(false) }),
                )
            }
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                if (state.imageUri != null) MatchList(state, onOpenItem, onLoadMore) else ResultsGrid(state, onOpenCollection, onLoadMore, onRetry, followModes, onToggleFollow)
            }
        }
            }
            Column(Modifier.fillMaxSize()) {
                CatalogHeader(alertsUnread, onOpenSellers, onOpenAlerts)
                if (wide) {
                    Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.margin)) {
                        Column(Modifier.width(CONTROLS_PANEL).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = Spacing.md)) {
                            controls()
                        }
                        Box(Modifier.weight(1f).fillMaxHeight().padding(top = Spacing.sm)) { results() }
                    }
                } else {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.margin)) {
                        controls()
                        Box(Modifier.weight(1f).fillMaxWidth()) { results() }
                    }
                }
            }
        }
    }
}

/** Screens at least this wide get the controls in a side panel. */
private val WIDE_LAYOUT = 600.dp
private val CONTROLS_PANEL = 320.dp

/** The page title, with the two places that used to be tabs: the sellers the customer follows, and the bell. */
@Composable
internal fun CatalogHeader(alertsUnread: Long, onOpenSellers: () -> Unit, onOpenAlerts: () -> Unit) {
    ScreenHeader("Saree Market", titleTag = "catalog-title") {
        IconButton(onClick = onOpenSellers, modifier = Modifier.size(MinTouchTarget).testTag("open-sellers")) {
            Icon(Icons.Outlined.People, contentDescription = "My sellers")
        }
        IconButton(onClick = onOpenAlerts, modifier = Modifier.size(MinTouchTarget).testTag("open-alerts")) {
            BadgedBox(badge = {
                if (alertsUnread > 0) Badge(Modifier.testTag("badge")) { Text(if (alertsUnread > 99) "99+" else alertsUnread.toString()) }
            }) { Icon(Icons.Outlined.Notifications, contentDescription = "Alerts") }
        }
    }
}

/** The one search bar: a tonal pill with the camera at the end; it turns white with a crimson edge while typing. */
@Composable
private fun SearchField(text: String, onText: (String) -> Unit, onPhotoSearch: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = text,
        onValueChange = onText,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primaryContainer),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.testTag("search"),
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .background(if (focused) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                    .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape) else Modifier)
                    .padding(start = Spacing.md + 2.dp, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text("Search sarees, colours, fabrics", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
                if (text.isNotEmpty()) {
                    IconButton(onClick = { onText("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
                }
                Box(Modifier.size(MinTouchTarget).clickable(onClickLabel = "Search by photo", role = Role.Button, onClick = onPhotoSearch).testTag("photo-search"), Alignment.Center) {
                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f), CircleShape), Alignment.Center) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "Search by photo", tint = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
    )
}

/**
 * Following / All sellers as two text tabs with a crimson mark under the chosen one, then one row: Filters and the sort
 * order on the left, how many are shown on the right. The chips are neutral; only active filters take the brand colour.
 */
@Composable
private fun FilterChips(state: CatalogState, onFollowing: (Boolean) -> Unit, onOpenFilters: () -> Unit, onSort: (CatalogSort) -> Unit) {
    var sortOpen by remember { mutableStateOf(false) }
    val count = state.query.activeFilterCount
    Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        listOf(true to "Following (${state.followedCount})", false to "All sellers (${state.sellers.size})").forEach { (following, label) ->
            val on = following == state.query.followingOnly
            Column(
                Modifier
                    .heightIn(min = MinTouchTarget)
                    .clickable(role = Role.Tab, onClick = { onFollowing(following) })
                    .padding(horizontal = Spacing.md)
                    .testTag(if (following) "tab-following" else "tab-all"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.padding(top = 4.dp).height(3.dp).width(if (on) 28.dp else 0.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            PillChip(if (count > 0) "Filters ($count)" else "Filters", count > 0, "filters", icon = Icons.Filled.Tune, onClick = onOpenFilters)
            // A photo search lists the closest match first, so there is no sort order to choose.
            if (state.imageUri == null) {
                Box {
                    PillChip(state.query.sort.label, false, "sort", icon = Icons.AutoMirrored.Filled.Sort) { sortOpen = true }
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
        }
        if (state.imageUri == null && !state.loading && state.collections.isNotEmpty()) {
            Text(
                "${state.collections.size} of ${state.totalItems} collections",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.sm).testTag("count"),
            )
        } else if (state.imageUri != null && !state.loading && state.items.isNotEmpty()) {
            Text(
                "${state.items.size} of ${state.totalItems} sarees",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.sm).testTag("count"),
            )
        }
    }
}

/** The filters that are on, each a chip with a cross, so the reseller sees why the list is short and can undo one at a time. */
@Composable
private fun ActiveFilterRow(state: CatalogState, onQueryChange: (CatalogQuery) -> Unit) {
    val active = activeFilters(state.query, state.reference, state.sellers)
    if (active.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        active.forEach { filter ->
            InputChip(
                selected = true,
                onClick = { onQueryChange(filter.clear(state.query)) },
                label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove ${filter.label}", modifier = Modifier.size(16.dp)) },
                shape = CircleShape,
                colors = InputChipDefaults.inputChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f),
                    selectedLabelColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                border = null,
                modifier = Modifier.testTag("active-${filter.key}"),
            )
        }
    }
}

/** Shows the picture being searched with, how many sarees matched, and a way to change or drop it. */
@Composable
private fun PhotoBanner(imageUri: String, matches: Int, onChange: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = Spacing.sm).testTag("photo-banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SareeImage(imageUri, contentDescription = "Your photo", modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Text(if (matches == 1) "1 match found" else "$matches matches found", style = MaterialTheme.typography.titleSmall)
            Text("Sarees that look like your photo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onChange, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("change-photo")) {
            Text("Change photo", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onClear, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("clear-photo")) {
            Text("Clear", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** "Find by photo": take a new photo, or pick one already on the phone (a screenshot someone sent works well). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoSourceDialog(onDismiss: () -> Unit, onCamera: () -> Unit, onGallery: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = Spacing.margin).padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primaryContainer)
                Text("Find by photo", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                "Works best with a photo forwarded from a seller's WhatsApp, or a screenshot from your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
                PhotoSourceButton("Camera", Icons.Filled.PhotoCamera, "take-photo", onCamera, Modifier.weight(1f))
                PhotoSourceButton("Gallery or screenshot", Icons.Filled.PhotoLibrary, "choose-photo", onGallery, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PhotoSourceButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.testTag(tag)) {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primaryContainer)
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ResultsGrid(state: CatalogState, onOpenCollection: (Long) -> Unit, onLoadMore: () -> Unit, onRetry: () -> Unit, followModes: Map<Long, NotifyMode>, onToggleFollow: (Long) -> Unit) {
    val grid = rememberLazyGridState()
    LaunchedEffect(grid, state.collections.size) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= state.collections.size - LOAD_MORE_AHEAD) onLoadMore()
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = COLLECTION_CARD_MIN),
        state = grid,
        contentPadding = PaddingValues(bottom = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxSize().testTag("results"),
    ) {
        items(state.collections, key = { it.id }) { Box(Modifier.animateItem()) {
            CollectionCard(
                it,
                onClick = { onOpenCollection(it.id) },
                followMode = it.sellerId?.let(followModes::get),
                onToggleFollow = it.sellerId?.let { id -> { onToggleFollow(id) } },
            )
        } }
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

/** Photo-search results as rows, closest match first, each saying how close it is. */
@Composable
private fun MatchList(state: CatalogState, onOpenItem: (Long) -> Unit, onLoadMore: () -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(list, state.items.size) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= state.items.size - LOAD_MORE_AHEAD) onLoadMore()
        }
    }
    LazyColumn(
        state = list,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
        contentPadding = PaddingValues(bottom = Spacing.lg),
        modifier = Modifier.fillMaxSize().testTag("results"),
    ) {
        items(state.items, key = { it.id }) { item -> Box(Modifier.animateItem()) { MatchRow(item, onClick = { onOpenItem(item.id) }) } }
        if (state.loadingMore) {
            item { Box(Modifier.fillMaxWidth().padding(Spacing.md), Alignment.Center) { CircularProgressIndicator() } }
        }
    }
}

/** A hash distance of 0..EXACT_MATCH_DISTANCE is the same picture; anything beyond is merely similar. */
private const val EXACT_MATCH_DISTANCE = 6

@Composable
private fun MatchRow(item: CatalogItem, onClick: () -> Unit) {
    val soldOut = item.status == ItemStatus.SOLD_OUT
    val exact = (item.distance ?: Int.MAX_VALUE) <= EXACT_MATCH_DISTANCE
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = CardBorder,
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open saree", role = Role.Button, onClick = onClick).testTag("card-${item.id}"),
    ) {
        Row(Modifier.padding(Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(Modifier.width(96.dp)) {
                SareeImage(imageUrl(item.id, thumb = true), contentDescription = item.colour ?: item.collectionTitle, modifier = Modifier.fillMaxWidth().sharedPhoto(item.id).alpha(if (soldOut) 0.6f else 1f))
                Text(
                    if (exact) "Exact match" else "Close match",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (exact) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Spacing.xs)
                        .background(if (exact) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(horizontal = Spacing.sm, vertical = 2.dp)
                        .testTag("match-${item.id}"),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    formatRupees(item.price),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    textDecoration = if (soldOut) TextDecoration.LineThrough else null,
                )
                Text(typeAndFabric(item.sareeType, item.fabric), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.shopName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.status != ItemStatus.AVAILABLE) ItemStatusPill(item.status)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
}
