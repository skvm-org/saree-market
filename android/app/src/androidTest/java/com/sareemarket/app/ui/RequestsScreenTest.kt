package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sareemarket.app.data.Booking
import com.sareemarket.app.data.BookingItem
import com.sareemarket.app.data.BookingParty
import com.sareemarket.app.data.BookingStatus
import com.sareemarket.app.data.BookingSummary
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.ui.seller.RequestFilter
import com.sareemarket.app.ui.seller.RequestsContent
import com.sareemarket.app.ui.seller.RequestsState
import com.sareemarket.app.ui.theme.SareeTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RequestsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val priya = BookingParty(3, "Priya", "9000000001", "9000000001")

    private fun booking(
        id: Long,
        status: BookingStatus = BookingStatus.PENDING,
        itemStatus: ItemStatus = ItemStatus.AVAILABLE,
        note: String? = null,
        expires: String? = null,
    ) = Booking(
        id, status, note, Instant.now().minusSeconds(120).toString(), null, expires, false,
        BookingItem(20 + id, 5, "Diwali silks", "Soft silk", "Pure silk", "maroon", 4500, itemStatus), priya,
    )

    private val log = mutableListOf<String>()

    private fun show(state: RequestsState) = compose.setContent {
        SareeTheme {
            RequestsContent(
                state, { log += "collection $it" }, { log += "call $it" }, {}, { log += "filter $it" }, { log += "accept $it" },
                { log += "reject $it" }, { log += "extend $it" }, { log += "sold $it" }, {}, {}, {},
            )
        }
    }

    @Test
    fun theCountsShowHowManyCameAndHowManyWereAnswered() {
        show(RequestsState(summary = BookingSummary(received = 8, pending = 3, onHold = 1, responded = 6, sold = 2), loading = false))

        compose.onNodeWithTag("tile-pending").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("You answered 6 of 8 requests (75%)").assertIsDisplayed()
    }

    @Test
    fun noRateIsShownBeforeTheFirstRequest() {
        show(RequestsState(loading = false))

        compose.onNodeWithTag("response-rate").assertDoesNotExist()
    }

    @Test
    fun aNewRequestShowsTheResellerTheNoteAndTheTwoAnswers() {
        show(RequestsState(items = listOf(booking(1, note = "need by Friday")), loading = false))

        compose.onNodeWithTag("reseller-1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Priya").assertIsDisplayed()
        compose.onNodeWithTag("note-1").assertIsDisplayed()
        compose.onNodeWithText("New request").assertIsDisplayed()
        compose.onNodeWithTag("accept-1").performClick()
        assertEquals(listOf("accept 1"), log)
    }

    @Test
    fun rejectingAsksFirstAndOnlyThenSends() {
        show(RequestsState(items = listOf(booking(1)), loading = false))

        compose.onNodeWithTag("reject-1").performClick()
        assertEquals(emptyList<String>(), log)
        compose.onNodeWithText("Reject this request?").assertIsDisplayed()
        compose.onNodeWithTag("confirm").performClick()

        assertEquals(listOf("reject 1"), log)
    }

    @Test
    fun backingOutOfTheQuestionSendsNothing() {
        show(RequestsState(items = listOf(booking(1)), loading = false))

        compose.onNodeWithTag("reject-1").performClick()
        compose.onNodeWithText("Not now").performClick()

        assertEquals(emptyList<String>(), log)
        compose.onNodeWithText("Reject this request?").assertDoesNotExist()
    }

    @Test
    fun aHeldRequestCanBeSoldExtendedOrReleased() {
        show(RequestsState(items = listOf(booking(1, BookingStatus.ACCEPTED, ItemStatus.ON_HOLD, expires = Instant.now().plusSeconds(2 * 3600 + 60).toString())), loading = false))

        compose.onNodeWithText("On hold · ends in 2 h").assertIsDisplayed()
        compose.onNodeWithTag("extend-1").performClick()
        compose.onNodeWithTag("sold-1").performClick()
        compose.onNodeWithText("Mark as sold?").assertIsDisplayed()
        compose.onNodeWithTag("confirm").performClick()
        compose.onNodeWithTag("release-1").performClick()
        compose.onNodeWithTag("confirm").performClick()

        assertEquals(listOf("extend 1", "sold 1", "reject 1"), log)
    }

    @Test
    fun aRequestBehindAHeldSareeCannotBeAcceptedYet() {
        show(RequestsState(items = listOf(booking(1, itemStatus = ItemStatus.ON_HOLD)), loading = false))

        compose.onNodeWithText("Another request has this saree on hold.").assertIsDisplayed()
        compose.onNodeWithTag("accept-1").assertIsNotEnabled()
        compose.onNodeWithTag("reject-1").assertIsEnabled()
        compose.onNodeWithTag("sold-1").assertDoesNotExist() // cannot sell a saree someone else holds
    }

    @Test
    fun whileAnAnswerIsOnItsWayItsButtonsAreOff() {
        show(RequestsState(items = listOf(booking(1)), busyId = 1, loading = false))

        compose.onNodeWithTag("accept-1").assertIsNotEnabled()
        compose.onNodeWithTag("reject-1").assertIsNotEnabled()
    }

    @Test
    fun finishedRequestsHaveNoButtons() {
        show(RequestsState(filter = RequestFilter.ALL, items = listOf(booking(1, BookingStatus.SOLD), booking(2, BookingStatus.REJECTED), booking(3, BookingStatus.CANCELLED)), loading = false))

        compose.onNodeWithTag("status-1").assertIsDisplayed() // "Sold" is also the name of a count tile
        compose.onNodeWithText("Rejected").assertIsDisplayed()
        compose.onNodeWithText("Cancelled by reseller").assertIsDisplayed()
        compose.onNodeWithTag("accept-1").assertDoesNotExist()
        compose.onNodeWithTag("call").assertDoesNotExist()
    }

    @Test
    fun eachCountIsAButtonThatReportsItsFilter() {
        show(RequestsState(summary = BookingSummary(received = 9, pending = 2, onHold = 3, sold = 1), loading = false))

        compose.onNodeWithTag("tile-pending").performClick()
        compose.onNodeWithTag("tile-on-hold").performClick()
        compose.onNodeWithTag("tile-sold").performClick()
        compose.onNodeWithTag("tile-received").performClick()

        assertEquals(listOf("filter PENDING", "filter ON_HOLD", "filter SOLD", "filter ALL"), log)
    }

    @Test
    fun theChosenCountIsMarkedSelected() {
        show(RequestsState(filter = RequestFilter.ON_HOLD, summary = BookingSummary(onHold = 3), loading = false))

        compose.onNodeWithTag("tile-on-hold").assertIsSelected()
        compose.onNodeWithTag("tile-pending").assertIsNotSelected()
        compose.onNodeWithTag("tile-received").assertIsNotSelected()
    }

    @Test
    fun theCountsAreBigEnoughToTap() {
        show(RequestsState(loading = false))

        compose.onNodeWithTag("tile-pending").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun everyFilterHasItsOwnEmptyMessage() {
        show(RequestsState(filter = RequestFilter.PENDING, loading = false))
        compose.onNodeWithText("No requests waiting.", substring = true).assertIsDisplayed()
    }

    @Test
    fun nothingOnHoldSaysSo() {
        show(RequestsState(filter = RequestFilter.ON_HOLD, loading = false))
        compose.onNodeWithText("Nothing on hold.").assertIsDisplayed()
    }

    @Test
    fun noRequestsAtAllSaysSo() {
        show(RequestsState(filter = RequestFilter.ALL, loading = false))
        compose.onNodeWithText("No requests yet.").assertIsDisplayed()
    }

    @Test
    fun aRefusalFromTheServerIsShownUnderTheList() {
        show(RequestsState(items = listOf(booking(1)), error = "This saree is already on hold or sold out", loading = false))

        compose.onNodeWithTag("error").assertIsDisplayed()
    }

    @Test
    fun callingTheResellerPassesTheirNumber() {
        show(RequestsState(items = listOf(booking(1)), loading = false))

        compose.onNodeWithTag("call").performClick()

        assertEquals(listOf("call 9000000001"), log)
        assertNull(log.firstOrNull { it.startsWith("accept") })
    }
}
