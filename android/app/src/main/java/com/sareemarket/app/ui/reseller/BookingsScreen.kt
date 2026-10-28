package com.sareemarket.app.ui.reseller

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.Booking
import com.sareemarket.app.data.BookingStatus
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.SkeletonRows
import com.sareemarket.app.ui.components.StatusPill
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.formatRupees
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing
import com.sareemarket.app.ui.timeAgo
import com.sareemarket.app.ui.timeLeft
import com.sareemarket.app.ui.typeAndFabric

private const val LOAD_MORE_AHEAD_BOOKINGS = 4

/** The reseller's booking requests: what was asked, what the seller said, and how long a hold lasts. */
@Composable
fun BookingsScreen(onOpenItem: (Long) -> Unit, viewModel: BookingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val (call, whatsapp) = rememberContactActions()
    // Answers can arrive while another tab is open, so look again whenever this tab comes back.
    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose {}
    }
    BookingsContent(state, onOpenItem, call, whatsapp, viewModel::cancel, viewModel::loadMore, viewModel::reload, viewModel::pullRefresh)
}

@Composable
fun BookingsContent(
    state: BookingsState,
    onOpenItem: (Long) -> Unit,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onCancel: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.margin)) {
            Text("My bookings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = Spacing.md).testTag("title"))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> SkeletonRows()
                    state.items.isEmpty() && state.error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
                    }
                    state.items.isEmpty() -> Text(
                        "No booking requests yet.\nOpen a saree and tap Book this saree.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).testTag("empty"),
                    )
                    else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        BookingList(state, onOpenItem, onCall, onWhatsApp, onCancel, onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingList(
    state: BookingsState,
    onOpenItem: (Long) -> Unit,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onCancel: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(list, state.items.size) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= state.items.size - LOAD_MORE_AHEAD_BOOKINGS) onLoadMore()
        }
    }
    LazyColumn(
        state = list,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
        contentPadding = PaddingValues(bottom = Spacing.lg),
        modifier = Modifier.fillMaxSize().testTag("bookings"),
    ) {
        items(state.items, key = { it.id }) { booking ->
            BookingCard(booking, state.busyId == booking.id, onOpenItem, onCall, onWhatsApp, onCancel)
        }
        state.error?.let {
            item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error")) }
        }
        if (state.loadingMore) {
            item { Box(Modifier.fillMaxWidth().padding(Spacing.md), Alignment.Center) { CircularProgressIndicator() } }
        }
    }
}

@Composable
private fun BookingCard(
    booking: Booking,
    busy: Boolean,
    onOpenItem: (Long) -> Unit,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onCancel: (Long) -> Unit,
) {
    val item = booking.item
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = CardBorder, modifier = Modifier.fillMaxWidth().testTag("booking-${booking.id}")) {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                Modifier.clickable(onClickLabel = "Open saree", role = Role.Button) { onOpenItem(item.id) },
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SareeImage(imageUrl(item.id, thumb = true), contentDescription = item.colour ?: item.collectionTitle, modifier = Modifier.width(84.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(item.collectionTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (booking.unread) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(10.dp).testTag("unread-${booking.id}")) {}
                        }
                    }
                    Text(typeAndFabric(item.sareeType, item.fabric), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatRupees(item.price), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primaryContainer)
                    Text(booking.counterparty.name, style = MaterialTheme.typography.labelMedium, color = SareeColors.SlateNeutral, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val (label, color) = statusLook(booking)
                StatusPill(label, color, Modifier.testTag("status-${booking.id}"))
                Text(timeAgo(booking.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (booking.status.isOpen) {
                    TextButton(onClick = { onCancel(booking.id) }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("cancel-${booking.id}")) { Text("Cancel") }
                }
            }
            booking.note?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (booking.status.isOpen) {
                ContactButtons(booking.counterparty.phone, booking.counterparty.whatsappNumber, onCall, onWhatsApp)
            }
        }
    }
}

/** The words and colour for a request's status, from the reseller's side. */
internal fun statusLook(booking: Booking): Pair<String, Color> = when (booking.status) {
    BookingStatus.PENDING -> "Waiting for reply" to SareeColors.Gold
    BookingStatus.ACCEPTED -> "On hold${booking.holdExpiresAt?.let { " · " + timeLeft(it) } ?: ""}" to SareeColors.Available
    BookingStatus.REJECTED -> "Declined" to SareeColors.SlateNeutral
    BookingStatus.CANCELLED -> "Cancelled" to SareeColors.SlateNeutral
    BookingStatus.EXPIRED -> "Expired" to SareeColors.SlateNeutral
    BookingStatus.SOLD -> "Sold to you" to SareeColors.Available
    BookingStatus.CLOSED -> "Sold to someone else" to SareeColors.SlateNeutral
}
