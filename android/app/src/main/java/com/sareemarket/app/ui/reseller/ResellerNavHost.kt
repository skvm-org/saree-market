package com.sareemarket.app.ui.reseller

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import com.sareemarket.app.ui.components.ProvideSharedScopes
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sareemarket.app.ui.profile.accountRoutes
import com.sareemarket.app.ui.profile.ProfileShortcut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import kotlinx.coroutines.delay

/** How often the unread number on the Alerts tab is refreshed while the app is open. */
private const val UNREAD_POLL_MS = 60_000L

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ResellerNavHost(onLogout: () -> Unit) {
    val nav = rememberNavController()
    val shell: ResellerShellViewModel = hiltViewModel()
    val unread by shell.unread.collectAsStateWithLifecycle()
    val cartCount by shell.cartCount.collectAsStateWithLifecycle()
    val orderBadge by shell.orderBadge.collectAsStateWithLifecycle()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val id = listOf(navArgument("id") { type = NavType.LongType })

    // The bell: refresh the unread number every minute, but only while the app is on screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                shell.refreshUnread()
                delay(UNREAD_POLL_MS)
            }
        }
    }
    // Background check for new collections, and the permission Android 13+ needs to show its notification.
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

    // Collection and seller pages keep the bar; the tab they were opened from stays highlighted. The saree page has none.
    val showBar = route in TABS || route == ROUTE_SELLERS || route == ROUTE_ALERTS || route in BROWSE_ROUTES
    val selectedTab = if (route in BROWSE_ROUTES) nav.currentBackStack.value.lastOrNull { it.destination.route in TABS }?.destination?.route ?: TAB_CATALOG else route
    val openTab: (String) -> Unit = { tab -> nav.openTab(tab) }
    Column(Modifier.fillMaxSize()) {
        // The bar already pads for the system buttons, so the screen above must not pad for them twice.
        Box(Modifier.weight(1f).then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) {
            SharedTransitionLayout {
            val transition = this
            NavHost(navController = nav, startDestination = TAB_CATALOG) {
                composable(TAB_CATALOG) {
                    ProvideSharedScopes(transition, this) {
                        CatalogScreen(
                            onOpenItem = { nav.navigate("item/$it") },
                            onOpenCollection = { nav.navigate("collection/$it") },
                            alertsUnread = unread,
                            onOpenSellers = { nav.navigate(ROUTE_SELLERS) },
                            onOpenAlerts = { nav.navigate(ROUTE_ALERTS) },
                        )
                    }
                }
                composable(TAB_WISHLIST) {
                    WishlistScreen(onOpenItem = { nav.navigate("item/$it") }, onBrowse = { openTab(TAB_CATALOG) })
                }
                composable(TAB_CART) {
                    CartScreen(
                        onOpenItem = { nav.navigate("item/$it") },
                        onOpenHold = { nav.navigate("hold/$it") },
                        onAddAddress = { nav.navigate("addresses") },
                        onDiscover = { openTab(TAB_CATALOG) },
                    )
                }
                composable(TAB_ORDERS) {
                    OrdersScreen(onOpenOrder = { nav.navigate("order/$it") }, onOpenHold = { nav.navigate("hold/$it") }, onBrowse = { openTab(TAB_CATALOG) })
                }
                composable(ROUTE_SELLERS) {
                    MySellersScreen(onOpenSeller = { nav.navigate("seller/$it") }, onDiscover = { openTab(TAB_CATALOG) }, onBack = { nav.popBackStack() })
                }
                composable(ROUTE_ALERTS) {
                    NotificationsScreen(onOpenCollection = { nav.navigate("collection/$it") }, onBack = { nav.popBackStack() })
                }
                composable("addresses") {
                    AddressesScreen(onBack = { nav.popBackStack() })
                }
                composable("hold/{id}", arguments = id) {
                    HoldScreen(
                        onBack = { nav.popBackStack() },
                        onPaid = { orderId -> nav.navigate("order/$orderId") { popUpTo("hold/{id}") { inclusive = true } } },
                        onClosed = { nav.closeHold() },
                        onOpenOrders = { nav.closeHold(TAB_ORDERS) },
                    )
                }
                composable("order/{id}", arguments = id) {
                    OrderDetailScreen(onBack = { nav.popBackStack() })
                }
                composable("item/{id}", arguments = id) {
                    ProvideSharedScopes(transition, this) {
                        ItemDetailScreen(
                            onBack = { nav.popBackStack() },
                            onOpenCollection = { nav.navigate("collection/$it") },
                            onOpenSeller = { nav.navigate("seller/$it") },
                            onOpenCart = { openTab(TAB_CART) },
                        )
                    }
                }
                composable("seller/{id}", arguments = id) {
                    SellerPageScreen(onBack = { nav.popBackStack() }, onOpenCollection = { nav.navigate("collection/$it") })
                }
                composable("collection/{id}", arguments = id) {
                    ProvideSharedScopes(transition, this) {
                        ResellerCollectionScreen(onBack = { nav.popBackStack() }, onOpenItem = { nav.navigate("item/$it") })
                    }
                }
                accountRoutes(
                    nav, onLogout,
                    shortcuts = listOf(
                        ProfileShortcut(Icons.Filled.Inventory2, "My orders", "shortcut-orders") { openTab(TAB_ORDERS) },
                        ProfileShortcut(Icons.Filled.People, "My sellers", "shortcut-sellers") { nav.navigate(ROUTE_SELLERS) },
                        ProfileShortcut(Icons.Filled.Notifications, "Notifications", "shortcut-alerts") { nav.navigate(ROUTE_ALERTS) },
                    ),
                )
            }
            }
        }
        if (showBar) {
            ResellerBottomBar(current = selectedTab, cartCount = cartCount, orderBadge = orderBadge, onSelect = openTab)
        }
    }
}
