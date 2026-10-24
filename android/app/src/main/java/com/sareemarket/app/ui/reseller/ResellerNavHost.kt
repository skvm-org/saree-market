package com.sareemarket.app.ui.reseller

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun ResellerNavHost(onLogout: () -> Unit) {
    val nav = rememberNavController()
    val id = listOf(navArgument("id") { type = NavType.LongType })
    NavHost(navController = nav, startDestination = "catalog") {
        composable("catalog") {
            CatalogScreen(onOpenItem = { nav.navigate("item/$it") }, onLogout = onLogout)
        }
        composable("item/{id}", arguments = id) {
            ItemDetailScreen(
                onBack = { nav.popBackStack() },
                onOpenCollection = { nav.navigate("collection/$it") },
                onOpenSeller = { nav.navigate("seller/$it") },
            )
        }
        composable("seller/{id}", arguments = id) {
            SellerPageScreen(onBack = { nav.popBackStack() }, onOpenCollection = { nav.navigate("collection/$it") })
        }
        composable("collection/{id}", arguments = id) {
            ResellerCollectionScreen(onBack = { nav.popBackStack() }, onOpenItem = { nav.navigate("item/$it") })
        }
    }
}
