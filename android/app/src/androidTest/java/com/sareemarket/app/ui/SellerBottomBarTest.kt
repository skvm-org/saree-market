package com.sareemarket.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sareemarket.app.ui.seller.SellerBottomBar
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SellerBottomBarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theOrdersTabCarriesTheNumberWaitingForConfirmation() {
        compose.setContent { SareeTheme { SellerBottomBar(current = "home", pending = 7) {} } }

        compose.onNodeWithTag("orders-badge", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("7", useUnmergedTree = true).assertExists()
    }

    @Test
    fun bigNumbersAreCapped() {
        compose.setContent { SareeTheme { SellerBottomBar(current = "home", pending = 120) {} } }

        compose.onNodeWithText("99+", useUnmergedTree = true).assertExists()
    }

    @Test
    fun noBadgeWhenNothingWaits() {
        compose.setContent { SareeTheme { SellerBottomBar(current = "home", pending = 0) {} } }

        compose.onNodeWithTag("orders-badge", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun tabsReportWhereToGo() {
        val picked = mutableListOf<String>()
        compose.setContent { SareeTheme { SellerBottomBar(current = "home") { picked += it } } }

        compose.onNodeWithTag("seller-tab-home").performClick()
        compose.onNodeWithTag("seller-tab-orders").performClick()
        compose.onNodeWithTag("seller-tab-new").performClick()
        compose.onNodeWithTag("seller-tab-shop").performClick()
        compose.onNodeWithTag("seller-tab-profile").performClick()

        assertEquals(listOf("home", "orders", "new", "shop", "profile"), picked)
    }

    @Test
    fun theOldRequestsTabIsGone() {
        compose.setContent { SareeTheme { SellerBottomBar(current = "home") {} } }

        compose.onNodeWithTag("seller-tab-requests").assertDoesNotExist()
        compose.onNodeWithTag("requests-badge", useUnmergedTree = true).assertDoesNotExist()
    }
}
