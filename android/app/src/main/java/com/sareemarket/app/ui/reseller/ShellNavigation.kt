package com.sareemarket.app.ui.reseller

import androidx.navigation.NavController

/**
 * Switches to a tab, remembering where the customer was inside the tab they leave. My sellers and Alerts sit on top of
 * the catalog without being tabs; they are left first, or the catalog's saved stack would bring them back.
 */
internal fun NavController.openTab(tab: String) {
    val current = currentBackStackEntry?.destination?.route
    if (current == ROUTE_SELLERS || current == ROUTE_ALERTS) popBackStack()
    navigate(tab) {
        popUpTo(TAB_CATALOG) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The customer released a hold (or it ended): drop the hold screen for good, then show [then] (the cart, or their orders). Leaving it on the stack
 * would let a tab switch restore it, and its "released" state would send the customer straight back here.
 */
internal fun NavController.closeHold(then: String = TAB_CART) {
    popBackStack()
    openTab(then)
}
