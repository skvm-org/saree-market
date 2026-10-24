package com.sareemarket.app.ui.reseller

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
import com.sareemarket.app.ui.seller.CollectionCard
import com.sareemarket.app.ui.theme.Spacing

@Composable
fun SellerPageScreen(onBack: () -> Unit, onOpenCollection: (Long) -> Unit, viewModel: SellerPageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val (call, whatsapp) = rememberContactActions()
    val follow: FollowViewModel = hiltViewModel()
    val followState by follow.state.collectAsStateWithLifecycle()
    LoadedContent(state, onRetry = viewModel::reload, onBack = onBack) { page ->
        val mode = followState.modeOf(page.id, page.followMode)
        SellerPageContent(page, onBack, onOpenCollection, call, whatsapp, mode, followState.error) { follow.toggle(page.id, mode) }
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
    onToggleFollow: () -> Unit,
) {
    LazyColumn(
        Modifier.safeDrawingPadding().padding(horizontal = Spacing.margin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                BackHeader(page.shopName, onBack)
                Text(page.marketLocation, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ContactButtons(page.contactPhone, page.whatsappNumber, onCall, onWhatsApp)
                FollowButton(followMode, followError, onToggleFollow)
                Text("Collections (${page.collections.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Spacing.sm))
                if (page.collections.isEmpty()) {
                    Text("No collections yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(page.collections, key = { it.id }) { CollectionCard(it, onClick = { onOpenCollection(it.id) }) }
    }
}
