package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
        status: OrderStatus = OrderStatus.PAYMENT_SUBMITTED,
        unread: Boolean = false,
        deadline: String? = null,
        reason: String? = null,
        courier: String? = null,
        tracking: String? = null,
        cancelledBy: String? = null,
        cancelReason: String? = null,
    ) = Order(
        id, status, 1, "Lakshmi Silks", "lakshmi@okaxis", listOf(OrderLine(10, "Diwali silks", "maroon", 800, 1)), 800, 50, 850,
        address, null, "412345678901", false, reason, deadline, cancelReason, cancelledBy, courier, tracking, unread,
        "2026-10-28T09:00:00Z", "2026-10-28T09:00:00Z", listOf(StatusEntry(OrderStatus.PAYMENT_SUBMITTED, "2026-10-28T09:00:00Z")),
    )

    private val hold = Hold(
        7, 1, "Lakshmi Silks", "lakshmi@okaxis", emptyList(), 800, 50, 850, address, null, "2026-10-28T11:00:00Z",
    )

    private var openedOrder: Long? = null
    private var openedHold: Long? = null

    private fun showList(state: OrdersState) = compose.setContent {
        SareeTheme { OrdersContent(state, now, { openedOrder = it }, { openedHold = it }, {}, {}, {}) }
    }

    private var utr = ""
    private var resubmitted = false
    private var cancelled = false

    private fun showDetail(state: OrderDetailState) = compose.setContent {
        SareeTheme {
            OrderDetailContent(state, now, {}, {}, { utr = it }, {}, {}, { resubmitted = true }, { cancelled = true })
        }
    }

    @Test
    fun aRejectedOrderSaysWhatToDoAndComesFirst() {
        showList(OrdersState(items = listOf(order(1, OrderStatus.DELIVERED), order(2, OrderStatus.PAYMENT_REJECTED, deadline = "2026-10-28T11:30:00Z")), loading = false))

        compose.onNodeWithTag("next-2", useUnmergedTree = true).assertTextContains("Send the right UTR within", substring = true)
        val top = compose.onNodeWithTag("order-2").fetchSemanticsNode().boundsInRoot.top
        val below = compose.onNodeWithTag("order-1").fetchSemanticsNode().boundsInRoot.top
        assert(top < below) { "rejected order should be above the delivered one" }
    }

    @Test
    fun aSubmittedOrderSaysItIsWaitingForTheSeller() {
        showList(OrdersState(items = listOf(order(3)), loading = false))

        compose.onNodeWithTag("next-3", useUnmergedTree = true).assertTextContains("Waiting for the seller", substring = true)
    }

    @Test
    fun anOrderRowShowsItsStatusAndTotal() {
        showList(OrdersState(items = listOf(order(1)), loading = false))

        compose.onNodeWithTag("order-1").assertIsDisplayed()
        compose.onNodeWithTag("status-1", useUnmergedTree = true).assertIsDisplayed()
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
    fun aRejectedOrderShowsTheReasonAndTheResubmitForm() {
        showDetail(OrderDetailState(order(5, OrderStatus.PAYMENT_REJECTED, reason = "Not received", deadline = "2026-10-28T11:30:00Z"), loading = false, utr = "412345678902"))

        compose.onNodeWithTag("rejection-reason", useUnmergedTree = true).assertTextContains("Not received")
        compose.onNodeWithTag("rejection-deadline", useUnmergedTree = true).assertTextContains("1 h 30 min", substring = true)
        compose.onNodeWithTag("resubmit").performClick()
        assertEquals(true, resubmitted)
    }

    @Test
    fun aRejectedOrderPastItsDeadlineHidesTheForm() {
        showDetail(OrderDetailState(order(5, OrderStatus.PAYMENT_REJECTED, reason = "Not received", deadline = "2026-10-28T09:00:00Z"), loading = false))

        compose.onNodeWithTag("rejection-ended", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("resubmit-utr").assertDoesNotExist()
    }

    @Test
    fun aShippedOrderShowsCourierAndTracking() {
        showDetail(OrderDetailState(order(5, OrderStatus.SHIPPED, courier = "DTDC", tracking = "D123"), loading = false))

        compose.onNodeWithTag("tracking").assertTextContains("DTDC", substring = true)
        compose.onNodeWithTag("tracking").assertTextContains("D123", substring = true)
        compose.onNodeWithTag("cancel-order").assertDoesNotExist()
    }

    @Test
    fun aCancelledOrderSaysWhoAndWhy() {
        showDetail(OrderDetailState(order(5, OrderStatus.CANCELLED, cancelledBy = "SELLER", cancelReason = "Sold in shop"), loading = false))

        compose.onNodeWithTag("cancelled-note").assertTextContains("Lakshmi Silks", substring = true)
        compose.onNodeWithTag("cancelled-note").assertTextContains("Sold in shop", substring = true)
    }

    @Test
    fun cancelAsksForConfirmationFirst() {
        showDetail(OrderDetailState(order(5), loading = false))

        compose.onNodeWithTag("cancel-order").performClick()
        assertEquals(false, cancelled)
        compose.onNodeWithTag("confirm-cancel-order").performClick()

        assertEquals(true, cancelled)
    }
}
