package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.sareemarket.app.data.AddressSnapshot
import com.sareemarket.app.data.Hold
import com.sareemarket.app.data.Order
import com.sareemarket.app.data.OrderLine
import com.sareemarket.app.data.OrderStatus
import com.sareemarket.app.data.StatusEntry
import com.sareemarket.app.ui.reseller.OrderDetailContent
import com.sareemarket.app.ui.reseller.OrderDetailState
import com.sareemarket.app.ui.reseller.OrdersContent
import com.sareemarket.app.ui.reseller.OrdersState
import com.sareemarket.app.ui.theme.SareeTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OrdersScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-28T10:00:00Z")
    private val address = AddressSnapshot("Home", "9876543210", "12 Gandhi Road", null, "Erode", "Tamil Nadu", "638001")

    private fun order(
        id: Long,
        status: OrderStatus = OrderStatus.CONFIRMED,
        unread: Boolean = false,
        courier: String? = null,
        tracking: String? = null,
        cancelledBy: String? = null,
        cancelReason: String? = null,
    ) = Order(
        id, status, 1, "Lakshmi Silks", "lakshmi@okaxis", listOf(OrderLine(10, "Diwali silks", "maroon", 800, 1)), 800, 50, 850,
        address, null, "412345678901", false, cancelReason, cancelledBy, courier, tracking, unread,
        "2026-10-28T09:00:00Z", "2026-10-28T09:00:00Z", listOf(StatusEntry(OrderStatus.CONFIRMED, "2026-10-28T09:00:00Z")),
    )

    private val hold = Hold(
        7, 1, "Lakshmi Silks", "lakshmi@okaxis", emptyList(), 800, 50, 850, address, null, "2026-10-28T11:00:00Z",
    )

    private var openedOrder: Long? = null
    private var openedHold: Long? = null

    private fun showList(state: OrdersState) = compose.setContent {
        SareeTheme { OrdersContent(state, now, { openedOrder = it }, { openedHold = it }, {}, {}, {}) }
    }

    private var copied: String? = null
    private var received = false
    private var cancelReason: String? = "unset"
    private var openedItem: Long? = null

    private fun showDetail(state: OrderDetailState) = compose.setContent {
        SareeTheme {
            OrderDetailContent(state, {}, {}, { copied = it }, { received = true }, { cancelReason = it }, { openedItem = it })
        }
    }

    @Test
    fun aConfirmedOrderSaysItIsWaitingForTheSellerToShip() {
        showList(OrdersState(items = listOf(order(3)), loading = false))

        compose.onNodeWithTag("next-3", useUnmergedTree = true).assertTextContains("Waiting for the seller to ship", substring = true)
    }

    @Test
    fun aShippedOrderSaysItIsOnItsWay() {
        showList(OrdersState(items = listOf(order(3, OrderStatus.SHIPPED, courier = "DTDC", tracking = "D123")), loading = false))

        compose.onNodeWithTag("next-3", useUnmergedTree = true).assertTextContains("On its way", substring = true)
    }

    @Test
    fun anOrderRowShowsItsStatusAndTotal() {
        showList(OrdersState(items = listOf(order(1)), loading = false))

        compose.onNodeWithTag("order-1").assertIsDisplayed()
        compose.onNodeWithTag("status-1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun anOrderRowShowsThePhotosOfWhatWasOrdered() {
        showList(OrdersState(items = listOf(order(5, OrderStatus.CONFIRMED)), loading = false))

        compose.onNodeWithTag("order-thumbs", useUnmergedTree = true).assertExists()
    }

    @Test
    fun anUnreadOrderHasADot() {
        showList(OrdersState(items = listOf(order(1, unread = true)), loading = false))

        compose.onNodeWithTag("unread-1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun anActiveHoldOpensTheHoldScreen() {
        showList(OrdersState(holds = listOf(hold), loading = false))

        compose.onNodeWithTag("hold-7").performClick()

        assertEquals(7L, openedHold)
    }

    @Test
    fun tappingAnOrderOpensIt() {
        showList(OrdersState(items = listOf(order(3)), loading = false))

        compose.onNodeWithTag("order-3").performClick()

        assertEquals(3L, openedOrder)
    }

    @Test
    fun noOrdersShowsTheHint() {
        showList(OrdersState(loading = false))

        compose.onNodeWithTag("orders-empty").assertIsDisplayed()
    }

    @Test
    fun aShippedOrderShowsCourierAndTrackingNumberAndCopiesTheNumber() {
        showDetail(OrderDetailState(order(5, OrderStatus.SHIPPED, courier = "DTDC", tracking = "D123"), loading = false))

        compose.onNodeWithTag("tracking-courier", useUnmergedTree = true).assertTextContains("DTDC")
        compose.onNodeWithTag("tracking-number", useUnmergedTree = true).assertTextContains("D123")
        compose.onNodeWithTag("copy-tracking").performClick()

        assertEquals("D123", copied)
        compose.onNodeWithTag("copy-tracking").assertTextContains("Copied")
    }

    @Test
    fun aShippedOrderCannotBeCancelledButCanBeMarkedReceivedAfterConfirming() {
        showDetail(OrderDetailState(order(5, OrderStatus.SHIPPED, courier = "DTDC", tracking = "D123"), loading = false))

        compose.onNodeWithTag("cancel-order").assertDoesNotExist()
        compose.onNodeWithTag("receive-order").performClick()
        assertEquals(false, received)
        compose.onNodeWithTag("confirm-receive-order").performClick()

        assertEquals(true, received)
    }

    @Test
    fun aConfirmedOrderHasNoTrackingAndNoReceivedButton() {
        showDetail(OrderDetailState(order(5), loading = false))

        compose.onNodeWithTag("tracking-card").assertDoesNotExist()
        compose.onNodeWithTag("receive-order").assertDoesNotExist()
    }

    @Test
    fun tappingASareeOnTheOrderOpensItsPage() {
        showDetail(OrderDetailState(order(5), loading = false))

        compose.onNodeWithTag("order-line-0").performClick()

        assertEquals(10L, openedItem)
    }

    @Test
    fun aSareeTheSellerHasDeletedHasNoLinkToOpen() {
        val gone = order(5).copy(lines = listOf(OrderLine(null, "Removed saree", null, 800, 1)))
        showDetail(OrderDetailState(gone, loading = false))

        compose.onNodeWithTag("order-line-0").performClick()

        assertEquals(null, openedItem)
    }

    @Test
    fun aCancelledOrderSaysWhoAndWhy() {
        showDetail(OrderDetailState(order(5, OrderStatus.CANCELLED, cancelledBy = "SELLER", cancelReason = "Sold in shop"), loading = false))

        compose.onNodeWithTag("cancelled-note").assertTextContains("Lakshmi Silks", substring = true)
        compose.onNodeWithTag("cancelled-note").assertTextContains("Sold in shop", substring = true)
        compose.onNodeWithTag("cancel-order").assertDoesNotExist()
    }

    @Test
    fun cancellingAConfirmedOrderWarnsAboutTheRefundAndTheReasonIsOptional() {
        showDetail(OrderDetailState(order(5), loading = false))

        compose.onNodeWithTag("cancel-order").performClick()
        compose.onNodeWithText("The app does not refund you", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("reason-confirm").assertIsEnabled()
        assertEquals("unset", cancelReason)
        compose.onNodeWithTag("reason-confirm").performClick()

        assertEquals(null, cancelReason)
    }

    @Test
    fun theCancelReasonIsSentWhenTyped() {
        showDetail(OrderDetailState(order(5), loading = false))

        compose.onNodeWithTag("cancel-order").performClick()
        compose.onNodeWithTag("reason").performTextInput("Ordered by mistake")
        compose.onNodeWithTag("reason-confirm").performClick()

        assertEquals("Ordered by mistake", cancelReason)
    }
}
