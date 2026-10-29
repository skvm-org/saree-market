package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sareemarket.app.ui.reseller.CatalogHeader
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CatalogHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    private var sellers = 0
    private var alerts = 0

    private fun show(unread: Long) = compose.setContent {
        SareeTheme { CatalogHeader(unread, { sellers++ }, { alerts++ }) }
    }

    @Test
    fun theButtonsCallTheirCallbacks() {
        show(0)

        compose.onNodeWithTag("open-sellers").performClick()
        compose.onNodeWithTag("open-alerts").performClick()

        assertEquals(1, sellers)
        assertEquals(1, alerts)
    }

    @Test
    fun theBellShowsTheUnreadNumber() {
        show(4)

        compose.onNodeWithTag("badge", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("4", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun noBadgeWhenNothingIsUnread() {
        show(0)

        compose.onNodeWithTag("badge", useUnmergedTree = true).assertDoesNotExist()
    }
}
