package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sareemarket.app.data.Booking
import com.sareemarket.app.data.BookingStatus
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.ui.reseller.BookingsContent
import com.sareemarket.app.ui.reseller.BookingsState
import com.sareemarket.app.ui.theme.SareeTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BookingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val shop = com.sareemarket.app.data.BookingParty(1, "Lakshmi Silks", "9876543210", "9876543211")

    private fun booking(id: Long, status: BookingStatus = BookingStatus.PENDING, unread: Boolean = false, expires: String? = null, note: String? = null) = Booking(
        id, status, note, Instant.now().minusSeconds(600).toString(), null, expires, unread,
        com.sareemarket.app.data.BookingItem(10 + id, 5, "Diwali silks", "Soft silk", "Pure silk", "maroon", 4500, ItemStatus.AVAILABLE), shop,
    )

    private var opened = 0L
    private var cancelled = 0L
    private var called: String? = null

    private fun show(state: BookingsState) = compose.setContent {
        SareeTheme { BookingsContent(state, { opened = it }, { called = it }, {}, { cancelled = it }, {}, {}, {}) }
    }

    @Test
    fun eachRequestShowsTheSareeTheShopAndWhereItStands() {
        show(BookingsState(items = listOf(booking(1, note = "after 5"), booking(2, BookingStatus.REJECTED)), loading = false))

        compose.onNodeWithText("My bookings").assertIsDisplayed()
        compose.onNodeWithTag("booking-1").assertIsDisplayed()
        compose.onNodeWithText("Waiting for reply").assertIsDisplayed()
        compose.onNodeWithText("Declined").assertIsDisplayed()
        compose.onNodeWithText("“after 5”").assertIsDisplayed()
        compose.onNodeWithTag("status-1").assertIsDisplayed()
    }

    @Test
    fun anAcceptedHoldSaysHowLongItLastsAndKeepsTheContactButtons() {
        show(BookingsState(items = listOf(booking(1, BookingStatus.ACCEPTED, expires = Instant.now().plusSeconds(3 * 3600 + 60).toString())), loading = false))

        compose.onNodeWithText("On hold · ends in 3 h").assertIsDisplayed()
        compose.onNodeWithTag("call").performClick()

        assertEquals("9876543210", called)
    }

    @Test
    fun onlyOpenRequestsCanBeCancelled() {
        show(BookingsState(items = listOf(booking(1), booking(2, BookingStatus.SOLD)), loading = false))

        compose.onNodeWithTag("cancel-1").performClick()

        assertEquals(1L, cancelled)
        compose.onNodeWithTag("cancel-2").assertDoesNotExist()
        compose.onNodeWithText("Sold to you").assertIsDisplayed()
    }

    @Test
    fun aRequestWithANewAnswerHasADot() {
        show(BookingsState(items = listOf(booking(1, BookingStatus.ACCEPTED, unread = true), booking(2)), loading = false))

        compose.onNodeWithTag("unread-1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("unread-2", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun tappingARequestOpensItsSaree() {
        show(BookingsState(items = listOf(booking(1)), loading = false))

        compose.onNodeWithText("Diwali silks").performClick()

        assertEquals(11L, opened)
    }

    @Test
    fun noRequestsExplainsHowToStart() {
        show(BookingsState(loading = false))

        compose.onNodeWithTag("empty").assertIsDisplayed()
    }

    @Test
    fun aFailedLoadShowsTheMessageAndRetry() {
        var retries = 0
        compose.setContent {
            SareeTheme { BookingsContent(BookingsState(loading = false, error = "Can't reach the server."), {}, {}, {}, {}, {}, { retries++ }, {}) }
        }

        compose.onNodeWithText("Can't reach the server.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }
}
