package com.sareemarket.app.ui.reseller

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
import com.sareemarket.app.data.NotifyMode
import com.sareemarket.app.ui.profile.MenuButton
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing

@Composable
fun MySellersScreen(onOpenSeller: (Long) -> Unit, onMenu: () -> Unit, viewModel: MySellersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }
    MySellersContent(
        state, onOpenSeller, viewModel::setMuted, viewModel::unfollow, onMenu,
        onRefresh = viewModel::pullRefresh, onUndo = viewModel::undoUnfollow, onUndoDismissed = viewModel::dismissUndo,
    )
}

@Composable
fun MySellersContent(state: MySellersState, onOpenSeller: (Long) -> Unit, onMute: (Long, Boolean) -> Unit, onUnfollow: (Long) -> Unit,
    onMenu: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onUndo: () -> Unit = {},
    onUndoDismissed: () -> Unit = {},
) {
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(state.undo) {
        val removed = state.undo ?: return@LaunchedEffect
        val result = snackbars.showSnackbar("Unfollowed ${removed.shopName}", actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) onUndo() else onUndoDismissed()
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Box {
        Column(Modifier.safeDrawingPadding().padding(horizontal = Spacing.margin)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MenuButton(onMenu)
                Text("My sellers", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = Spacing.sm))
            }
            Text(
                "Mute a seller to stop their notifications. You still see their sarees under Following.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.error != null) {
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("error").padding(top = Spacing.sm))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.sellers.isEmpty() -> Text(
                        "You are not following anyone yet.\nOpen a seller's page and tap Follow.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).testTag("empty"),
                    )
                    else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
                            contentPadding = PaddingValues(vertical = Spacing.md),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.sellers, key = { it.sellerId }) { SellerRow(it, onOpenSeller, onMute, onUnfollow, Modifier.animateItem()) }
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
    Surface(
        onClick = { onOpen(seller.sellerId) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, SareeColors.Hairline),
        modifier = modifier.fillMaxWidth().testTag("seller-${seller.sellerId}"),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(seller.shopName, style = MaterialTheme.typography.titleLarge)
            Text(seller.marketLocation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (notifying) "Notifications on" else "Muted",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (notifying) SareeColors.Available else SareeColors.SlateNeutral,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = notifying,
                    onCheckedChange = {
                        haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        onMute(seller.sellerId, !it)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = SareeColors.Available, checkedThumbColor = Color.White),
                    modifier = Modifier.testTag("notify-${seller.sellerId}"),
                )
                TextButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onUnfollow(seller.sellerId) }, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("unfollow-${seller.sellerId}")) {
                    Text("Unfollow", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
