package com.sareemarket.app.ui

import com.sareemarket.app.data.BookingRepository
import com.sareemarket.app.data.BookingStatus
import com.sareemarket.app.data.FakeBookingApi
import com.sareemarket.app.data.booking
import com.sareemarket.app.data.bookingPage
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.networkError
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.reseller.BookingsViewModel
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
class BookingsViewModelTest {

    private val api = FakeBookingApi()
    private fun vm() = BookingsViewModel(BookingRepository(api, testJson))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun opensWithTheFirstPageOfTheResellersRequests() {
        api.onMine = { _, page -> bookingPage(page, totalPages = 3) }

        val vm = vm()

        assertEquals(listOf(0L, 1L), vm.state.value.items.map { it.id })
        assertTrue(vm.state.value.hasMore)
        assertFalse(vm.state.value.loading)
        assertEquals("mine null 0", api.calls.first())
    }

    @Test
    fun scrollingLoadsTheNextPageAndStopsAtTheEnd() {
        api.onMine = { _, page -> bookingPage(page, totalPages = 2) }
        val vm = vm()

        vm.loadMore()
        vm.loadMore() // nothing left

        assertEquals(listOf(0L, 1L, 100L, 101L), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.hasMore)
        assertEquals(2, api.calls.count { it.startsWith("mine") })
    }

    @Test
    fun answersAreMarkedSeenWhenTheListIsOnScreen() {
        api.onMine = { _, _ -> bookingPage(0, 1).copy(items = listOf(booking(1, BookingStatus.ACCEPTED, unread = true))) }

        val vm = vm()

        assertTrue("seen" in api.calls)
        assertTrue(vm.state.value.items.single().unread) // the dot stays until the next visit
    }

    @Test
    fun nothingIsMarkedSeenWhenThereIsNothingNew() {
        vm()

        assertFalse("seen" in api.calls)
    }

    @Test
    fun cancellingReplacesTheRowWithTheAnswer() {
        api.onMine = { _, _ -> bookingPage(0, 1) }
        val vm = vm()

        vm.cancel(0)

        assertEquals(BookingStatus.CANCELLED, vm.state.value.items.first { it.id == 0L }.status)
        assertNull(vm.state.value.busyId)
        assertTrue("cancel 0" in api.calls)
    }

    @Test
    fun aFailedCancelShowsTheMessageAndKeepsTheRow() {
        api.onMine = { _, _ -> bookingPage(0, 1) }
        api.onAction = { _, _ -> throw httpError(409, """{"code":"INVALID_STATE","message":"This request can no longer be cancelled"}""") }
        val vm = vm()

        vm.cancel(0)

        assertEquals("This request can no longer be cancelled", vm.state.value.error)
        assertEquals(BookingStatus.PENDING, vm.state.value.items.first { it.id == 0L }.status)
        assertNull(vm.state.value.busyId)
    }

    @Test
    fun aFailedFirstLoadShowsTheErrorAndReloadRecovers() {
        var fail = true
        api.onMine = { _, page -> if (fail) throw networkError() else bookingPage(page, 1) }
        val vm = vm()
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.items.isEmpty())

        fail = false
        vm.reload()

        assertNull(vm.state.value.error)
        assertEquals(2, vm.state.value.items.size)
    }

    @Test
    fun noRequestsIsAnEmptyListNotAnError() {
        api.onMine = { _, page -> bookingPage(page, totalPages = 0, perPage = 0) }

        val vm = vm()

        assertTrue(vm.state.value.items.isEmpty())
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }
}
