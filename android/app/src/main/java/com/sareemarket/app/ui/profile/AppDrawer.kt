package com.sareemarket.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sareemarket.app.BuildConfig
import com.sareemarket.app.data.Role
import com.sareemarket.app.ui.components.BrandMark
import com.sareemarket.app.data.Session
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.Spacing
import kotlinx.coroutines.launch

data class DrawerItem(val route: String, val label: String, val icon: ImageVector)

/** The entries every role has, after its own. */
val accountItems = listOf(
    DrawerItem(ROUTE_PROFILE, "My profile", Icons.Filled.AccountCircle),
    DrawerItem(ROUTE_PASSWORD, "Change password", Icons.Filled.Lock),
)

const val ROUTE_PROFILE = "profile"
const val ROUTE_PASSWORD = "password"

/** Adds the profile and change-password screens to a role's navigation graph. */
fun NavGraphBuilder.accountRoutes(nav: NavController) {
    composable(ROUTE_PROFILE) { ProfileScreen(onBack = { nav.popBackStack() }) }
    composable(ROUTE_PASSWORD) { ChangePasswordScreen(onBack = { nav.popBackStack() }) }
}

fun Role.label() = when (this) {
    Role.SELLER -> "Seller"
    Role.RESELLER -> "Reseller"
    Role.ADMIN -> "Admin"
}

/**
 * The side menu every role shares. [content] gets a function that opens it, for the menu button in each top bar.
 * Swipe-from-edge opens it only while [gesturesEnabled] (on a screen that has no back arrow).
 */
@Composable
fun AppDrawer(
    session: Session,
    items: List<DrawerItem>,
    currentRoute: String?,
    gesturesEnabled: Boolean,
    onSelect: (String) -> Unit,
    onLogout: () -> Unit,
    content: @Composable (openMenu: () -> Unit) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled || drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.padding(horizontal = Spacing.margin, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        BrandMark(32.dp)
                        Text("Saree Market", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primaryContainer)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        // The person's initial in the brand colour, so the menu is clearly "your" account.
                        Box(
                            Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                session.name.trim().take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Column {
                            Text(session.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("drawer-name"))
                            Text(
                                "${session.role.label()} · +91 ${session.phone}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider()
                val colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                )
                items.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        selected = currentRoute == item.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onSelect(item.route)
                        },
                        colors = colors,
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                            .heightIn(min = MinTouchTarget)
                            .testTag("drawer-${item.route}"),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
                NavigationDrawerItem(
                    label = { Text("Log out") },
                    icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                    selected = false,
                    onClick = { confirmLogout = true },
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .heightIn(min = MinTouchTarget)
                        .testTag("drawer-logout"),
                )
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.margin, vertical = Spacing.md),
                )
            }
        },
    ) {
        content { scope.launch { drawerState.open() } }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("You will need your phone number and password to sign in again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        onLogout()
                    },
                    modifier = Modifier.heightIn(min = MinTouchTarget).testTag("confirm-logout"),
                ) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Cancel") }
            },
        )
    }
}

/** The hamburger in a top bar; opens the [AppDrawer]. */
@Composable
fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.heightIn(min = MinTouchTarget).testTag("menu")) {
        Icon(Icons.Filled.Menu, contentDescription = "Open menu")
    }
}
