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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.sareemarket.app.data.CatalogItem
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.components.ItemStatusPill
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.SkeletonRows
import com.sareemarket.app.ui.components.StatusPill
import com.sareemarket.app.ui.components.EmptyState
import com.sareemarket.app.ui.components.ScreenHeader
import androidx.compose.material.icons.filled.FavoriteBorder
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.formatRupees
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing
import com.sareemarket.app.ui.timeAgo
import com.sareemarket.app.ui.timeLeft
import com.sareemarket.app.ui.typeAndFabric

private const val LOAD_MORE_AHEAD_WISHLIST = 4

/** The sarees the customer saved for later. */
@Composable
fun WishlistScreen(onOpenItem: (Long) -> Unit, onBrowse: () -> Unit = {}, viewModel: WishlistViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A saree can be saved from its page while this tab is away, so look again whenever it comes back.
    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose {}
    }
    WishlistContent(state, onOpenItem, viewModel::remove, viewModel::loadMore, viewModel::reload, viewModel::pullRefresh, onBrowse)
}

@Composable
fun WishlistContent(
    state: WishlistState,
    onOpenItem: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onBrowse: () -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenHeader("Wishlist")
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.margin)) {
                when {
                    state.loading -> SkeletonRows()
                    state.items.isEmpty() && state.error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
                    }
                    state.items.isEmpty() -> EmptyState(
                        Icons.Filled.FavoriteBorder,
                        "Nothing saved yet",
                        "Tap the heart on any saree to keep it here.",
                        Modifier.align(Alignment.Center).testTag("wishlist-empty"),
                        actionLabel = "Browse sarees",
                        onAction = onBrowse,
                    )
                    else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        WishlistList(state, onOpenItem, onRemove, onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun WishlistList(state: WishlistState, onOpenItem: (Long) -> Unit, onRemove: (Long) -> Unit, onLoadMore: () -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(list, state.items.size) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= state.items.size - LOAD_MORE_AHEAD_WISHLIST) onLoadMore()
        }
    }
    LazyColumn(
        state = list,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
        contentPadding = PaddingValues(bottom = Spacing.lg),
        modifier = Modifier.fillMaxSize().testTag("wishlist"),
    ) {
        items(state.items, key = { it.id }) { item -> WishlistRow(item, state.busyId == item.id, onOpenItem, onRemove) }
        state.error?.let {
            item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error")) }
        }
        if (state.loadingMore) {
            item { Box(Modifier.fillMaxWidth().padding(Spacing.md), Alignment.Center) { CircularProgressIndicator() } }
        }
    }
}

@Composable
private fun WishlistRow(item: CatalogItem, busy: Boolean, onOpenItem: (Long) -> Unit, onRemove: (Long) -> Unit) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = CardBorder,
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open saree", role = Role.Button) { onOpenItem(item.id) }.testTag("wish-${item.id}"),
    ) {
        Row(Modifier.padding(Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SareeImage(imageUrl(item.id, thumb = true), contentDescription = item.colour ?: item.collectionTitle, modifier = Modifier.width(84.dp).alpha(if (item.inStock) 1f else 0.6f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    formatRupees(item.price),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    textDecoration = if (item.inStock) null else TextDecoration.LineThrough,
                )
                Text(typeAndFabric(item.sareeType, item.fabric), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.shopName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!item.inStock) Text("Out of stock", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("out-of-stock-${item.id}"))
            }
            IconButton(
                onClick = { onRemove(item.id) },
                enabled = !busy,
                modifier = Modifier.size(MinTouchTarget).testTag("wishlist-remove-${item.id}"),
            ) { Icon(Icons.Filled.Favorite, contentDescription = "Remove from wishlist", tint = MaterialTheme.colorScheme.primaryContainer) }
        }
    }
}
