package com.sareemarket.app.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sareemarket.app.data.Session
import com.sareemarket.app.ui.profile.AppDrawer
import com.sareemarket.app.ui.profile.DrawerItem
import com.sareemarket.app.ui.profile.MenuButton
import com.sareemarket.app.ui.profile.accountItems
import com.sareemarket.app.ui.profile.accountRoutes
import com.sareemarket.app.ui.theme.Spacing

private const val HOME = "home"

private val adminItems = listOf(DrawerItem(HOME, "Home", Icons.Filled.Home)) + accountItems

/** Admin screens come with the web app; on Android an admin only gets the menu, profile and log out. */
@Composable
fun AdminNavHost(session: Session, onLogout: () -> Unit) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    AppDrawer(
        session = session,
        items = adminItems,
        currentRoute = route,
        gesturesEnabled = route == HOME,
        onSelect = { target ->
            if (target == HOME) nav.popBackStack(HOME, inclusive = false) else nav.navigate(target) { launchSingleTop = true }
        },
        onLogout = onLogout,
    ) { openMenu ->
        NavHost(navController = nav, startDestination = HOME) {
            composable(HOME) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding().padding(horizontal = Spacing.margin)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MenuButton(openMenu)
                            Text("Admin", style = MaterialTheme.typography.headlineMedium)
                        }
                        Text(
                            "Signed in as ${session.name}.\nAdmin screens come with the web app. Use the API for now.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = Spacing.xl),
                        )
                    }
                }
            }
            accountRoutes(nav)
        }
    }
}
