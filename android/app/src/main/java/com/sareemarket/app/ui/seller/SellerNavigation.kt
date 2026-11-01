package com.sareemarket.app.ui.seller

import androidx.navigation.NavController

/**
 * What a tap on the seller's bottom bar does. Sarees goes back to the home screen; if home is somehow not on the
 * back stack, it is put there, so the tab never goes dead. Add opens its own page; Orders and Profile replace each other.
 */
internal fun NavController.openSellerTab(tab: String) {
    when (tab) {
        SELLER_HOME ->
            if (!popBackStack(SELLER_HOME, inclusive = false)) {
                navigate(SELLER_HOME) {
                    popUpTo(graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        // "Add" opens a page with its own back arrow.
        SELLER_NEW -> navigate(SELLER_NEW)
        else -> navigate(tab) {
            popUpTo(SELLER_HOME)
            launchSingleTop = true
        }
    }
}
