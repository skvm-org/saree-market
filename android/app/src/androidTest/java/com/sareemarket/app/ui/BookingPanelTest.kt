package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.sareemarket.app.data.BookingStatus
import com.sareemarket.app.data.ItemDetail
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.data.MyBooking
import com.sareemarket.app.data.SellerContact
import com.sareemarket.app.ui.reseller.BookingUi
import com.sareemarket.app.ui.reseller.ItemDetailContent
import com.sareemarket.app.ui.theme.SareeTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The Book button, the note dialog and the reseller's own request on the saree page. */
class BookingPanelTest {

    @get:Rule
    val compose = createComposeRule()

    private val seller = SellerContact(7, "Lakshmi Silks", "Chinnalapatti", "9876543210", "9876543211")
    private val detail = ItemDetail(10, 5, "Diwali silks", null, "Soft silk", "Pure silk", "maroon", null, 4000, ItemStatus.AVAILABLE, seller)

    private var booked: String? = null
    private var cancelled: Long? = null

    private fun show(d: ItemDetail = detail, ui: BookingUi = BookingUi()) = compose.setContent {
        SareeTheme {
            ItemDetailContent(
                d, {}, {}, {}, {}, {}, null, null,
                booking = ui, onBook = { booked = it }, onCancelBooking = { cancelled = it },
            ) {}
        }
    }

    @Test
    fun anAvailableSareeCanBeBookedWithANote() {
        show()

        compose.onNodeWithTag("book").assertIsDisplayed()
        compose.onNodeWithText("Book this saree").performClick()
        compose.onNodeWithTag("booking-note").performTextInput("after 5 pm")
        compose.onNodeWithTag("send-booking").performClick()

        assertEquals("after 5 pm", booked)
        compose.onNodeWithTag("booking-note").assertDoesNotExist() // the dialog closed
    }

    @Test
    fun theNoteIsOptional() {
        show()

        compose.onNodeWithTag("book").performClick()
        compose.onNodeWithTag("send-booking").performClick()

        assertEquals("", booked)
    }

    @Test
    fun notNowClosesTheDialogWithoutSending() {
        show()

        compose.onNodeWithTag("book").performClick()
        compose.onNodeWithText("Not now").performClick()

        assertNull(booked)
        compose.onNodeWithTag("booking-note").assertDoesNotExist()
    }

    @Test
    fun aSoldOutSareeCannotBeBooked() {
        show(detail.copy(status = ItemStatus.SOLD_OUT))

        compose.onNodeWithTag("book").assertDoesNotExist()
    }

    @Test
    fun aSareeOnHoldCanBeQueuedFor() {
        show(detail.copy(status = ItemStatus.ON_HOLD))

        compose.onNodeWithText("Ask to be next").assertIsDisplayed()
        compose.onNodeWithTag("status").assertIsDisplayed()
        compose.onNodeWithText("On hold").assertIsDisplayed()
    }

    @Test
    fun aPendingRequestShowsItsStateAndCanBeCancelled() {
        show(detail.copy(myBooking = MyBooking(9, BookingStatus.PENDING)))

        compose.onNodeWithText("Request sent. Lakshmi Silks will reply soon.").assertIsDisplayed()
        compose.onNodeWithTag("book").assertDoesNotExist() // one open request per saree
        compose.onNodeWithTag("cancel-booking").performClick()

        assertEquals(9L, cancelled)
    }

    @Test
    fun anAcceptedHoldShowsHowLongItHasLeft() {
        show(detail.copy(status = ItemStatus.ON_HOLD, myBooking = MyBooking(9, BookingStatus.ACCEPTED, Instant.now().plusSeconds(5 * 3600 + 60).toString())))

        compose.onNodeWithText("On hold for you, ends in 5 h.").assertIsDisplayed()
        compose.onNodeWithTag("cancel-booking").assertIsDisplayed()
    }

    @Test
    fun aFinishedRequestIsAOneLineNoteAndTheSareeCanBeAskedForAgain() {
        show(detail.copy(myBooking = MyBooking(9, BookingStatus.REJECTED)))

        compose.onNodeWithText("Lakshmi Silks could not hold this one.").assertIsDisplayed()
        compose.onNodeWithTag("cancel-booking").assertDoesNotExist()
        compose.onNodeWithTag("book").assertIsDisplayed()
    }

    @Test
    fun aSoldRequestSaysSoAndOffersNothingMore() {
        show(detail.copy(status = ItemStatus.SOLD_OUT, myBooking = MyBooking(9, BookingStatus.SOLD)))

        compose.onNodeWithText("Sold to you.").assertIsDisplayed()
        compose.onNodeWithTag("book").assertDoesNotExist()
    }

    @Test
    fun aRefusalFromTheServerIsShown() {
        show(ui = BookingUi(error = "You have 10 open booking requests; cancel one before asking for another"))

        compose.onNodeWithTag("booking-error").assertIsDisplayed()
    }

    @Test
    fun callAndWhatsAppStayWithinReach() {
        show(detail.copy(myBooking = MyBooking(9, BookingStatus.PENDING)))

        compose.onNodeWithTag("call").assertIsDisplayed()
        compose.onNodeWithTag("whatsapp").assertIsDisplayed()
    }
}
