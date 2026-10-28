package com.sareemarket.app.ui

import androidx.lifecycle.SavedStateHandle
import com.sareemarket.app.data.BookingRepository
import com.sareemarket.app.data.BookingStatus
import com.sareemarket.app.data.CatalogRepository
import com.sareemarket.app.data.FakeBookingApi
import com.sareemarket.app.data.MyBooking
import com.sareemarket.app.data.FakeCatalogApi
import com.sareemarket.app.data.FakeCollectionsApi
import com.sareemarket.app.data.detail
import com.sareemarket.app.data.fakePhotos
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.item
import com.sareemarket.app.data.itemDetail
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.reseller.ItemPagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemPagerViewModelTest {

    private val catalog = FakeCatalogApi()
    private val collections = FakeCollectionsApi()
    private val bookingApi = FakeBookingApi()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(openedId: Long) =
        ItemPagerViewModel(
            SavedStateHandle(mapOf("id" to openedId)), CatalogRepository(catalog, collections, fakePhotos(), testJson),
            BookingRepository(bookingApi, testJson),
        )

    @Test
    fun theOpenedPhotoIsShownFirstAndItsCollectionSuppliesTheNeighbours() {
        collections.get = { detail(5, listOf(item(10), item(11), item(12))) }

        val vm = vm(11)

        assertEquals(listOf(10L, 11L, 12L), vm.state.value.ids)
        assertEquals(1, vm.state.value.startIndex)
        assertNotNull(vm.state.value.details[11]?.value)
    }

    @Test
    fun otherPhotosLoadOnlyWhenAskedFor() {
        collections.get = { detail(5, listOf(item(10), item(11), item(12))) }
        val vm = vm(10)

        assertNull(vm.state.value.details[12])
        vm.ensure(12)

        assertNotNull(vm.state.value.details[12]?.value)
    }

    @Test
    fun askingTwiceForTheSamePhotoLoadsItOnce() {
        collections.get = { detail(5, listOf(item(10), item(11))) }
        var calls = 0
        catalog.onItem = { calls++; itemDetail(it) }
        val vm = vm(10)

        vm.ensure(11)
        vm.ensure(11)

        assertEquals(2, calls) // the opened photo once, the neighbour once
    }

    @Test
    fun ifTheCollectionCannotBeLoadedTheOpenedPhotoStillShows() {
        collections.get = { throw httpError(500) }

        val vm = vm(10)

        assertEquals(listOf(10L), vm.state.value.ids)
        assertNotNull(vm.state.value.details[10]?.value)
    }

    @Test
    fun aFailedPhotoShowsItsErrorAndRetryLoadsItAgain() {
        var fail = true
        catalog.onItem = { if (fail) throw httpError(404, """{"code":"NOT_FOUND","message":"Saree not found"}""") else itemDetail(it) }
        collections.get = { detail(5, listOf(item(10))) }
        val vm = vm(10)
        assertEquals("Saree not found", vm.state.value.details[10]?.error)

        fail = false
        vm.retry(10)

        assertNotNull(vm.state.value.details[10]?.value)
    }

    @Test
    fun bookingSendsTheNoteThenReloadsThePageSoItShowsTheRequest() {
        collections.get = { detail(5, listOf(item(10))) }
        var booked = false
        catalog.onItem = { itemDetail(it).copy(myBooking = if (booked) MyBooking(9, BookingStatus.PENDING) else null) }
        bookingApi.onBook = { id, note -> booked = true; com.sareemarket.app.data.booking(9, itemId = id, note = note) }
        val vm = vm(10)
        assertNull(vm.state.value.details[10]?.value?.myBooking)

        vm.book(10, "  after 5 pm  ")

        assertTrue("book 10 after 5 pm" in bookingApi.calls)
        assertEquals(BookingStatus.PENDING, vm.state.value.details[10]?.value?.myBooking?.status)
        assertFalse(vm.state.value.bookingBusy)
        assertNull(vm.state.value.bookingError)
    }

    @Test
    fun aBlankNoteIsSentAsNoNote() {
        collections.get = { detail(5, listOf(item(10))) }
        val vm = vm(10)

        vm.book(10, "   ")

        assertTrue("book 10 null" in bookingApi.calls)
    }

    @Test
    fun aRefusedRequestShowsTheMessageAndStillReloadsThePage() {
        collections.get = { detail(5, listOf(item(10))) }
        var loads = 0
        catalog.onItem = { loads++; itemDetail(it) }
        bookingApi.onBook = { _, _ -> throw httpError(409, """{"code":"NOT_BOOKABLE","message":"This saree is sold out"}""") }
        val vm = vm(10)
        val before = loads

        vm.book(10, "")

        assertEquals("This saree is sold out", vm.state.value.bookingError)
        assertEquals(before + 1, loads) // the saree may have just sold out
        assertFalse(vm.state.value.bookingBusy)
    }

    @Test
    fun cancellingCallsTheCancelEndpointForThatRequest() {
        collections.get = { detail(5, listOf(item(10))) }
        val vm = vm(10)

        vm.cancelBooking(10, 9)

        assertTrue("cancel 9" in bookingApi.calls)
        assertNull(vm.state.value.bookingError)
    }
}
