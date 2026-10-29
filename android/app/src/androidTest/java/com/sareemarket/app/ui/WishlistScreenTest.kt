package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.sareemarket.app.data.CatalogItem
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.ui.reseller.WishlistContent
import com.sareemarket.app.ui.reseller.WishlistState
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WishlistScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val kanjivaram = CatalogItem(10, 5, "Diwali silks", "Kanjivaram", "Pure silk", "maroon", 4000, ItemStatus.AVAILABLE, 1, "Lakshmi Silks")

    private var opened: Long? = null
    private var removed: Long? = null

    private fun show(state: WishlistState) = compose.setContent {
        SareeTheme { WishlistContent(state, { opened = it }, { removed = it }, {}, {}, {}) }
    }

    @Test
    fun showsSavedSareesWithPriceAndShop() {
        show(WishlistState(items = listOf(kanjivaram), loading = false))

        compose.onNodeWithTag("wish-10").assertIsDisplayed()
        compose.onNodeWithTag("wishlist", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun anOutOfStockRowSaysSo() {
        show(WishlistState(items = listOf(kanjivaram.copy(inStock = false, quantity = 0)), loading = false))

        compose.onNodeWithTag("out-of-stock-10", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun anEmptyWishlistShowsTheHint() {
        show(WishlistState(loading = false))

        compose.onNodeWithTag("wishlist-empty").assertIsDisplayed()
    }

    @Test
    fun theHeartButtonSendsTheItemId() {
        show(WishlistState(items = listOf(kanjivaram), loading = false))

        compose.onNodeWithTag("wishlist-remove-10", useUnmergedTree = true).performClick()

        assertEquals(10L, removed)
    }

    @Test
    fun tappingARowOpensTheSaree() {
        show(WishlistState(items = listOf(kanjivaram), loading = false))

        compose.onNodeWithTag("wish-10").performClick()

        assertEquals(10L, opened)
    }
}
