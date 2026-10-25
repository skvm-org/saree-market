package com.sareemarket.app.ui.reseller

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import coil3.compose.AsyncImage
import com.sareemarket.app.ui.components.InitialAvatar
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.dayLabel
import com.sareemarket.app.ui.theme.CardShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.sareemarket.app.ui.profile.MenuButton
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing
import com.sareemarket.app.ui.timeAgo

private const val LOAD_MORE_AHEAD = 4

@Composable
fun NotificationsScreen(onOpenCollection: (Long) -> Unit, onMenu: () -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
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
        onMenu = onMenu,
        onRefresh = viewModel::pullRefresh,
    )
}

@Composable
fun NotificationsContent(state: NotificationsState, onOpen: (AppNotification) -> Unit, onMarkAllRead: () -> Unit, onLoadMore: () -> Unit, onMenu: () -> Unit = {}, onRefresh: () -> Unit = {}) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = Spacing.margin)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MenuButton(onMenu)
                Text("Notifications", style = MaterialTheme.typography.headlineMedium)
                val unread = state.items.count { !it.read }
                if (unread > 0) {
                    Box(
                        Modifier.padding(start = Spacing.sm).size(24.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).testTag("unread-count"),
                        contentAlignment = Alignment.Center,
                    ) { Text(unread.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary) }
                }
                Spacer(Modifier.weight(1f))
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
                    else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        NotificationList(state, onOpen, onLoadMore)
                    }
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
        var previous: String? = null
        state.items.forEach { note ->
            val day = dayLabel(note.updatedAt)
            if (day != previous) {
                previous = day
                item(key = "day-$day") {
                    Text(day, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm).testTag("day-$day"))
                }
            }
            item(key = note.id) { Box(Modifier.animateItem()) { NotificationRow(note, onOpen) } }
        }
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
        shape = CardShape,
        color = if (unread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("note-${notification.id}"),
    ) {
        Row(Modifier.padding(Spacing.sm + Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(SareeColors.SurfaceMuted)) {
                notification.coverItemId?.let {
                    AsyncImage(model = imageUrl(it, thumb = true), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } ?: InitialAvatar(notification.shopName, 48.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(notification.shopName, style = MaterialTheme.typography.labelLarge)
                Text(
                    boldTitle(withoutShop(notification.message, notification.shopName), notification.collectionTitle),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                )
                Text(timeAgo(notification.updatedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (unread) {
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).testTag("unread-dot-${notification.id}"))
            }
        }
    }
}

/** The shop already heads the row, so "Kanchi Weaves added 4 sarees" reads "Added 4 sarees" under it. */
internal fun withoutShop(message: String, shop: String): String =
    if (message.startsWith("$shop ")) message.removePrefix("$shop ").replaceFirstChar { it.uppercase() } else message

/** The message with the collection's name in bold, so the eye lands on what was added to. */
private fun boldTitle(message: String, title: String) = buildAnnotatedString {
    val at = message.indexOf(title)
    if (at < 0) {
        append(message)
    } else {
        append(message.substring(0, at))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(title) }
        append(message.substring(at + title.length))
    }
}
