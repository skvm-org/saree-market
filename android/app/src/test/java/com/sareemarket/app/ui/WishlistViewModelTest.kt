package com.sareemarket.app.ui

import com.sareemarket.app.data.FakeShoppingApi
import com.sareemarket.app.data.WishlistRepository
import com.sareemarket.app.data.page
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.reseller.WishlistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WishlistViewModelTest {

    private val api = FakeShoppingApi()
    private fun vm() = WishlistViewModel(WishlistRepository(api, testJson))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun opensWithTheFirstPage() {
        api.onWishlist = { page(it, totalPages = 3) }

        val vm = vm()

        assertEquals(listOf(0L, 1L), vm.state.value.items.map { it.id })
        assertTrue(vm.state.value.hasMore)
        assertFalse(vm.state.value.loading)
        assertEquals("wishlist 0", api.calls.first())
    }

    @Test
    fun scrollingLoadsTheNextPageAndStopsAtTheEnd() {
        api.onWishlist = { page(it, totalPages = 2) }
        val vm = vm()

        vm.loadMore()
        vm.loadMore() // nothing left

        assertEquals(listOf(0L, 1L, 100L, 101L), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.hasMore)
        assertEquals(2, api.calls.count { it.startsWith("wishlist") })
    }

    @Test
    fun aRepeatedItemOnTheNextPageIsNotListedTwice() {
        api.onWishlist = { com.sareemarket.app.data.CatalogPage(page(0, 1).items, it, 2, 4, 2) }
        val vm = vm()

        vm.loadMore()

        assertEquals(listOf(0L, 1L), vm.state.value.items.map { it.id })
    }

    @Test
    fun removingDropsTheRow() {
        api.onWishlist = { page(it, totalPages = 1) }
        val vm = vm()

        vm.remove(0)

        assertTrue("wish- 0" in api.calls)
        assertEquals(listOf(1L), vm.state.value.items.map { it.id })
        assertNull(vm.state.value.busyId)
    }

    @Test
    fun aFailedRemoveKeepsTheRowAndShowsTheError() {
        api.onWishlist = { page(it, totalPages = 1) }
        val vm = vm()
        api.failWishlistRemove = com.sareemarket.app.data.httpError(500)

        vm.remove(0)

        assertEquals(listOf(0L, 1L), vm.state.value.items.map { it.id })
        assertTrue(vm.state.value.error != null)
    }

    @Test
    fun anEmptyWishlistIsNotAnError() {
        api.onWishlist = { com.sareemarket.app.data.CatalogPage(emptyList(), 0, 20, 0, 0) }

        val vm = vm()

        assertTrue(vm.state.value.items.isEmpty())
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }
}
