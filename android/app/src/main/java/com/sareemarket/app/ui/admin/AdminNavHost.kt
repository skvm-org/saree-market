package com.sareemarket.app.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sareemarket.app.data.Session
import com.sareemarket.app.ui.profile.ROUTE_PROFILE
import com.sareemarket.app.ui.profile.accountRoutes
import com.sareemarket.app.ui.theme.Spacing

private const val HOME = "home"

/** Admin screens come with the web app; on Android an admin gets a home note and their profile, like the other roles. */
@Composable
fun AdminNavHost(session: Session, onLogout: () -> Unit) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val showBar = route == HOME || route == ROUTE_PROFILE
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) {
            NavHost(navController = nav, startDestination = HOME) {
                composable(HOME) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.safeDrawingPadding().padding(horizontal = Spacing.margin)) {
                            Text("Admin", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = Spacing.sm))
                            Text(
                                "Signed in as ${session.name}.\nAdmin screens come with the web app. Use the API for now.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.md),
                            )
                        }
                    }
                }
                accountRoutes(nav, onLogout)
            }
        }
        if (showBar) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                listOf(Triple(HOME, "Home", Icons.Filled.Home), Triple(ROUTE_PROFILE, "Profile", Icons.Filled.AccountCircle)).forEach { (tab, label, icon) ->
                    NavigationBarItem(
                        selected = route == tab,
                        onClick = { if (tab == HOME) nav.popBackStack(HOME, inclusive = false) else nav.navigate(tab) { popUpTo(HOME); launchSingleTop = true } },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        modifier = Modifier.testTag("admin-tab-$tab"),
                    )
                }
            }
        }
    }
}
