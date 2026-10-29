package com.sareemarket.app.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sareemarket.app.data.CollectionSummary
import com.sareemarket.app.ui.seller.SellerHomeContent
import com.sareemarket.app.ui.seller.SellerHomeState
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SellerHomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val diwali = CollectionSummary(7, "Diwali soft silks", "Soft silk", "Pure silk", 125000, 18, 15, 3, 1)

    private fun show(
        state: SellerHomeState,
        onNew: () -> Unit = {},
        onOpen: (Long) -> Unit = {},
        onRetry: () -> Unit = {},
    ) = compose.setContent {
        SareeTheme { SellerHomeContent(state, "Lakshmi", onNew, onOpen, onRetry) }
    }

    @Test
    fun aShopWithCollectionsHasNoFloatingButtonOverTheCards() {
        show(SellerHomeState.Loaded(listOf(diwali)))

        compose.onNodeWithTag("new-collection").assertDoesNotExist()
    }

    @Test
    fun paymentsToConfirmShowABannerThatOpensOrders() {
        var opened = false
        compose.setContent {
            SareeTheme { SellerHomeContent(SellerHomeState.Loaded(listOf(diwali)), "Lakshmi", {}, {}, {}, toConfirm = 2, onOpenOrders = { opened = true }) }
        }

        compose.onNodeWithText("2 payments to confirm").assertExists()
        compose.onNodeWithTag("confirm-banner").performClick()

        assert(opened)
    }

    @Test
    fun noBannerWhenNothingWaits() {
        show(SellerHomeState.Loaded(listOf(diwali)))

        compose.onNodeWithTag("confirm-banner").assertDoesNotExist()
    }

    @Test
    fun emptyDashboardInvitesTheSellerToUpload() {
        show(SellerHomeState.Loaded(emptyList()))

        compose.onNodeWithTag("empty").assertExists()
        compose.onNodeWithTag("new-collection").assertExists()
    }

    @Test
    fun collectionCardShowsTitlePriceAndCounts() {
        show(SellerHomeState.Loaded(listOf(diwali)))

        compose.onNodeWithText("Diwali soft silks").assertExists()
        compose.onNodeWithText("Soft silk · Pure silk").assertExists()
        compose.onNodeWithText("₹1,25,000").assertExists()
        compose.onNodeWithText("15 available · 3 sold out").assertExists()
    }

    @Test
    fun tappingACardOpensThatCollection() {
        var opened = 0L
        show(SellerHomeState.Loaded(listOf(diwali)), onOpen = { opened = it })

        compose.onNodeWithTag("collection-7").performClick()

        assertEquals(7L, opened)
    }

    @Test
    fun newCollectionButtonWorksAndThereIsNoSideMenu() {
        var newClicks = 0
        show(SellerHomeState.Loaded(emptyList()), onNew = { newClicks++ })

        compose.onNodeWithTag("new-collection").performClick()
        compose.onNodeWithTag("menu").assertDoesNotExist()

        assertEquals(1, newClicks)
    }

    @Test
    fun failedLoadShowsTheMessageAndRetry() {
        var retries = 0
        show(SellerHomeState.Failed("Can't reach the server."), onRetry = { retries++ })

        compose.onNodeWithText("Can't reach the server.").assertExists()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun headerShowsTheShopAndTheStockTotals() {
        compose.setContent {
            SareeTheme { SellerHomeContent(SellerHomeState.Loaded(listOf(diwali)), "Lakshmi Silks", {}, {}, {}, location = "Dindigul") }
        }

        compose.onNodeWithTag("shop-name").assertTextEquals("Lakshmi Silks")
        compose.onNodeWithText("Dindigul").assertExists()
        compose.onNodeWithTag("stat-available").assert(hasAnyDescendant(hasText("15")))
        compose.onNodeWithTag("stat-sold-out").assert(hasAnyDescendant(hasText("3")))
    }
}
