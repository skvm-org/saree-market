package com.sareemarket.app.ui.reseller

import com.sareemarket.app.ui.components.SkeletonRows
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.components.InitialAvatar
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.timeAgo
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.FollowItem
import androidx.compose.material.icons.filled.Storefront
import com.sareemarket.app.ui.components.ScreenHeader
import com.sareemarket.app.ui.components.EmptyState
import com.sareemarket.app.data.NotifyMode
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing

@Composable
fun MySellersScreen(onOpenSeller: (Long) -> Unit, onDiscover: () -> Unit, onBack: (() -> Unit)? = null, viewModel: MySellersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }
    MySellersContent(
        state, onOpenSeller, viewModel::setMuted, viewModel::unfollow,
        onRefresh = viewModel::pullRefresh, onUndo = viewModel::undoUnfollow, onUndoDismissed = viewModel::dismissUndo, onDiscover = onDiscover, onBack = onBack,
    )
}

@Composable
fun MySellersContent(state: MySellersState, onOpenSeller: (Long) -> Unit, onMute: (Long, Boolean) -> Unit, onUnfollow: (Long) -> Unit,
    onRefresh: () -> Unit = {},
    onUndo: () -> Unit = {},
    onUndoDismissed: () -> Unit = {},
    onDiscover: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(state.undo) {
        val removed = state.undo ?: return@LaunchedEffect
        val result = snackbars.showSnackbar("Unfollowed ${removed.shopName}", actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) onUndo() else onUndoDismissed()
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Box {
        Column(Modifier.safeDrawingPadding()) {
          ScreenHeader("My sellers", onBack = onBack)
          Column(Modifier.weight(1f).padding(horizontal = Spacing.margin)) {
            Text(
                "Following ${state.sellers.size}. You get updates only from sellers you follow; tap the bell to mute one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm).testTag("followed-count"),
            )
            if (state.error != null) {
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("error").padding(top = Spacing.sm))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> SkeletonRows()
                    state.sellers.isEmpty() -> EmptyState(
                        Icons.Filled.Storefront,
                        "You are not following anyone yet.",
                        "Open a seller's page and tap Follow.",
                        Modifier.align(Alignment.Center),
                        actionLabel = "Discover sellers",
                        actionTag = "discover",
                        titleTag = "empty",
                        onAction = onDiscover,
                    )
                    else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
                            contentPadding = PaddingValues(vertical = Spacing.md),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.sellers, key = { it.sellerId }) { SellerRow(it, onOpenSeller, onMute, onUnfollow, Modifier.animateItem()) }
                            item {
                                FilledTonalButton(
                                    onClick = onDiscover,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("discover"),
                                ) {
                                    Icon(Icons.Filled.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("  Discover more sellers", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
          }
        }
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(Spacing.margin))
      }
    }
}

@Composable
private fun SellerRow(seller: FollowItem, onOpen: (Long) -> Unit, onMute: (Long, Boolean) -> Unit, onUnfollow: (Long) -> Unit, modifier: Modifier = Modifier) {
    val notifying = seller.notifyMode == NotifyMode.INSTANT
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = { onOpen(seller.sellerId) },
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth().testTag("seller-${seller.sellerId}"),
    ) {
        Row(Modifier.padding(start = Spacing.md, top = Spacing.sm + Spacing.xs, bottom = Spacing.sm + Spacing.xs, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
            InitialAvatar(seller.shopName, 48.dp, filled = notifying)
            Column(Modifier.weight(1f)) {
                Text(seller.shopName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(seller.marketLocation, seller.lastUploadAt?.let { "Last upload ${timeAgo(it)}" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!notifying) Text("Muted", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(if (notifying) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onMute(seller.sellerId, notifying)
                },
                modifier = Modifier.testTag("notify-${seller.sellerId}"),
            ) {
                Icon(
                    if (notifying) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                    contentDescription = if (notifying) "Mute ${seller.shopName}" else "Turn on notifications for ${seller.shopName}",
                    tint = if (notifying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("more-${seller.sellerId}")) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More for ${seller.shopName}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Unfollow") },
                        modifier = Modifier.heightIn(min = MinTouchTarget).testTag("unfollow-${seller.sellerId}"),
                        onClick = {
                            menuOpen = false
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onUnfollow(seller.sellerId)
                        },
                    )
                }
            }
        }
    }
}
