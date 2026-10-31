package com.sareemarket.app.ui.reseller

import com.sareemarket.app.ui.components.ItemStatusPill
import com.sareemarket.app.ui.components.sharedPhoto
import com.sareemarket.app.ui.theme.GRID_MIN_CELL
import androidx.compose.material3.Surface
import com.sareemarket.app.ui.components.LowStockChip
import com.sareemarket.app.ui.components.SoldOutChip
import com.sareemarket.app.ui.components.ScreenHeader
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.typeAndFabric
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.CollectionDetail
import com.sareemarket.app.data.Item
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.StatusPill
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.formatRupees
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing

@Composable
fun ResellerCollectionScreen(onBack: () -> Unit, onOpenItem: (Long) -> Unit, viewModel: ResellerCollectionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoadedContent(state, onRetry = viewModel::reload, onBack = onBack) { collection ->
        ResellerCollectionContent(collection, onBack, onOpenItem)
    }
}

@Composable
fun ResellerCollectionContent(collection: CollectionDetail, onBack: () -> Unit, onOpenItem: (Long) -> Unit) {
    Column(Modifier.safeDrawingPadding()) {
        ScreenHeader(collection.title, onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = GRID_MIN_CELL),
            contentPadding = PaddingValues(start = Spacing.margin, end = Spacing.margin, top = Spacing.xs, bottom = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(typeAndFabric(collection.sareeType.name, collection.fabric.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatRupees(collection.price), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primaryContainer)
                    collection.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            items(collection.items, key = { it.id }) { Box(Modifier.animateItem()) { PhotoTile(it, onClick = { onOpenItem(it.id) }) } }
        }
    }
}

/** The photo first, with a heart-free, border-free footer: the price, the colour when there is one, and a chip only for low stock or sold out. */
@Composable
private fun PhotoTile(item: Item, onClick: () -> Unit) {
    val soldOut = item.status == ItemStatus.SOLD_OUT
    Column(
        Modifier.clickable(onClickLabel = "Open saree", role = Role.Button, onClick = onClick).testTag("tile-${item.id}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box {
            SareeImage(imageUrl(item.id, thumb = true), contentDescription = item.colour ?: "Saree photo", modifier = Modifier.fillMaxWidth().sharedPhoto(item.id).alpha(if (soldOut) 0.6f else 1f))
            when {
                soldOut -> SoldOutChip(Modifier.padding(Spacing.sm).align(Alignment.BottomStart))
                // A single piece is the usual case for a saree, so "only 1 left" is kept for the saree page itself.
                item.quantity in 2..LOW_STOCK -> LowStockChip(item.quantity, Modifier.padding(Spacing.sm).align(Alignment.BottomStart))
            }
        }
        Column(Modifier.padding(horizontal = Spacing.xs)) {
            Text(
                formatRupees(item.effectivePrice),
                style = MaterialTheme.typography.titleLarge,
                color = if (soldOut) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                textDecoration = if (soldOut) TextDecoration.LineThrough else null,
            )
            item.colour?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}
