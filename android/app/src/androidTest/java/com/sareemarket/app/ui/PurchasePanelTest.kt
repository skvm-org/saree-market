package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextEquals
import com.sareemarket.app.data.ItemDetail
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.data.SellerContact
import com.sareemarket.app.ui.reseller.ItemDetailContent
import com.sareemarket.app.ui.reseller.ShoppingUi
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PurchasePanelTest {

    @get:Rule
    val compose = createComposeRule()

    private val seller = SellerContact(7, "Lakshmi Silks", "Chinnalapatti", "9876543210", "9876543211")
    private val detail = ItemDetail(
        10, 5, "Diwali silks", null, "Soft silk", "Pure silk", "maroon", null, 4000, ItemStatus.AVAILABLE, seller,
        quantity = 5, inStock = true,
    )

    private var quantity: Int? = null
    private var removed = false
    private var wished = false

    private fun show(d: ItemDetail = detail, ui: ShoppingUi = ShoppingUi()) = compose.setContent {
        SareeTheme {
            ItemDetailContent(
                d, {}, {}, {}, {}, {}, null, null,
                shopping = ui, onSetQuantity = { quantity = it }, onRemoveFromCart = { removed = true },
                onToggleWishlist = { wished = true },
            ) {}
        }
    }

    @Test
    fun anInStockSareeShowsTheStepperAndAddToCart() {
        show()

        compose.onNodeWithTag("qty-value").assertTextEquals("1")
        compose.onNodeWithTag("add-to-cart").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun addToCartSendsTheChosenQuantity() {
        show()

        compose.onNodeWithTag("qty-plus").performClick()
        compose.onNodeWithTag("qty-plus").performClick()
        compose.onNodeWithTag("add-to-cart").performClick()

        assertEquals(3, quantity)
    }

    @Test
    fun plusStopsAtTheStock() {
        show(detail.copy(quantity = 2))

        compose.onNodeWithTag("qty-plus").performClick()
        compose.onNodeWithTag("qty-plus").assertIsNotEnabled()
        compose.onNodeWithTag("qty-value").assertTextEquals("2")
    }

    @Test
    fun lowStockIsMentionedAtThreeOrFewer() {
        show(detail.copy(quantity = 3))

        compose.onNodeWithTag("low-stock").assertIsDisplayed()
    }

    @Test
    fun plentyOfStockSaysNothing() {
        show()

        compose.onNodeWithTag("low-stock").assertDoesNotExist()
    }

    @Test
    fun anOutOfStockSareeOffersNothingToBuy() {
        show(detail.copy(inStock = false, quantity = 0, status = ItemStatus.SOLD_OUT))

        compose.onNodeWithTag("out-of-stock").assertIsDisplayed()
        compose.onNodeWithTag("add-to-cart").assertDoesNotExist()
        compose.onNodeWithTag("qty-plus").assertDoesNotExist()
    }

    @Test
    fun aSareeInTheCartShowsItsQuantityAndSendsChanges() {
        show(detail.copy(inCart = 2))

        compose.onNodeWithTag("qty-value").assertTextEquals("2")
        compose.onNodeWithTag("add-to-cart").assertDoesNotExist()
        compose.onNodeWithTag("qty-plus").performClick()
        assertEquals(3, quantity)
        compose.onNodeWithTag("remove-from-cart").performClick()
        assertEquals(true, removed)
    }

    @Test
    fun theHeartTogglesTheWishlist() {
        show()

        compose.onNodeWithTag("wishlist").performClick()

        assertEquals(true, wished)
    }

    @Test
    fun anErrorFromTheServerIsShown() {
        show(ui = ShoppingUi(error = "Only 1 left"))

        compose.onNodeWithTag("shopping-error").assertTextEquals("Only 1 left")
    }
}
