package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.sareemarket.app.data.CartLine
import com.sareemarket.app.data.CartSeller
import com.sareemarket.app.data.CheckoutResult
import com.sareemarket.app.data.SkippedSeller
import com.sareemarket.app.data.Address
import com.sareemarket.app.data.Cart
import com.sareemarket.app.ui.reseller.CartContent
import com.sareemarket.app.ui.reseller.CartState
import com.sareemarket.app.ui.reseller.CheckoutSheet
import com.sareemarket.app.ui.reseller.CheckoutUi
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CartScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun line(id: Long, qty: Int = 1, max: Int = 5, inStock: Boolean = true) =
        CartLine(id, "Diwali silks", "maroon", 800, qty, max, inStock, if (inStock) 800 * minOf(qty, max) else 0)

    private fun seller(id: Long, lines: List<CartLine>, accepts: Boolean = true, fee: Int = 50) = CartSeller(
        id, "Lakshmi Silks", accepts, fee, lines, lines.sumOf { it.lineTotal },
        if (lines.any { it.inStock }) lines.sumOf { it.lineTotal } + fee else 0,
    )

    private var paid: Long? = null
    private var quantity: Pair<Long, Int>? = null
    private var removed: Long? = null
    private var discovered = false

    private fun show(state: CartState) = compose.setContent {
        SareeTheme {
            CartContent(state, {}, { discovered = true }, { id, q -> quantity = id to q }, { removed = it }, { paid = it }, {}, {})
        }
    }

    private fun cart(vararg sellers: CartSeller) = CartState(cart = Cart(sellers.toList(), sellers.sumOf { it.lines.size }), loading = false)

    @Test
    fun showsSellerGroupsWithShippingAndTotals() {
        show(cart(seller(1, listOf(line(10, qty = 2)))))

        compose.onNodeWithTag("seller-1").assertIsDisplayed()
        compose.onNodeWithTag("total-1", useUnmergedTree = true).assertTextEquals("₹1,650")
        compose.onNodeWithTag("pay-1").assertIsEnabled()
    }

    @Test
    fun payIsReplacedByAReasonWhenTheSellerIsNotAccepting() {
        show(cart(seller(1, listOf(line(10)), accepts = false)))

        compose.onNodeWithTag("not-accepting-1").assertIsDisplayed()
        compose.onNodeWithTag("pay-1").assertDoesNotExistCompat()
    }

    @Test
    fun anOutOfStockLineIsDimmedAndOffersRemove() {
        show(cart(seller(1, listOf(line(10), line(11, inStock = false)))))

        compose.onNodeWithTag("out-of-stock-11", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("cart-remove-11", useUnmergedTree = true).performClick()
        assertEquals(11L, removed)
    }

    @Test
    fun aGroupWithNothingInStockCannotBePaid() {
        show(cart(seller(1, listOf(line(10, inStock = false)))))

        compose.onNodeWithTag("nothing-in-stock-1").assertIsDisplayed()
        compose.onNodeWithTag("pay-1").assertDoesNotExistCompat()
    }

    @Test
    fun aQuantityAboveStockSaysHowManyAreAvailable() {
        show(cart(seller(1, listOf(line(10, qty = 4, max = 2)))))

        compose.onNodeWithTag("only-available-10", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("cart-qty-10", useUnmergedTree = true).assertTextEquals("2")
        compose.onNodeWithTag("cart-plus-10", useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test
    fun theStepperSendsQuantityChanges() {
        show(cart(seller(1, listOf(line(10, qty = 2)))))

        compose.onNodeWithTag("cart-plus-10", useUnmergedTree = true).performClick()

        assertEquals(10L to 3, quantity)
    }

    @Test
    fun payStartsCheckoutForThatSeller() {
        show(cart(seller(7, listOf(line(10)))))

        compose.onNodeWithTag("pay-7").performClick()

        assertEquals(7L, paid)
    }

    @Test
    fun anEmptyCartOffersToBrowse() {
        show(CartState(loading = false))

        compose.onNodeWithTag("cart-empty").assertIsDisplayed()
        compose.onNodeWithTag("browse").performClick()
        assertEquals(true, discovered)
    }

    @Test
    fun theCheckoutSheetEnablesContinueOnlyWithAnAddress() {
        val home = Address(1, "Home", "9876543210", "12 Gandhi Road", null, "Erode", "Tamil Nadu", "638001", true)
        var selected: Long? = null
        compose.setContent {
            SareeTheme {
                val ui = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(CheckoutUi(seller(1, listOf(line(10))), listOf(home), null, false))
                }
                CheckoutSheet(ui.value, { selected = it; ui.value = ui.value.copy(selectedAddressId = it) }, {}, {}, {}, {})
            }
        }

        compose.onNodeWithTag("continue-payment").assertIsNotEnabled()
        compose.onNodeWithTag("address-choice-1").performClick()
        compose.onNodeWithTag("continue-payment").assertIsEnabled()
        assertEquals(1L, selected)
    }

    @Test
    fun theCheckoutSheetExplainsWhyNothingWasHeld() {
        compose.setContent {
            SareeTheme {
                CheckoutSheet(
                    CheckoutUi(
                        seller(1, listOf(line(10))), emptyList(), null, false,
                        result = CheckoutResult(emptyList(), emptyList(), listOf(SkippedSeller(1, "Lakshmi Silks", "NOTHING_IN_STOCK"))),
                    ),
                    {}, {}, {}, {}, {},
                )
            }
        }

        compose.onNodeWithTag("checkout-result").assertIsDisplayed()
        compose.onNodeWithTag("no-addresses").assertIsDisplayed()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() =
    this.assertDoesNotExist()
