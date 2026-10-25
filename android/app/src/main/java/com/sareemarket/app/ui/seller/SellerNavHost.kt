package com.sareemarket.app.ui.seller

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sareemarket.app.data.Session
import com.sareemarket.app.ui.profile.AppDrawer
import com.sareemarket.app.ui.profile.DrawerItem
import com.sareemarket.app.ui.profile.ShopDetailsScreen
import com.sareemarket.app.ui.profile.accountItems
import com.sareemarket.app.ui.profile.accountRoutes

private const val HOME = "home"
private const val SHOP = "shop"

private val sellerItems = listOf(
    DrawerItem(HOME, "My collections", Icons.Filled.Home),
    DrawerItem(SHOP, "Shop details", Icons.Filled.Settings),
) + accountItems

@Composable
fun SellerNavHost(session: Session, onLogout: () -> Unit) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    AppDrawer(
        session = session,
        items = sellerItems,
        currentRoute = route,
        gesturesEnabled = route == HOME,
        onSelect = { target ->
            if (target == HOME) nav.popBackStack(HOME, inclusive = false) else nav.navigate(target) { launchSingleTop = true }
        },
        onLogout = onLogout,
    ) { openMenu ->
        NavHost(navController = nav, startDestination = HOME) {
            composable(HOME) {
                SellerHomeScreen(
                    sellerName = session.name,
                    onNewCollection = { nav.navigate("new") },
                    onOpen = { id -> nav.navigate("collection/$id") },
                    onMenu = openMenu,
                )
            }
            composable("new") {
                NewCollectionScreen(
                    onBack = { nav.popBackStack() },
                    onCreated = { id -> nav.navigate("collection/$id") { popUpTo(HOME) } },
                )
            }
            composable("collection/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                CollectionDetailScreen(onBack = { nav.popBackStack() })
            }
            composable(SHOP) { ShopDetailsScreen(onBack = { nav.popBackStack() }) }
            accountRoutes(nav)
        }
    }
}
