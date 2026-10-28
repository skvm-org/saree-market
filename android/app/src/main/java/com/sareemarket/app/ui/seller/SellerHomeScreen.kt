package com.sareemarket.app.ui.seller

import com.sareemarket.app.ui.components.SkeletonCollections
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
    viewModel: SellerHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Reload each time the screen is shown, so counts are right after coming back from a collection.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    SellerHomeContent(state, shopName, onNewCollection, onOpen, onRetry = viewModel::refresh, onRefresh = viewModel::pullRefresh, location = location)
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
) {
    // On a wide screen the Add tab is enough, and a floating button would sit on top of a card.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!wide) ExtendedFloatingActionButton(
                onClick = onNewCollection,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New collection", style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.testTag("new-collection"),
            )
        },
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
                            Text(
                                "No collections yet.\nTap New collection to upload your first photos.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center).testTag("empty"),
                            )
                        } else {
                            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                                // One column on a phone; two or three on a wide screen.
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 340.dp),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                                    contentPadding = PaddingValues(top = Spacing.md, bottom = 96.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
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
    val onHold = collections.sumOf { it.onHoldCount }
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
                if (onHold > 0) StatTile("On hold", onHold, SareeColors.OnHoldText, Modifier.weight(1f).testTag("stat-on-hold"))
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

@Composable
internal fun CollectionCard(collection: CollectionSummary, onClick: () -> Unit) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = CardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open collection", role = Role.Button, onClick = onClick)
            .testTag("collection-${collection.id}"),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Column(Modifier.weight(1f)) {
                    Text(collection.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        typeAndFabric(collection.sareeType, collection.fabric),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Only the reseller catalog fills this in; a seller's own dashboard is all one shop.
                    collection.shopName?.let {
                        Row(Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Icon(Icons.Filled.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(18.dp))
                            Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text(formatRupees(collection.price), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primaryContainer)
            }
            PhotoStrip(collection)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    buildString {
                        append("${collection.availableCount} available")
                        if (collection.onHoldCount > 0) append(" · ${collection.onHoldCount} on hold")
                        if (collection.soldOutCount > 0) append(" · ${collection.soldOutCount} sold out")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = SareeColors.GoldText,
                )
                collection.createdAt?.let {
                    Text("Uploaded ${timeAgo(it)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Up to three photos side by side; a collection with fewer photos shows what it has. */
@Composable
private fun PhotoStrip(collection: CollectionSummary) {
    val ids = collection.previewItemIds.ifEmpty { listOfNotNull(collection.coverItemId) }
    if (ids.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ids.take(3).forEach { id ->
            AsyncImage(
                model = imageUrl(id, thumb = true),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatioPortrait()
                    .clip(PhotoShape)
                    .background(SareeColors.SurfaceMuted),
            )
        }
        // Keep the photos the same width when there are fewer than three.
        repeat(3 - ids.take(3).size) { Box(Modifier.weight(1f)) }
    }
}

private fun Modifier.aspectRatioPortrait() = this.aspectRatio(4f / 5f)


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
