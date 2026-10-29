package com.sareemarket.app.ui.seller

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Storefront
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

private data class SellerTab(val route: String, val label: String, val icon: ImageVector)

private val sellerTabs = listOf(
    SellerTab(SELLER_HOME, "Sarees", Icons.Filled.PhotoLibrary),
    SellerTab(SELLER_ORDERS, "Orders", Icons.Filled.Inbox),
    SellerTab(SELLER_NEW, "Add", Icons.Filled.AddCircleOutline),
    SellerTab(SELLER_SHOP, "Shop", Icons.Filled.Storefront),
    SellerTab(ROUTE_PROFILE, "Profile", Icons.Filled.AccountCircle),
)

/** Where a seller works from: collections, orders (with the number waiting for confirmation), a new upload, the shop and the profile. */
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
                    }) { Icon(tab.icon, contentDescription = null) }
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.testTag("seller-tab-${tab.route}"),
            )
        }
    }
}
