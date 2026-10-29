package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.sareemarket.app.data.AddressSnapshot
import com.sareemarket.app.data.Order
import com.sareemarket.app.data.OrderLine
import com.sareemarket.app.data.OrderParty
import com.sareemarket.app.data.OrderStatus
import com.sareemarket.app.data.OrderSummary
import com.sareemarket.app.ui.seller.SellerOrderFilter
import com.sareemarket.app.ui.seller.SellerOrdersContent
import com.sareemarket.app.ui.seller.SellerOrdersState
import com.sareemarket.app.ui.theme.SareeTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SellerOrdersScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-28T10:00:00Z")
    private val address = AddressSnapshot("Home", "9876543210", "12 Gandhi Road", null, "Erode", "Tamil Nadu", "638001")

    private fun order(
        id: Long,
        status: OrderStatus = OrderStatus.PAYMENT_SUBMITTED,
        proof: Boolean = true,
        deadline: String? = null,
        courier: String? = null,
        tracking: String? = null,
        cancelledBy: String? = null,
        cancelReason: String? = null,
    ) = Order(
        id, status, 1, "Lakshmi Silks", "lakshmi@okaxis", listOf(OrderLine(10, "Diwali silks", "maroon", 800, 1)), 800, 50, 850,
        address, null, "412345678901", proof, null, deadline, cancelReason, cancelledBy, courier, tracking, false,
        "2026-10-28T09:00:00Z", "2026-10-28T09:00:00Z", emptyList(), OrderParty("Priya", "9876543211"),
    )

    private var filter: SellerOrderFilter? = null
    private var confirmed: Long? = null
    private var rejected: Pair<Long, String>? = null
    private var packed: Long? = null
    private var shipped: Triple<Long, String?, String?>? = null
    private var delivered: Long? = null
    private var cancelled: Pair<Long, String>? = null
    private var settings = false
    private var called: String? = null

    private fun show(state: SellerOrdersState, hasUpi: Boolean = true) = compose.setContent {
        SareeTheme {
            SellerOrdersContent(
                state, hasUpi, now, { settings = true }, { called = it }, {}, { filter = it }, { confirmed = it },
                { id, r -> rejected = id to r }, { packed = it }, { id, c, t -> shipped = Triple(id, c, t) }, { delivered = it },
                { id, r -> cancelled = id to r }, {}, {}, {},
            )
        }
    }

    private fun state(vararg orders: Order, filter: SellerOrderFilter = SellerOrderFilter.ALL) =
        SellerOrdersState(filter = filter, items = orders.toList(), loading = false, summary = OrderSummary(toConfirm = 2, toPack = 1, toShip = 3, shipped = 4))

    @Test
    fun tilesShowTheCountsAndTheSelectedOneIsMarked() {
        show(state(filter = SellerOrderFilter.TO_PACK))

        compose.onNodeWithTag("tile-to-confirm").assertIsDisplayed()
        compose.onNodeWithTag("tile-to-pack").assertIsSelected()
        compose.onNodeWithTag("tile-to-confirm").assertIsNotSelectedCompat()
        compose.onNodeWithTag("count-To confirm", useUnmergedTree = true).assertTextContains("2")
        compose.onNodeWithTag("count-Shipped", useUnmergedTree = true).assertTextContains("4")
    }

    @Test
    fun tappingATileReportsTheFilter() {
        show(state())

        compose.onNodeWithTag("tile-shipped").performClick()

        assertEquals(SellerOrderFilter.SHIPPED, filter)
    }

    @Test
    fun aCardToConfirmShowsTheCustomerTheUtrAndTheThreeActions() {
        show(state(order(1)))

        compose.onNodeWithTag("customer-1", useUnmergedTree = true).assertTextContains("Priya")
        compose.onNodeWithTag("utr-1", useUnmergedTree = true).assertTextContains("412345678901")
        compose.onNodeWithTag("proof-1").assertIsDisplayed()
        compose.onNodeWithTag("act-confirm-1").assertIsEnabled()
        compose.onNodeWithTag("act-reject-1").assertIsDisplayed()
        compose.onNodeWithTag("act-cancel-1").assertIsDisplayed()
    }

    @Test
    fun noScreenshotLinkWithoutAScreenshot() {
        show(state(order(1, proof = false)))

        compose.onNodeWithTag("proof-1").assertDoesNotExist()
    }

    @Test
    fun confirmingAsksWithTheAmountTheUtrAndTheBankWarning() {
        show(state(order(1)))

        compose.onNodeWithTag("act-confirm-1").performClick()
        compose.onNodeWithTag("confirm-payment-dialog").assertIsDisplayed()
        compose.onNodeWithTag("confirm-payment-dialog").assertTextContains("₹850", substring = true)
        compose.onNodeWithTag("confirm-payment-dialog").assertTextContains("412345678901", substring = true)
        compose.onNodeWithTag("confirm-payment-dialog").assertTextContains("Check your bank or UPI app first", substring = true)
        assertEquals(null, confirmed)
        compose.onNodeWithTag("confirm-payment-yes").performClick()

        assertEquals(1L, confirmed)
    }

    @Test
    fun rejectingNeedsAReasonBeforeItCanBeSent() {
        show(state(order(1)))

        compose.onNodeWithTag("act-reject-1").performClick()
        compose.onNodeWithTag("reason-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("reason").performTextInput("Not received yet")
        compose.onNodeWithTag("reason-confirm").assertIsEnabled().performClick()

        assertEquals(1L to "Not received yet", rejected)
    }

    @Test
    fun cancellingNeedsAReasonToo() {
        show(state(order(1, OrderStatus.CONFIRMED)))

        compose.onNodeWithTag("act-cancel-1").performClick()
        compose.onNodeWithTag("reason-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("reason").performTextInput("Sold in the shop")
        compose.onNodeWithTag("reason-confirm").performClick()

        assertEquals(1L to "Sold in the shop", cancelled)
    }

    @Test
    fun aConfirmedCardOffersPacking() {
        show(state(order(2, OrderStatus.CONFIRMED)))

        compose.onNodeWithTag("act-pack-2").performClick()

        assertEquals(2L, packed)
    }

    @Test
    fun aPackedCardOffersShippingAndTheDetailsAreOptional() {
        show(state(order(3, OrderStatus.PACKED)))

        compose.onNodeWithTag("act-ship-3").performClick()
        compose.onNodeWithTag("ship-confirm").performClick()

        assertEquals(Triple(3L, null, null), shipped)
    }

    @Test
    fun shippingSendsTheCourierAndTracking() {
        show(state(order(3, OrderStatus.PACKED)))

        compose.onNodeWithTag("act-ship-3").performClick()
        compose.onNodeWithTag("courier").performTextInput("DTDC")
        compose.onNodeWithTag("tracking").performTextInput("D123")
        compose.onNodeWithTag("ship-confirm").performClick()

        assertEquals(Triple(3L, "DTDC", "D123"), shipped)
    }

    @Test
    fun aShippedCardOffersDelivered() {
        show(state(order(4, OrderStatus.SHIPPED, courier = "DTDC", tracking = "D123")))

        compose.onNodeWithTag("tracking-4", useUnmergedTree = true).assertTextContains("DTDC", substring = true)
        compose.onNodeWithTag("act-deliver-4").performClick()

        assertEquals(4L, delivered)
        compose.onNodeWithTag("act-cancel-4").assertDoesNotExist() // too late to cancel once shipped
    }

    @Test
    fun aRejectedCardWaitsForTheCustomerAndOffersOnlyCancel() {
        show(state(order(5, OrderStatus.PAYMENT_REJECTED, deadline = "2026-10-28T11:30:00Z")))

        compose.onNodeWithTag("waiting-5").assertTextContains("1 h 30 min", substring = true)
        compose.onNodeWithTag("act-confirm-5").assertDoesNotExist()
        compose.onNodeWithTag("act-cancel-5").assertIsDisplayed()
    }

    @Test
    fun aDeliveredCardHasNoActions() {
        show(state(order(6, OrderStatus.DELIVERED)))

        compose.onNodeWithTag("act-deliver-6").assertDoesNotExist()
        compose.onNodeWithTag("act-cancel-6").assertDoesNotExist()
    }

    @Test
    fun aCancelledCardSaysWhoAndWhy() {
        show(state(order(7, OrderStatus.CANCELLED, cancelledBy = "CUSTOMER", cancelReason = "Changed my mind")))

        compose.onNodeWithTag("cancelled-7").assertTextContains("the customer", substring = true)
        compose.onNodeWithTag("cancelled-7").assertTextContains("Changed my mind", substring = true)
    }

    @Test
    fun theCustomerCanBeCalledFromTheCard() {
        show(state(order(1)))

        compose.onNodeWithTag("call", useUnmergedTree = true).performClick()

        assertEquals("9876543211", called)
    }

    @Test
    fun noUpiBannerCallsTheSettingsCallback() {
        show(state(), hasUpi = false)

        compose.onNodeWithTag("no-upi-banner").assertIsDisplayed()
        compose.onNodeWithTag("add-upi").performClick()

        assertEquals(true, settings)
    }

    @Test
    fun theBannerIsHiddenOnceThereIsAUpiId() {
        show(state(), hasUpi = true)

        compose.onNodeWithTag("no-upi-banner").assertDoesNotExist()
    }

    @Test
    fun eachTileHasItsOwnEmptyMessage() {
        show(SellerOrdersState(filter = SellerOrderFilter.TO_PACK, loading = false))

        compose.onNodeWithTag("orders-empty").assertTextContains("Nothing to pack")
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertIsNotSelectedCompat() =
    this.assertIsNotSelected()
