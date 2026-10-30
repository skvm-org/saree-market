package com.sareemarket.app.ui.seller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sareemarket.app.data.Session
import com.sareemarket.app.ui.profile.ShopDetailsScreen
import com.sareemarket.app.ui.profile.ROUTE_PROFILE
import com.sareemarket.app.ui.profile.accountRoutes

private const val HOME = SELLER_HOME
private const val SHOP = SELLER_SHOP

/** How often the number on the Orders tab is refreshed while the app is open. */
private const val PENDING_POLL_MS = 60_000L

@Composable
fun SellerNavHost(session: Session, onLogout: () -> Unit) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val shell: SellerShellViewModel = hiltViewModel()
    val shop by shell.shop.collectAsStateWithLifecycle()
    val pending by shell.pending.collectAsStateWithLifecycle()
    // The shop is read again whenever a screen that shows it comes back, so a UPI ID just saved clears the banner.
    LaunchedEffect(route) { if (route == HOME || route == SELLER_ORDERS || route == ROUTE_PROFILE) shell.reload() }
    // The badge: refresh the waiting requests every minute, but only while the app is on screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                shell.refreshPending()
                delay(PENDING_POLL_MS)
            }
        }
    }
    // Background check for new requests, and the permission Android 13+ needs to show its notification.
    val context = LocalContext.current
    val askForPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        shell.startAlerts()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askForPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val showBar = route == HOME || route == SELLER_ORDERS || route == ROUTE_PROFILE
    Column(Modifier.fillMaxSize()) {
        // The bar already pads for the system buttons, so the screen above must not pad for them twice.
        Box(Modifier.weight(1f).then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) {
        NavHost(navController = nav, startDestination = HOME) {
            composable(HOME) {
                SellerHomeScreen(
                    shopName = shop?.shopName ?: session.name,
                    location = shop?.marketLocation.orEmpty(),
                    onNewCollection = { nav.navigate(SELLER_NEW) },
                    onOpen = { id -> nav.navigate("collection/$id") },
                    toConfirm = pending,
                    onOpenOrders = { nav.navigate(SELLER_ORDERS) { popUpTo(HOME); launchSingleTop = true } },
                    hasUpiId = shopHasUpiId(shop),
                    onAddUpi = { nav.navigate(SHOP) },
                )
            }
            composable(SELLER_NEW) {
                NewCollectionScreen(
                    onBack = { nav.popBackStack() },
                    onCreated = { id -> nav.navigate("collection/$id") { popUpTo(HOME) } },
                )
            }
            composable("collection/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                CollectionDetailScreen(onBack = { nav.popBackStack() })
            }
            composable(SELLER_ORDERS) {
                SellerOrdersScreen(onOpenSettings = { nav.navigate(SHOP) }, hasUpiId = shopHasUpiId(shop))
            }
            composable(SHOP) { ShopDetailsScreen(onBack = { nav.popBackStack() }) }
            accountRoutes(nav, onLogout, onOpenShop = { nav.navigate(SHOP) }, missingUpi = { !shopHasUpiId(shop) })
        }
        }
        if (showBar) {
            SellerBottomBar(current = route, pending = pending) { tab ->
                when (tab) {
                    HOME -> nav.popBackStack(HOME, inclusive = false)
                    // "Add" opens a page with its own back arrow; the other tabs replace each other.
                    SELLER_NEW -> nav.navigate(SELLER_NEW)
                    else -> nav.navigate(tab) { popUpTo(HOME); launchSingleTop = true }
                }
            }
        }
    }
}
