package com.sareemarket.app.ui.seller

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
import androidx.hilt.navigation.compose.hiltViewModel
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

@Composable
fun SellerNavHost(session: Session, onLogout: () -> Unit) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val shell: SellerShellViewModel = hiltViewModel()
    val shop by shell.shop.collectAsStateWithLifecycle()
    LaunchedEffect(route) { if (route == HOME) shell.reload() }
    val showBar = route == HOME || route == SHOP || route == ROUTE_PROFILE
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
            composable(SHOP) { ShopDetailsScreen(onBack = { nav.popBackStack() }) }
            accountRoutes(nav, onLogout)
        }
        }
        if (showBar) {
            SellerBottomBar(current = route) { tab ->
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
