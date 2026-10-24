package com.sareemarket.app.ui

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
        onLogout: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = compose.setContent {
        SareeTheme { SellerHomeContent(state, "Lakshmi", onNew, onOpen, onLogout, onRetry) }
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
        compose.onNodeWithText("15 available").assertExists()
        compose.onNodeWithText("3 sold out").assertExists()
    }

    @Test
    fun tappingACardOpensThatCollection() {
        var opened = 0L
        show(SellerHomeState.Loaded(listOf(diwali)), onOpen = { opened = it })

        compose.onNodeWithTag("collection-7").performClick()

        assertEquals(7L, opened)
    }

    @Test
    fun newCollectionAndLogOutButtonsWork() {
        var newClicks = 0
        var logouts = 0
        show(SellerHomeState.Loaded(emptyList()), onNew = { newClicks++ }, onLogout = { logouts++ })

        compose.onNodeWithTag("new-collection").performClick()
        compose.onNodeWithText("Log out").performClick()

        assertEquals(1, newClicks)
        assertEquals(1, logouts)
    }

    @Test
    fun failedLoadShowsTheMessageAndRetry() {
        var retries = 0
        show(SellerHomeState.Failed("Can't reach the server."), onRetry = { retries++ })

        compose.onNodeWithText("Can't reach the server.").assertExists()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }
}
