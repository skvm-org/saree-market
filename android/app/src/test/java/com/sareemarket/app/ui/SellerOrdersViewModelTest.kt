package com.sareemarket.app.ui

import com.sareemarket.app.data.FakeOrderApi
import com.sareemarket.app.data.FakePhotoSource
import com.sareemarket.app.data.OrderPage
import com.sareemarket.app.data.OrderRepository
import com.sareemarket.app.data.OrderSummary
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.order
import com.sareemarket.app.data.orderPage
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.seller.SellerOrderFilter
import com.sareemarket.app.ui.seller.SellerOrdersViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
class SellerOrdersViewModelTest {

    private val api = FakeOrderApi()
    private fun vm() = SellerOrdersViewModel(OrderRepository(api, FakePhotoSource(), testJson))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun opensOnToConfirmAndLoadsThatState() {
        val vm = vm()

        assertEquals(SellerOrderFilter.TO_CONFIRM, vm.state.value.filter)
        assertTrue("received PAYMENT_SUBMITTED 0" in api.calls)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun selectingATileLoadsItsStateAndDropsTheOldRows() {
        val vm = vm()

        vm.selectFilter(SellerOrderFilter.TO_PACK)

        assertTrue("received CONFIRMED 0" in api.calls)
        assertEquals(SellerOrderFilter.TO_PACK, vm.state.value.filter)
    }

    @Test
    fun allLoadsEveryState() {
        val vm = vm()

        vm.selectFilter(SellerOrderFilter.ALL)

        assertTrue("received null 0" in api.calls)
    }

    @Test
    fun selectingTheSameTileAgainDoesNotReload() {
        val vm = vm()
        val before = api.calls.count { it.startsWith("received") }

        vm.selectFilter(SellerOrderFilter.TO_CONFIRM)

        assertEquals(before, api.calls.count { it.startsWith("received") })
    }

    @Test
    fun confirmingCallsTheServerThenReloadsTheListAndCounts() {
        val vm = vm()
        api.calls.clear()

        vm.confirmPayment(5)

        assertEquals("confirm 5", api.calls.first())
        assertTrue(api.calls.any { it.startsWith("received") })
        assertTrue("summary" in api.calls)
        assertNull(vm.state.value.busyId)
    }

    @Test
    fun aSecondActionWhileOneIsRunningIsIgnored() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        api.calls.clear()

        vm.confirmPayment(5)
        vm.confirmPayment(5)
        vm.pack(6)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, api.calls.count { it.startsWith("confirm") || it.startsWith("pack") })
    }

    @Test
    fun aBadStateAnswerShowsTheServersMessageAndStillReloads() {
        val vm = vm()
        api.calls.clear()
        api.onAction = { _, _ -> throw httpError(409, """{"code":"BAD_STATE","message":"This can't be done to an order that is CANCELLED"}""") }

        vm.confirmPayment(5)

        assertEquals("This can't be done to an order that is CANCELLED", vm.state.value.error)
        assertTrue(api.calls.any { it.startsWith("received") }) // the order may have moved on
        assertNull(vm.state.value.busyId)
    }

    @Test
    fun rejectingNeedsAReason() {
        val vm = vm()
        api.calls.clear()

        vm.rejectPayment(5, "   ")

        assertTrue(api.calls.none { it.startsWith("reject") })
        assertNotNull(vm.state.value.error)
        vm.rejectPayment(5, " Not received ")
        assertTrue("reject 5 Not received" in api.calls)
    }

    @Test
    fun cancellingNeedsAReason() {
        val vm = vm()
        api.calls.clear()

        vm.cancel(5, "")
        assertTrue(api.calls.none { it.startsWith("cancel") })

        vm.cancel(5, "Sold in the shop")
        assertTrue("cancel-reason 5 Sold in the shop" in api.calls)
    }

    @Test
    fun shippingSendsTheOptionalFieldsTrimmed() {
        val vm = vm()
        api.calls.clear()

        vm.ship(5, " DTDC ", "")

        assertTrue("ship 5 DTDC null" in api.calls)
    }

    @Test
    fun packAndDeliverCallTheirRoutes() {
        val vm = vm()
        api.calls.clear()

        vm.pack(5)
        vm.deliver(5)

        assertTrue("pack 5" in api.calls)
        assertTrue("deliver 5" in api.calls)
    }

    @Test
    fun opensAndMarksSeenWhenSomethingIsUnread() {
        api.onMine = { _, _ -> OrderPage(listOf(order(1, unread = true)), 0, 20, 1, 1) }

        vm()

        assertTrue("seen" in api.calls)
    }

    @Test
    fun doesNotMarkSeenWhenNothingIsUnread() {
        api.onMine = { _, page -> orderPage(page, totalPages = 1) }

        vm()

        assertTrue("seen" !in api.calls)
    }

    @Test
    fun scrollingLoadsTheNextPageAndStopsAtTheEnd() {
        api.onMine = { _, page -> orderPage(page, totalPages = 2) }
        val vm = vm()

        vm.loadMore()
        vm.loadMore()

        assertEquals(listOf(0L, 1L, 100L, 101L), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.hasMore)
    }

    @Test
    fun theCountsComeFromTheServersSummary() {
        api.summary = { OrderSummary(toConfirm = 3, toPack = 2, toShip = 1, shipped = 4) }

        val vm = vm()

        assertEquals(3, vm.state.value.summary.toConfirm)
        assertEquals(4, vm.state.value.summary.shipped)
    }

    @Test
    fun aFailureShowsTheMessage() {
        api.onMine = { _, _ -> throw httpError(500) }

        val vm = vm()

        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }
}
