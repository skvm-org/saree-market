package com.sareemarket.app.ui.seller

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.CollectionSummary
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.StatusPill
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.formatRupees
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.SareeTheme
import com.sareemarket.app.ui.theme.Spacing

@Composable
fun SellerHomeScreen(
    sellerName: String,
    onNewCollection: () -> Unit,
    onOpen: (Long) -> Unit,
    onLogout: () -> Unit,
    viewModel: SellerHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Reload each time the screen is shown, so counts are right after coming back from a collection.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    SellerHomeContent(state, sellerName, onNewCollection, onOpen, onLogout, onRetry = viewModel::refresh)
}

@Composable
fun SellerHomeContent(
    state: SellerHomeState,
    sellerName: String,
    onNewCollection: () -> Unit,
    onOpen: (Long) -> Unit,
    onLogout: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewCollection,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New collection", style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.testTag("new-collection"),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .safeDrawingPadding()
                .padding(horizontal = Spacing.margin),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your collections", style = MaterialTheme.typography.headlineMedium)
                    Text(sellerName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onLogout, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                    Text("Log out", style = MaterialTheme.typography.labelLarge)
                }
            }
            Box(Modifier.fillMaxSize()) {
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
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Spacing.md, bottom = 96.dp),
                            ) {
                                items(state.collections, key = { it.id }) { CollectionCard(it, onClick = { onOpen(it.id) }) }
                            }
                        }
                }
            }
        }
    }
}

@Composable
internal fun CollectionCard(collection: CollectionSummary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, SareeColors.Hairline),
        modifier = Modifier.fillMaxWidth().testTag("collection-${collection.id}"),
    ) {
        Row(Modifier.padding(Spacing.sm + Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SareeImage(
                model = collection.coverItemId?.let { imageUrl(it, thumb = true) },
                contentDescription = null,
                modifier = Modifier.width(72.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(collection.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${collection.sareeType} · ${collection.fabric}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(formatRupees(collection.price), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primaryContainer)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatusPill("${collection.availableCount} available", SareeColors.Available)
                    if (collection.soldOutCount > 0) StatusPill("${collection.soldOutCount} sold out", SareeColors.SlateNeutral)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SellerHomePreview() {
    SareeTheme {
        SellerHomeContent(
            SellerHomeState.Loaded(listOf(CollectionSummary(1, "Diwali soft silks", "Soft silk", "Pure silk", 4500, 18, 15, 3, 1))),
            "Lakshmi", {}, {}, {}, {},
        )
    }
}
