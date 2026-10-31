package com.sareemarket.app.ui.seller

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.sareemarket.app.ui.profile.ROUTE_PROFILE

internal const val SELLER_HOME = "home"
internal const val SELLER_NEW = "new"
internal const val SELLER_SHOP = "shop"
internal const val SELLER_ORDERS = "orders"

private data class SellerTab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val sellerTabs = listOf(
    SellerTab(SELLER_HOME, "Sarees", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
    SellerTab(SELLER_ORDERS, "Orders", Icons.Outlined.Inbox, Icons.Filled.Inbox),
    SellerTab(SELLER_NEW, "Add", Icons.Outlined.AddCircleOutline, Icons.Filled.AddCircleOutline),
    SellerTab(ROUTE_PROFILE, "Profile", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
)

/** Where a seller works from: collections, orders (with the number waiting for confirmation), a new upload, and the profile with the shop details inside it. */
@Composable
fun SellerBottomBar(current: String?, pending: Long = 0, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        sellerTabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = { onSelect(tab.route) },
                icon = {
                    BadgedBox(badge = {
                        if (tab.route == SELLER_ORDERS && pending > 0) {
                            Badge(Modifier.testTag("orders-badge")) { Text(if (pending > 99) "99+" else pending.toString()) }
                        }
                    }) { Icon(if (current == tab.route) tab.selectedIcon else tab.icon, contentDescription = null) }
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag("seller-tab-${tab.route}"),
            )
        }
    }
}
