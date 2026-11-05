package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SellerOrdersScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val address = AddressSnapshot("Home", "9876543210", "12 Gandhi Road", null, "Erode", "Tamil Nadu", "638001")

    private fun order(
        id: Long,
        status: OrderStatus = OrderStatus.CONFIRMED,
        proof: Boolean = true,
        courier: String? = null,
        tracking: String? = null,
        cancelledBy: String? = null,
        cancelReason: String? = null,
    ) = Order(
        id, status, 1, "Lakshmi Silks", "lakshmi@okaxis", listOf(OrderLine(10, "Diwali silks", "maroon", 800, 1)), 800, 50, 850,
        address, null, "412345678901", proof, cancelReason, cancelledBy, courier, tracking, false,
        "2026-10-28T09:00:00Z", "2026-10-28T09:00:00Z", emptyList(), OrderParty("Priya", "9876543211"),
    )

    private var filter: SellerOrderFilter? = null
    private var shipped: Triple<Long, String, String>? = null
    private var edited: Triple<Long, String, String>? = null
    private var cancelled: Pair<Long, String>? = null
    private var settings = false
    private var called: String? = null

    private fun show(state: SellerOrdersState, hasUpi: Boolean = true) = compose.setContent {
        SareeTheme {
            SellerOrdersContent(
                state, hasUpi, { settings = true }, { called = it }, {}, { filter = it },
                { id, c, t -> shipped = Triple(id, c, t) }, { id, c, t -> edited = Triple(id, c, t) },
                { id, r -> cancelled = id to r }, {}, {}, {},
            )
        }
    }

    private fun state(vararg orders: Order, filter: SellerOrderFilter = SellerOrderFilter.ALL) =
        SellerOrdersState(filter = filter, items = orders.toList(), loading = false, summary = OrderSummary(toShip = 3, shipped = 4))

    @Test
    fun theThreeFiltersAreVisibleAndTheOldStagesAreGone() {
        show(state())

        listOf("to-ship", "shipped", "all").forEach { compose.onNodeWithTag("tile-$it").assertIsDisplayed() }
        listOf("to-confirm", "to-pack").forEach { compose.onNodeWithTag("tile-$it").assertDoesNotExist() }
        compose.onNodeWithText("·").assertDoesNotExist()
    }

    @Test
    fun theCustomerCanBeReachedOnWhatsAppFromTheCard() {
        var chat: String? = null
        compose.setContent {
            SareeTheme {
                SellerOrdersContent(state(order(1)), true, {}, {}, { chat = it }, {}, { _, _, _ -> }, { _, _, _ -> }, { _, _ -> }, {}, {}, {})
            }
        }

        compose.onNodeWithTag("whatsapp", useUnmergedTree = true).performClick()

        assertEquals("9876543211", chat)
    }

    @Test
    fun aDeliveredOrderHidesItsAddressLine() {
        show(state(order(1, OrderStatus.DELIVERED)))

        compose.onNodeWithText("Erode 638001").assertDoesNotExist()
        compose.onNodeWithTag("total-1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun tilesShowTheCountsAndTheSelectedOneIsMarked() {
        show(state(filter = SellerOrderFilter.TO_SHIP))

        compose.onNodeWithTag("tile-to-ship").assertIsSelected()
        compose.onNodeWithTag("tile-shipped").assertIsNotSelected()
        compose.onNodeWithTag("count-To ship", useUnmergedTree = true).assertTextContains("3")
        compose.onNodeWithTag("count-Shipped", useUnmergedTree = true).assertTextContains("4")
    }

    @Test
    fun tappingATileReportsTheFilter() {
        show(state())

        compose.onNodeWithTag("tile-shipped").performClick()

        assertEquals(SellerOrderFilter.SHIPPED, filter)
    }

    @Test
    fun aToShipCardShowsTheCustomerTheUtrAndOnlyAddTrackingAndCancel() {
        show(state(order(1)))

        compose.onNodeWithTag("customer-1", useUnmergedTree = true).assertTextContains("Priya")
        compose.onNodeWithTag("utr-1", useUnmergedTree = true).assertTextContains("412345678901", substring = true)
        compose.onNodeWithTag("proof-1").assertIsDisplayed()
        compose.onNodeWithTag("act-ship-1").assertIsEnabled()
        compose.onNodeWithTag("act-cancel-1").assertIsDisplayed()
        compose.onNodeWithTag("act-confirm-1").assertDoesNotExist()
        compose.onNodeWithTag("act-reject-1").assertDoesNotExist()
        compose.onNodeWithTag("act-pack-1").assertDoesNotExist()
    }

    @Test
    fun aCardShowsTheCustomersAvatarNextToTheirName() {
        show(state(order(1)))

        compose.onNodeWithTag("customer-avatar-1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun noScreenshotLinkWithoutAScreenshot() {
        show(state(order(1, proof = false)))

        compose.onNodeWithTag("proof-1").assertDoesNotExist()
    }

    @Test
    fun cancellingNeedsAReasonBeforeItCanBeSent() {
        show(state(order(1)))

        compose.onNodeWithTag("act-cancel-1").performClick()
        compose.onNodeWithTag("reason-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("reason").performTextInput("Payment never arrived")
        compose.onNodeWithTag("reason-confirm").assertIsEnabled().performClick()

        assertEquals(1L to "Payment never arrived", cancelled)
    }

    @Test
    fun addTrackingNeedsBothTheCourierAndTheNumber() {
        show(state(order(3)))

        compose.onNodeWithTag("act-ship-3").performClick()
        compose.onNodeWithTag("tracking-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("courier").performTextInput("DTDC")
        compose.onNodeWithTag("tracking-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("tracking").performTextInput("D123")
        compose.onNodeWithTag("tracking-confirm").assertIsEnabled().performClick()

        assertEquals(Triple(3L, "DTDC", "D123"), shipped)
    }

    @Test
    fun aShippedCardShowsTheTrackingAndOffersEditNotDeliveredOrCancel() {
        show(state(order(4, OrderStatus.SHIPPED, courier = "DTDC", tracking = "D123")))

        compose.onNodeWithTag("tracking-4", useUnmergedTree = true).assertTextContains("DTDC", substring = true)
        compose.onNodeWithTag("act-edit-tracking-4").assertIsDisplayed()
        compose.onNodeWithTag("act-deliver-4").assertDoesNotExist()
        compose.onNodeWithTag("act-cancel-4").assertDoesNotExist() // too late to cancel once shipped
    }

    @Test
    fun editingTheTrackingStartsFromTheCurrentValuesAndSendsTheCorrection() {
        show(state(order(4, OrderStatus.SHIPPED, courier = "DTDC", tracking = "D123")))

        compose.onNodeWithTag("act-edit-tracking-4").performClick()
        compose.onNodeWithTag("tracking").assertTextContains("D123")
        compose.onNodeWithTag("tracking").performTextClearance()
        compose.onNodeWithTag("tracking").performTextInput("X9")
        compose.onNodeWithTag("tracking-confirm").assertIsEnabled().performClick()

        assertEquals(Triple(4L, "DTDC", "X9"), edited)
    }

    @Test
    fun aDeliveredCardHasNoActions() {
        show(state(order(6, OrderStatus.DELIVERED)))

        compose.onNodeWithTag("act-ship-6").assertDoesNotExist()
        compose.onNodeWithTag("act-edit-tracking-6").assertDoesNotExist()
        compose.onNodeWithTag("act-cancel-6").assertDoesNotExist()
    }

    @Test
    fun aCancelledCardSaysWhoAndWhy() {
        show(state(order(7, OrderStatus.CANCELLED, cancelledBy = "CUSTOMER", cancelReason = "Changed my mind")))

        compose.onNodeWithTag("cancelled-7").assertTextContains("the customer", substring = true)
        compose.onNodeWithTag("cancelled-7").assertTextContains("Changed my mind", substring = true)
    }

    @Test
    fun whileOneOrderIsBusyEveryOtherActionIsOffSoNoTapIsSilentlyDropped() {
        show(state(order(1), order(2, OrderStatus.SHIPPED, courier = "DTDC", tracking = "D1")).copy(busyId = 2))

        compose.onNodeWithTag("act-ship-1").assertIsNotEnabled()
        compose.onNodeWithTag("act-cancel-1").assertIsNotEnabled()
        compose.onNodeWithTag("act-edit-tracking-2").assertIsNotEnabled()
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
        show(SellerOrdersState(filter = SellerOrderFilter.TO_SHIP, loading = false))

        compose.onNodeWithTag("orders-empty").assertTextContains("Nothing waiting to be shipped", substring = true)
    }
}
