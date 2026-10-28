package com.sareemarket.app.ui

import com.sareemarket.app.data.BookingRepository
import com.sareemarket.app.data.BookingStatus
import com.sareemarket.app.data.BookingSummary
import com.sareemarket.app.data.FakeBookingApi
import com.sareemarket.app.data.bookingPage
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.networkError
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.seller.RequestFilter
import com.sareemarket.app.ui.seller.RequestsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModelTest {

    private val api = FakeBookingApi()
    private fun vm() = RequestsViewModel(BookingRepository(api, testJson))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun opensOnThePendingRequestsWithTheCounts() {
        api.summary = { BookingSummary(received = 8, pending = 3, onHold = 1, responded = 5) }
        api.onReceived = { _, page -> bookingPage(page, totalPages = 2) }

        val vm = vm()

        assertEquals(RequestFilter.PENDING, vm.state.value.filter)
        assertEquals(listOf(0L, 1L), vm.state.value.items.map { it.id })
        assertTrue(vm.state.value.hasMore)
        assertEquals(3L, vm.state.value.summary.pending)
        assertEquals(5L, vm.state.value.summary.responded)
        assertTrue("received PENDING 0" in api.calls)
    }

    @Test
    fun eachCountTileAsksForItsOwnStatus() {
        val vm = vm()

        vm.selectFilter(RequestFilter.ON_HOLD)
        vm.selectFilter(RequestFilter.SOLD)
        vm.selectFilter(RequestFilter.ALL)

        assertEquals(RequestFilter.ALL, vm.state.value.filter)
        assertEquals(
            listOf("received PENDING 0", "received ACCEPTED 0", "received SOLD 0", "received null 0"),
            api.calls.filter { it.startsWith("received") },
        )
    }

    @Test
    fun choosingTheSameTileAgainDoesNotLoadAgain() {
        val vm = vm()
        val before = api.calls.size

        vm.selectFilter(RequestFilter.PENDING)

        assertEquals(before, api.calls.size)
    }

    @Test
    fun switchingTileClearsTheOldListWhileTheNewOneLoads() {
        api.onReceived = { state, page -> if (state == "SOLD") bookingPage(page, 1, status = BookingStatus.SOLD) else bookingPage(page, 1) }
        val vm = vm()

        vm.selectFilter(RequestFilter.SOLD)

        assertTrue(vm.state.value.items.all { it.status == BookingStatus.SOLD })
    }

    @Test
    fun scrollingLoadsMoreAndStopsAtTheEnd() {
        api.onReceived = { _, page -> bookingPage(page, totalPages = 2) }
        val vm = vm()

        vm.loadMore()
        vm.loadMore()

        assertEquals(4, vm.state.value.items.size)
        assertFalse(vm.state.value.hasMore)
    }

    @Test
    fun answeringCallsTheEndpointThenReloadsTheListAndCounts() {
        val vm = vm()
        val before = api.calls.size

        vm.accept(7)

        val after = api.calls.drop(before)
        assertEquals("accept 7", after.first())
        assertTrue("summary" in after)
        assertTrue(after.any { it.startsWith("received PENDING") })
        assertNull(vm.state.value.busyId)
    }

    @Test
    fun everyAnswerHasItsOwnCall() {
        val vm = vm()

        vm.reject(1)
        vm.extend(2)
        vm.sold(3)

        assertEquals(listOf("reject 1", "extend 2", "sold 3"), api.calls.filter { it.first().isLetter() && it.substringBefore(' ') in setOf("reject", "extend", "sold") })
    }

    @Test
    fun aRefusedAnswerShowsTheServersMessageAndStillReloads() {
        api.onAction = { _, _ -> throw httpError(409, """{"code":"ITEM_NOT_AVAILABLE","message":"This saree is already on hold or sold out"}""") }
        val vm = vm()
        val before = api.calls.count { it.startsWith("received") }

        vm.accept(7)

        assertEquals("This saree is already on hold or sold out", vm.state.value.error)
        assertEquals(before + 1, api.calls.count { it.startsWith("received") })
        assertNull(vm.state.value.busyId)
    }

    @Test
    fun aFailedLoadShowsTheErrorAndReloadRecovers() {
        var fail = true
        api.onReceived = { _, page -> if (fail) throw networkError() else bookingPage(page, 1) }
        val vm = vm()
        assertNotNull(vm.state.value.error)

        fail = false
        vm.reload()

        assertNull(vm.state.value.error)
        assertEquals(2, vm.state.value.items.size)
    }

    @Test
    fun statusLooksAreWrittenFromTheSellersSide() {
        assertEquals("New request", com.sareemarket.app.ui.seller.sellerStatusLook(com.sareemarket.app.data.booking(1)).first)
        assertEquals("Sold", com.sareemarket.app.ui.seller.sellerStatusLook(com.sareemarket.app.data.booking(1, BookingStatus.SOLD)).first)
        assertEquals("Rejected", com.sareemarket.app.ui.seller.sellerStatusLook(com.sareemarket.app.data.booking(1, BookingStatus.REJECTED)).first)
    }
}
