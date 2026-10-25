package com.sareemarket.app.ui.reseller

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
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

internal const val TAB_CATALOG = "catalog"
internal const val TAB_SELLERS = "sellers"
internal const val TAB_ALERTS = "alerts"
internal val TABS = setOf(TAB_CATALOG, TAB_SELLERS, TAB_ALERTS)

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(TAB_CATALOG, "Catalog", Icons.Filled.Search),
    Tab(TAB_SELLERS, "My sellers", Icons.Filled.Person),
    Tab(TAB_ALERTS, "Alerts", Icons.Filled.Notifications),
)

/** The three places a reseller lives. The Alerts tab carries the unread number (the bell). */
@Composable
fun ResellerBottomBar(current: String?, unread: Long, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = { onSelect(tab.route) },
                icon = {
                    BadgedBox(badge = {
                        if (tab.route == TAB_ALERTS && unread > 0) {
                            Badge(Modifier.testTag("badge")) { Text(if (unread > 99) "99+" else unread.toString()) }
                        }
                    }) { Icon(tab.icon, contentDescription = null) }
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.testTag("tab-${tab.route}"),
            )
        }
    }
}
