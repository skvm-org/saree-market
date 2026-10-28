package com.sareemarket.app.ui

import com.sareemarket.app.data.FakeOrderApi
import com.sareemarket.app.data.FakePhotoSource
import com.sareemarket.app.data.OrderPage
import com.sareemarket.app.data.OrderRepository
import com.sareemarket.app.data.hold
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.order
import com.sareemarket.app.data.orderPage
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.reseller.OrdersViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {

    private val api = FakeOrderApi()
    private fun vm() = OrdersViewModel(OrderRepository(api, FakePhotoSource(), testJson))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listsActiveHoldsAndOrders() {
        api.holds = { listOf(hold(1), hold(2)) }
        api.onMine = { _, page -> orderPage(page, totalPages = 1) }

        val vm = vm()

        assertEquals(listOf(1L, 2L), vm.state.value.holds.map { it.id })
        assertEquals(listOf(0L, 1L), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun opensAndMarksSeenOnlyWhenSomethingIsUnread() {
        api.onMine = { _, page -> orderPage(page, totalPages = 1) }
        vm()
        assertTrue("seen" !in api.calls)

        api.calls.clear()
        api.onMine = { _, _ -> OrderPage(listOf(order(1, unread = true)), 0, 20, 1, 1) }
        vm()

        assertTrue("seen" in api.calls)
    }

    @Test
    fun theBadgeIsClearedEvenWhenTheUnreadOrderIsOnALaterPage() = kotlinx.coroutines.test.runTest {
        api.summary = { com.sareemarket.app.data.OrderSummary(unread = 1) }
        api.onMine = { _, page -> orderPage(page, totalPages = 2) } // no unread row on the first page
        val repo = OrderRepository(api, FakePhotoSource(), testJson)
        repo.refreshSummary()

        OrdersViewModel(repo)

        assertTrue("seen" in api.calls)
    }

    @Test
    fun scrollingLoadsTheNextPageAndStopsAtTheEnd() {
        api.onMine = { _, page -> orderPage(page, totalPages = 2) }
        val vm = vm()

        vm.loadMore()
        vm.loadMore() // nothing left

        assertEquals(listOf(0L, 1L, 100L, 101L), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.hasMore)
        assertEquals(2, api.calls.count { it.startsWith("mine") })
    }

    @Test
    fun aFailureShowsTheMessage() {
        api.onMine = { _, _ -> throw httpError(500) }

        val vm = vm()

        assertTrue(vm.state.value.error != null)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun noOrdersAndNoHoldsIsNotAnError() {
        api.holds = { emptyList() }
        api.onMine = { _, _ -> OrderPage(emptyList(), 0, 20, 0, 0) }

        val vm = vm()

        assertTrue(vm.state.value.items.isEmpty())
        assertEquals(null, vm.state.value.error)
    }
}
