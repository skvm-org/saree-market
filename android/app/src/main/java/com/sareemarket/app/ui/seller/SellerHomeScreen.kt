package com.sareemarket.app.ui.seller

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import com.sareemarket.app.ui.profile.MenuButton
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
    onMenu: () -> Unit,
    viewModel: SellerHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Reload each time the screen is shown, so counts are right after coming back from a collection.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    SellerHomeContent(state, shopName, onNewCollection, onOpen, onMenu, onRetry = viewModel::refresh, onRefresh = viewModel::pullRefresh, location = location)
}

@Composable
fun SellerHomeContent(
    state: SellerHomeState,
    sellerName: String,
    onNewCollection: () -> Unit,
    onOpen: (Long) -> Unit,
    onMenu: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit = {},
    location: String = "",
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
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
            ShopHeader(sellerName, location, state, onMenu)
            Box(Modifier.fillMaxSize().padding(horizontal = Spacing.margin)) {
                when (state) {
                    SellerHomeState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
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
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                                    contentPadding = PaddingValues(top = Spacing.md, bottom = 96.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    item {
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
private fun ShopHeader(shopName: String, location: String, state: SellerHomeState, onMenu: () -> Unit) {
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MenuButton(onMenu)
            InitialAvatar(shopName, 44.dp)
            Column(Modifier.weight(1f)) {
                Text(shopName, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("shop-name"))
                if (location.isNotBlank()) {
                    Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs), modifier = Modifier.padding(bottom = Spacing.sm)) {
            StatTile("Available", available, SareeColors.Available, Modifier.weight(1f).testTag("stat-available"))
            StatTile("Sold out", soldOut, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).testTag("stat-sold-out"))
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
        onClick = onClick,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = CardBorder,
        modifier = Modifier.fillMaxWidth().testTag("collection-${collection.id}"),
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
                }
                Text(formatRupees(collection.price), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primaryContainer)
            }
            PhotoStrip(collection)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    buildString {
                        append("${collection.availableCount} available")
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
            "Lakshmi Silks", {}, {}, {}, {}, location = "Chinnalapatti",
        )
    }
}
