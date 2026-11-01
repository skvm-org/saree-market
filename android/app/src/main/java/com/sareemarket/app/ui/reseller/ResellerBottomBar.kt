package com.sareemarket.app.ui.reseller

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
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

internal const val TAB_CATALOG = "catalog"
internal const val TAB_WISHLIST = "wishlist"
internal const val TAB_CART = "cart"
internal const val TAB_ORDERS = "orders"
internal val TABS = setOf(TAB_CATALOG, TAB_WISHLIST, TAB_CART, TAB_ORDERS, ROUTE_PROFILE)

/** Where "My sellers" and "Alerts" live now: pages opened from the catalog header, not tabs. */
internal const val ROUTE_SELLERS = "sellers"
internal const val ROUTE_ALERTS = "alerts"
/** Pages opened from a tab that keep the bar. The saree page hides it: its own Add to cart / Buy now bar sits at the bottom. */
internal val BROWSE_ROUTES = setOf("collection/{id}", "seller/{id}")

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(TAB_CATALOG, "Catalog", Icons.Outlined.Search, Icons.Filled.Search),
    Tab(TAB_WISHLIST, "Wishlist", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    Tab(TAB_CART, "Cart", Icons.Outlined.ShoppingCart, Icons.Filled.ShoppingCart),
    Tab(TAB_ORDERS, "Orders", Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
    Tab(ROUTE_PROFILE, "Profile", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
)

/** The five places a customer lives. Cart carries the number of sarees in it; Orders, the updates not yet seen plus payments in progress. */
@Composable
fun ResellerBottomBar(current: String?, cartCount: Long, orderBadge: Long, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = { onSelect(tab.route) },
                icon = {
                    BadgedBox(badge = {
                        val count = when (tab.route) {
                            TAB_CART -> cartCount
                            TAB_ORDERS -> orderBadge
                            else -> 0
                        }
                        if (count > 0) {
                            Badge(Modifier.testTag(if (tab.route == TAB_CART) "cart-badge" else "orders-badge")) { Text(if (count > 99) "99+" else count.toString()) }
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
                modifier = Modifier.testTag("tab-${tab.route}"),
            )
        }
    }
}
