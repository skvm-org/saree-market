package com.sareemarket.app.ui.seller

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.Booking
import com.sareemarket.app.data.BookingStatus
import com.sareemarket.app.data.BookingSummary
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.SkeletonRows
import com.sareemarket.app.ui.components.StatusPill
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.formatRupees
import com.sareemarket.app.ui.reseller.ContactButtons
import com.sareemarket.app.ui.reseller.rememberContactActions
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing
import com.sareemarket.app.ui.timeAgo
import com.sareemarket.app.ui.timeLeft
import com.sareemarket.app.ui.typeAndFabric

private const val LOAD_MORE_AHEAD_REQUESTS = 4

/** What the seller still has to confirm before an answer is sent. */
private data class Confirm(val booking: Booking, val title: String, val text: String, val label: String, val run: (Long) -> Unit)

/** Booking requests from resellers: answer them, and keep track of how many came and how many were answered. */
@Composable
fun RequestsScreen(onOpenCollection: (Long) -> Unit, viewModel: RequestsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val (call, whatsapp) = rememberContactActions()
    // New requests arrive while the seller is elsewhere, so look again whenever this tab comes back.
    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose {}
    }
    RequestsContent(
        state, onOpenCollection, call, whatsapp, viewModel::selectFilter, viewModel::accept, viewModel::reject,
        viewModel::extend, viewModel::sold, viewModel::loadMore, viewModel::reload, viewModel::pullRefresh,
    )
}

@Composable
fun RequestsContent(
    state: RequestsState,
    onOpenCollection: (Long) -> Unit,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onFilter: (RequestFilter) -> Unit,
    onAccept: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onExtend: (Long) -> Unit,
    onSold: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
) {
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.margin), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Requests", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = Spacing.md).testTag("title"))
            SummaryTiles(state.summary, state.filter, onFilter)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> SkeletonRows()
                    state.items.isEmpty() && state.error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
                    }
                    state.items.isEmpty() -> Text(
                        when (state.filter) {
                            RequestFilter.PENDING -> "No requests waiting.\nWhen a reseller books a saree, it shows here."
                            RequestFilter.ON_HOLD -> "Nothing on hold."
                            RequestFilter.SOLD -> "Nothing sold through requests yet."
                            RequestFilter.ALL -> "No requests yet."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).testTag("empty"),
                    )
                    else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        RequestList(
                            state, onOpenCollection, onCall, onWhatsApp, onAccept,
                            ask = { confirm = it }, onExtend = onExtend, onReject = onReject, onSold = onSold, onLoadMore = onLoadMore,
                        )
                    }
                }
            }
        }
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.text) },
            confirmButton = {
                TextButton(onClick = { confirm = null; c.run(c.booking.id) }, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("confirm")) { Text(c.label) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Not now") } },
        )
    }
}

/** How many requests came and how many were answered. Each count is also a button that filters the list below it. */
@Composable
private fun SummaryTiles(summary: BookingSummary, selected: RequestFilter, onFilter: (RequestFilter) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.testTag("summary")) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Tile("Pending", summary.pending, SareeColors.GoldText, selected == RequestFilter.PENDING, { onFilter(RequestFilter.PENDING) }, Modifier.weight(1f).testTag("tile-pending"))
            Tile("On hold", summary.onHold, SareeColors.OnHoldText, selected == RequestFilter.ON_HOLD, { onFilter(RequestFilter.ON_HOLD) }, Modifier.weight(1f).testTag("tile-on-hold"))
            Tile("Sold", summary.sold, SareeColors.AvailableText, selected == RequestFilter.SOLD, { onFilter(RequestFilter.SOLD) }, Modifier.weight(1f).testTag("tile-sold"))
            Tile("Received", summary.received, MaterialTheme.colorScheme.onSurface, selected == RequestFilter.ALL, { onFilter(RequestFilter.ALL) }, Modifier.weight(1f).testTag("tile-received"))
        }
        if (summary.received > 0) {
            Text(
                "You answered ${summary.responded} of ${summary.received} requests (${summary.responded * 100 / summary.received}%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("response-rate"),
            )
        }
    }
}

@Composable
private fun Tile(label: String, value: Long, color: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    // The chosen tile gets a crimson outline and a tint, the same way the filter chips elsewhere show their choice.
    Surface(
        selected = selected,
        onClick = onClick,
        shape = CardShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primaryContainer) else null,
        modifier = modifier.heightIn(min = MinTouchTarget),
    ) {
        Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun RequestList(
    state: RequestsState,
    onOpenCollection: (Long) -> Unit,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onAccept: (Long) -> Unit,
    ask: (Confirm) -> Unit,
    onExtend: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onSold: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(list, state.items.size) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= state.items.size - LOAD_MORE_AHEAD_REQUESTS) onLoadMore()
        }
    }
    LazyColumn(
        state = list,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
        contentPadding = PaddingValues(bottom = Spacing.lg),
        modifier = Modifier.fillMaxSize().testTag("requests"),
    ) {
        items(state.items, key = { it.id }) { booking ->
            RequestCard(
                booking, busy = state.busyId == booking.id, onOpenCollection, onCall, onWhatsApp, onAccept, onExtend,
                onReject = { ask(Confirm(it, "Reject this request?", "${it.counterparty.name} will be told you cannot hold it.", "Reject", onReject)) },
                onRelease = { ask(Confirm(it, "Release this hold?", "The saree goes back on sale and ${it.counterparty.name} is told.", "Release", onReject)) },
                onSold = { ask(Confirm(it, "Mark as sold?", "The saree becomes sold out, and anyone else waiting for it is told it is gone.", "Mark sold", onSold)) },
            )
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
private fun RequestCard(
    booking: Booking,
    busy: Boolean,
    onOpenCollection: (Long) -> Unit,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onAccept: (Long) -> Unit,
    onExtend: (Long) -> Unit,
    onReject: (Booking) -> Unit,
    onRelease: (Booking) -> Unit,
    onSold: (Booking) -> Unit,
) {
    val item = booking.item
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = CardBorder, modifier = Modifier.fillMaxWidth().testTag("request-${booking.id}")) {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                Modifier.clickable(onClickLabel = "Open collection", role = Role.Button) { onOpenCollection(item.collectionId) },
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SareeImage(imageUrl(item.id, thumb = true), contentDescription = item.colour ?: item.collectionTitle, modifier = Modifier.width(84.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(item.collectionTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(typeAndFabric(item.sareeType, item.fabric), item.colour).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(formatRupees(item.price), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primaryContainer)
                    Text(booking.counterparty.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("reseller-${booking.id}"))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val (label, color) = sellerStatusLook(booking)
                StatusPill(label, color, Modifier.testTag("status-${booking.id}"))
                Text(timeAgo(booking.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            booking.note?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("note-${booking.id}")) }
            if (booking.status.isOpen) {
                ContactButtons(booking.counterparty.phone, booking.counterparty.whatsappNumber, onCall, onWhatsApp)
                when (booking.status) {
                    BookingStatus.PENDING -> {
                        // Only one request can hold a saree; the others wait until it is released or sold.
                        val blocked = item.status != ItemStatus.AVAILABLE
                        if (blocked) Text("Another request has this saree on hold.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Button(onClick = { onAccept(booking.id) }, enabled = !busy && !blocked, modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget).testTag("accept-${booking.id}")) { Text("Hold for 24 h") }
                            OutlinedButton(onClick = { onReject(booking) }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget).testTag("reject-${booking.id}")) { Text("Reject") }
                        }
                        if (!blocked) {
                            TextButton(onClick = { onSold(booking) }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("sold-${booking.id}")) { Text("Already sold to them") }
                        }
                    }
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(onClick = { onSold(booking) }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget).testTag("sold-${booking.id}")) { Text("Mark sold") }
                        OutlinedButton(onClick = { onExtend(booking.id) }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget).testTag("extend-${booking.id}")) { Text("Extend 24 h") }
                        TextButton(onClick = { onRelease(booking) }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("release-${booking.id}")) { Text("Release") }
                    }
                }
            }
        }
    }
}

/** The words and colour for a request's status, from the seller's side. */
internal fun sellerStatusLook(booking: Booking): Pair<String, Color> = when (booking.status) {
    BookingStatus.PENDING -> "New request" to SareeColors.Gold
    BookingStatus.ACCEPTED -> "On hold${booking.holdExpiresAt?.let { " · " + timeLeft(it) } ?: ""}" to SareeColors.OnHold
    BookingStatus.REJECTED -> "Rejected" to SareeColors.SlateNeutral
    BookingStatus.CANCELLED -> "Cancelled by reseller" to SareeColors.SlateNeutral
    BookingStatus.EXPIRED -> "Expired" to SareeColors.SlateNeutral
    BookingStatus.SOLD -> "Sold" to SareeColors.Available
    BookingStatus.CLOSED -> "Closed, sold out" to SareeColors.SlateNeutral
}
