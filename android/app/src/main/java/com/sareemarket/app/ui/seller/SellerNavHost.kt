package com.sareemarket.app.ui.seller

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sareemarket.app.data.Session

@Composable
fun SellerNavHost(session: Session, onLogout: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            SellerHomeScreen(
                sellerName = session.name,
                onNewCollection = { nav.navigate("new") },
                onOpen = { id -> nav.navigate("collection/$id") },
                onLogout = onLogout,
            )
        }
        composable("new") {
            NewCollectionScreen(
                onBack = { nav.popBackStack() },
                onCreated = { id -> nav.navigate("collection/$id") { popUpTo("home") } },
            )
        }
        composable("collection/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            CollectionDetailScreen(onBack = { nav.popBackStack() })
        }
    }
}
