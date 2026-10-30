package com.sareemarket.app.ui.seller

import com.sareemarket.app.ui.components.SkeletonCollections
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.heightIn
import com.sareemarket.app.ui.theme.MinTouchTarget
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sareemarket.app.data.CollectionSummary
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.components.InitialAvatar
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.formatRupees
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.theme.PhotoShape
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.SareeTheme
import com.sareemarket.app.ui.theme.Spacing
import com.sareemarket.app.ui.timeAgo
import com.sareemarket.app.ui.typeAndFabric

@Composable
fun SellerHomeScreen(
    shopName: String,
    location: String,
    onNewCollection: () -> Unit,
    onOpen: (Long) -> Unit,
    toConfirm: Long = 0,
    onOpenOrders: () -> Unit = {},
    viewModel: SellerHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Reload each time the screen is shown, so counts are right after coming back from a collection.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    SellerHomeContent(state, shopName, onNewCollection, onOpen, onRetry = viewModel::refresh, onRefresh = viewModel::pullRefresh, location = location, toConfirm = toConfirm, onOpenOrders = onOpenOrders)
}

@Composable
fun SellerHomeContent(
    state: SellerHomeState,
    sellerName: String,
    onNewCollection: () -> Unit,
    onOpen: (Long) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit = {},
    location: String = "",
    toConfirm: Long = 0,
    onOpenOrders: () -> Unit = {},
) {
    // The Add tab is always on screen, so there is no floating button to cover a card; an empty shop gets a button in the page itself.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .safeDrawingPadding(),
        ) {
            ShopHeader(sellerName, location, state, wide)
            Box(Modifier.fillMaxSize().padding(horizontal = Spacing.margin)) {
                when (state) {
                    SellerHomeState.Loading -> SkeletonCollections()
                    is SellerHomeState.Failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
                    }
                    is SellerHomeState.Loaded ->
                        if (state.collections.isEmpty()) {
                            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                Text(
                                    "No collections yet.\nUpload your first photos.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("empty"),
                                )
                                PrimaryButton("New collection", onClick = onNewCollection, modifier = Modifier.testTag("new-collection"))
                            }
                        } else {
                            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                                // One column on a phone; two or three on a wide screen.
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 340.dp),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                                    contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.lg),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    if (toConfirm > 0) item(span = { GridItemSpan(maxLineSpan) }) {
                                        Surface(
                                            onClick = onOpenOrders, shape = CardShape, color = SareeColors.GoldBg, border = CardBorder,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("confirm-banner"),
                                        ) {
                                            Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    if (toConfirm == 1L) "1 payment to confirm" else "$toConfirm payments to confirm",
                                                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                                                )
                                                Text("Open orders", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primaryContainer)
                                            }
                                        }
                                    }
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                            Text("Active collections (${state.collections.size})", style = MaterialTheme.typography.headlineSmall)
                                            Text("Newest first", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    items(state.collections, key = { it.id }) {
                                        Box(Modifier.animateItem()) { CollectionCard(it, onClick = { onOpen(it.id) }) }
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}

/** Shop name and place on a warm panel, with the two stock totals as tiles underneath. */
@Composable
private fun ShopHeader(shopName: String, location: String, state: SellerHomeState, wide: Boolean) {
    val collections = (state as? SellerHomeState.Loaded)?.collections.orEmpty()
    val available = collections.sumOf { it.availableCount }
    val soldOut = collections.sumOf { it.soldOutCount }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .padding(horizontal = Spacing.margin, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val identity: @Composable (Modifier) -> Unit = { modifier ->
            Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                InitialAvatar(shopName, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(shopName, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("shop-name"))
                    if (location.isNotBlank()) {
                        Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val totals: @Composable (Modifier) -> Unit = { modifier ->
            Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
                StatTile("Available", available, SareeColors.AvailableText, Modifier.weight(1f).testTag("stat-available"))
                StatTile("Sold out", soldOut, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).testTag("stat-sold-out"))
            }
        }
        if (wide) {
            // Name on the left, totals on the right: one short band instead of two tall ones.
            Row(Modifier.padding(bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                identity(Modifier.weight(1f))
                totals(Modifier.width(360.dp))
            }
        } else {
            identity(Modifier)
            totals(Modifier.padding(bottom = Spacing.sm))
        }
    }
}

/** A stock total as plain type: the label, then the number, in the colour that means what it counts. */
@Composable
private fun StatTile(label: String, value: Long, numberColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(modifier, shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xs)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = numberColor)
        }
    }
}

/**
 * A collection as a card. In the catalog the seller leads (who, and when), then the photos, then the title with
 * "From" price. On a seller's own dashboard every card is the same shop, so the header is left out.
 */
@Composable
internal fun CollectionCard(collection: CollectionSummary, onClick: () -> Unit) {
    val catalog = collection.shopName != null
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open collection", role = Role.Button, onClick = onClick)
            .testTag("collection-${collection.id}"),
    ) {
        Column(Modifier.padding(vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
            collection.shopName?.let { shop ->
                Row(Modifier.padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
                    InitialAvatar(shop, 32.dp)
                    Column {
                        Text(shop, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        collection.createdAt?.let { Text(timeAgo(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            PhotoStrip(collection)
            Column(Modifier.padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(collection.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        if (catalog) "From ${formatRupees(collection.price)}" else formatRupees(collection.price),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    )
                }
                Text(typeAndFabric(collection.sareeType, collection.fabric), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        buildString {
                            append("${collection.availableCount} available")
                            if (collection.soldOutCount > 0) append(" · ${collection.soldOutCount} sold out")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!catalog) collection.createdAt?.let {
                        Text("Uploaded ${timeAgo(it)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** The collection's photos in a row that scrolls sideways; the edge of the next one shows when there is more. */
@Composable
private fun PhotoStrip(collection: CollectionSummary) {
    val ids = collection.previewItemIds.ifEmpty { listOfNotNull(collection.coverItemId) }
    if (ids.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ids.forEach { id ->
            AsyncImage(
                model = imageUrl(id, thumb = true),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(128.dp)
                    .height(164.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SareeColors.SurfaceMuted),
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun SellerHomePreview() {
    SareeTheme {
        SellerHomeContent(
            SellerHomeState.Loaded(listOf(CollectionSummary(1, "Diwali soft silks", "Soft silk", "Pure silk", 4500, 18, 15, 3, 1))),
            "Lakshmi Silks", {}, {}, {}, location = "Chinnalapatti",
        )
    }
}
