package com.sareemarket.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import com.sareemarket.app.ui.seller.openSellerTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The seller's bottom bar on a real NavController (the screens are empty boxes). */
class SellerNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: TestNavHostController

    private fun start() = compose.setContent {
        nav = TestNavHostController(LocalContext.current).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
        NavHost(nav, startDestination = "home") {
            listOf("home", "orders", "new", "profile", "shop", "collection/{id}").forEach { route -> composable(route) { Box {} } }
        }
    }

    private fun run(block: () -> Unit) {
        compose.runOnUiThread(block)
        compose.waitForIdle()
    }

    private val route get() = nav.currentBackStackEntry?.destination?.route

    @Test
    fun sareesBringsHomeBackFromEveryTab() {
        start()
        listOf("orders", "profile").forEach { tab ->
            run { nav.openSellerTab(tab) }
            assertEquals(tab, route)
            run { nav.openSellerTab("home") }
            assertEquals("home", route)
        }
    }

    @Test
    fun sareesStillWorksWhenHomeIsNotOnTheBackStack() {
        start()
        // The state that left the tab dead: home popped off, only Profile left.
        run { nav.navigate("profile") { popUpTo("home") { inclusive = true } } }
        assertEquals("profile", route)

        run { nav.openSellerTab("home") }

        assertEquals("home", route)
        run { nav.openSellerTab("orders") }
        assertEquals("orders", route)
        run { nav.openSellerTab("home") }
        assertEquals("home", route)
    }

    @Test
    fun ordersAndProfileReplaceEachOtherInsteadOfPilingUp() {
        start()
        run { nav.openSellerTab("orders") }
        run { nav.openSellerTab("profile") }
        run { nav.openSellerTab("orders") }

        // One system Back from here lands on home, not on a stack of tabs.
        run { nav.popBackStack() }
        assertEquals("home", route)
    }

    @Test
    fun addOpensItsOwnPageAndBackReturns() {
        start()
        run { nav.openSellerTab("orders") }
        run { nav.openSellerTab("new") }
        assertEquals("new", route)

        run { nav.popBackStack() }
        assertEquals("orders", route)
    }
}
