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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.AppNotification
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing
import com.sareemarket.app.ui.timeAgo

private const val LOAD_MORE_AHEAD = 4

@Composable
fun NotificationsScreen(onOpenCollection: (Long) -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    NotificationsContent(
        state = state,
        onOpen = { viewModel.open(it); onOpenCollection(it.collectionId) },
        onMarkAllRead = viewModel::markAllRead,
        onLoadMore = viewModel::loadMore,
    )
}

@Composable
fun NotificationsContent(state: NotificationsState, onOpen: (AppNotification) -> Unit, onMarkAllRead: () -> Unit, onLoadMore: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = Spacing.margin)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (state.hasUnread) {
                    TextButton(onClick = onMarkAllRead, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("mark-all-read")) {
                        Text("Mark all read", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (state.error != null) {
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("error"))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.items.isEmpty() -> Text(
                        "No notifications yet.\nFollow sellers to hear when they add new sarees.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).testTag("empty"),
                    )
                    else -> NotificationList(state, onOpen, onLoadMore)
                }
            }
        }
    }
}

@Composable
private fun NotificationList(state: NotificationsState, onOpen: (AppNotification) -> Unit, onLoadMore: () -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(list, state.items.size) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= state.items.size - LOAD_MORE_AHEAD) onLoadMore()
        }
    }
    LazyColumn(
        state = list,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.md),
        modifier = Modifier.fillMaxSize().testTag("list"),
    ) {
        items(state.items, key = { it.id }) { NotificationRow(it, onOpen) }
        if (state.loadingMore) {
            item { Box(Modifier.fillMaxWidth().padding(Spacing.md), Alignment.Center) { CircularProgressIndicator() } }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onOpen: (AppNotification) -> Unit) {
    val unread = !notification.read
    Surface(
        onClick = { onOpen(notification) },
        shape = MaterialTheme.shapes.medium,
        color = if (unread) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, SareeColors.Hairline),
        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("note-${notification.id}"),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
            Box(
                Modifier.padding(top = 6.dp).size(10.dp).let {
                    if (unread) it.then(Modifier.testTag("unread-dot-${notification.id}")) else it
                },
            ) {
                if (unread) Surface(Modifier.size(10.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {}
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    notification.message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(timeAgo(notification.updatedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
