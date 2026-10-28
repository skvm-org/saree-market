package com.sareemarket.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import com.sareemarket.app.ui.reseller.closeHold
import com.sareemarket.app.ui.reseller.openTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The tab and hold navigation of the customer shell, run on a real NavController (the screens are empty boxes). */
class ShellNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: TestNavHostController

    private fun start() = compose.setContent {
        nav = TestNavHostController(LocalContext.current).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
        NavHost(nav, startDestination = "catalog") {
            listOf("catalog", "wishlist", "cart", "orders", "profile", "sellers", "alerts", "hold/{id}", "order/{id}").forEach { route ->
                composable(route) { Box {} }
            }
        }
    }

    private fun run(block: () -> Unit) {
        compose.runOnUiThread(block)
        compose.waitForIdle()
    }

    private val route get() = nav.currentBackStackEntry?.destination?.route

    @Test
    fun releasingAHoldOpenedFromTheCartLandsOnTheCartAndStaysThere() {
        start()
        run { nav.openTab("cart") }
        run { nav.navigate("hold/5") }

        run { nav.closeHold() }
        assertEquals("cart", route)

        run { nav.openTab("orders") }
        assertEquals("orders", route)
        run { nav.openTab("cart") }
        assertEquals("cart", route) // the released hold does not come back with the restored tab
    }

    @Test
    fun releasingAHoldOpenedFromOrdersDoesNotTrapTheOrdersTab() {
        start()
        run { nav.openTab("orders") }
        run { nav.navigate("hold/5") }

        run { nav.closeHold() }
        assertEquals("cart", route)

        run { nav.openTab("orders") }
        assertEquals("orders", route)
    }

    @Test
    fun leavingAlertsByATabKeepsTheCatalogReachable() {
        start()
        run { nav.navigate("alerts") }

        run { nav.openTab("cart") }
        assertEquals("cart", route)
        run { nav.openTab("catalog") }

        assertEquals("catalog", route)
    }

    @Test
    fun leavingMySellersByATabKeepsTheCatalogReachable() {
        start()
        run { nav.navigate("sellers") }

        run { nav.openTab("wishlist") }
        run { nav.openTab("catalog") }

        assertEquals("catalog", route)
    }

    @Test
    fun tabsStillRememberWhereTheCustomerWasInsideThem() {
        start()
        run { nav.openTab("orders") }
        run { nav.navigate("order/9") }

        run { nav.openTab("cart") }
        run { nav.openTab("orders") }

        assertEquals("order/{id}", route)
    }
}
