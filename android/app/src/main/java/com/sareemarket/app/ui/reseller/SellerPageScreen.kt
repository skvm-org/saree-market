package com.sareemarket.app.ui.reseller

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sareemarket.app.data.CollectionSummary
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.timeAgo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.NotifyMode
import com.sareemarket.app.data.SellerPage
import com.sareemarket.app.ui.theme.Spacing

@Composable
fun SellerPageScreen(onBack: () -> Unit, onOpenCollection: (Long) -> Unit, viewModel: SellerPageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val (call, whatsapp) = rememberContactActions()
    val follow: FollowViewModel = hiltViewModel()
    val followState by follow.state.collectAsStateWithLifecycle()
    LoadedContent(state, onRetry = viewModel::reload, onBack = onBack) { page ->
        val mode = followState.modeOf(page.id, page.followMode)
        SellerPageContent(page, onBack, onOpenCollection, call, whatsapp, mode, followState.error, onToggleMute = { follow.toggleMute(page.id, mode) }) { follow.toggle(page.id, mode) }
    }
}

@Composable
fun SellerPageContent(
    page: SellerPage,
    onBack: () -> Unit,
    onOpenCollection: (Long) -> Unit,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    followMode: NotifyMode?,
    followError: String?,
    onToggleMute: () -> Unit = {},
    onToggleFollow: () -> Unit,
) {
    val available = page.collections.sumOf { it.availableCount }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(bottom = Spacing.lg),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .safeDrawingPadding()
                    .padding(horizontal = Spacing.margin, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Spacer(Modifier.weight(1f))
                    if (!page.contactPhone.isNullOrBlank()) {
                        CircleAction(Icons.Filled.Phone, "Call", "call", MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.primaryContainer) { onCall(page.contactPhone) }
                    }
                    if (!page.whatsappNumber.isNullOrBlank()) {
                        CircleAction(Icons.AutoMirrored.Filled.Chat, "WhatsApp", "whatsapp", SareeColors.WhatsApp, MaterialTheme.colorScheme.onSurface) { onWhatsApp(page.whatsappNumber) }
                    }
                }
                Text(page.shopName, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("shop-name"))
                Text(page.marketLocation, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (page.collections.isNotEmpty()) {
                    Text(
                        "${if (available == 1L) "1 saree" else "$available sarees"} available",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("available-count"),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(bottom = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    FollowPill(followMode, onToggleFollow, Modifier.weight(1f))
                    if (followMode != null) {
                        CircleAction(
                            if (followMode == NotifyMode.MUTED) Icons.Filled.NotificationsOff else Icons.Filled.NotificationsActive,
                            if (followMode == NotifyMode.MUTED) "Turn notifications on" else "Mute notifications",
                            "mute", MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.primaryContainer, onToggleMute,
                        )
                    }
                }
                followError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("follow-error")) }
            }
        }
        item {
            Text("Collections (${page.collections.size})", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = Spacing.margin))
            if (page.collections.isEmpty()) {
                Text("No collections yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.margin, vertical = Spacing.sm))
            }
        }
        items(page.collections, key = { it.id }) { CollectionBanner(it, onClick = { onOpenCollection(it.id) }, Modifier.padding(horizontal = Spacing.margin)) }
    }
}

@Composable
private fun CircleAction(icon: ImageVector, description: String, tag: String, container: Color, tint: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = container, modifier = Modifier.size(MinTouchTarget).testTag(tag)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, tint = tint) }
    }
}

/** A collection as a wide photo with its name and stock written over a dark fade at the bottom. */
@Composable
private fun CollectionBanner(collection: CollectionSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = CardShape, color = SareeColors.SurfaceMuted, modifier = modifier.fillMaxWidth().testTag("collection-${collection.id}")) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f)) {
            collection.coverItemId?.let {
                AsyncImage(model = imageUrl(it, thumb = true), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(Spacing.md)) {
                Text(collection.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append("${collection.availableCount} available")
                        collection.createdAt?.let { append(" · Uploaded ${timeAgo(it)}") }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}
