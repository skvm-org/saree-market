package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sareemarket.app.data.Item
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.ui.seller.ItemCard
import com.sareemarket.app.ui.seller.ItemFilter
import com.sareemarket.app.ui.seller.StockControls
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AvailabilityControlsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun item(status: ItemStatus) = Item(3, "Peacock blue", null, 4500, status)

    @Test
    fun availableCardShowsInStockWithTheSwitchOn() {
        compose.setContent { SareeTheme { ItemCard(item(ItemStatus.AVAILABLE), {}, {}) } }

        compose.onNodeWithText("In stock").assertExists()
        compose.onNodeWithTag("stock-3").assertIsOn()
    }

    @Test
    fun soldOutCardShowsOutOfStockWithTheSwitchOff() {
        compose.setContent { SareeTheme { ItemCard(item(ItemStatus.SOLD_OUT), {}, {}) } }

        compose.onNodeWithText("Out of stock").assertExists()
        compose.onNodeWithTag("stock-3").assertIsOff()
    }

    @Test
    fun tappingTheSwitchTogglesWithoutOpeningTheCard() {
        var toggles = 0
        var opened = 0
        compose.setContent { SareeTheme { ItemCard(item(ItemStatus.AVAILABLE), { opened++ }, { toggles++ }) } }

        compose.onNodeWithTag("stock-3").performClick()

        assertEquals(1, toggles)
        assertEquals(0, opened)
    }

    @Test
    fun chipsShowCountsAndReportTheChosenFilter() {
        var chosen: ItemFilter? = null
        compose.setContent {
            SareeTheme { StockControls(22, 18, 4, ItemFilter.ALL, busy = false, onFilter = { chosen = it }, onMarkAll = {}) }
        }

        compose.onNodeWithText("All 22").assertExists()
        compose.onNodeWithText("Available 18").assertExists()
        compose.onNodeWithTag("chip-sold-out").performClick()

        assertEquals(ItemFilter.SOLD_OUT, chosen)
    }

    @Test
    fun markAllButtonFlipsToMarkAllAvailableWhenNothingIsInStock() {
        var marked: ItemStatus? = null
        compose.setContent {
            SareeTheme { StockControls(4, 0, 4, ItemFilter.ALL, busy = false, onFilter = {}, onMarkAll = { marked = it }) }
        }

        compose.onNodeWithText("Mark all available").performClick()

        assertEquals(ItemStatus.AVAILABLE, marked)
    }

    @Test
    fun markAllSoldOutIsOfferedWhileSomethingIsInStock() {
        var marked: ItemStatus? = null
        compose.setContent {
            SareeTheme { StockControls(4, 2, 2, ItemFilter.ALL, busy = false, onFilter = {}, onMarkAll = { marked = it }) }
        }

        compose.onNodeWithText("Mark all sold out").performClick()

        assertEquals(ItemStatus.SOLD_OUT, marked)
    }
}
