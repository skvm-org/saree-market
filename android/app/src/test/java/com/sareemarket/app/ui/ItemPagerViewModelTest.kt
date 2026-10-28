package com.sareemarket.app.ui

import androidx.lifecycle.SavedStateHandle
import com.sareemarket.app.data.CartRepository
import com.sareemarket.app.data.CatalogRepository
import com.sareemarket.app.data.FakeShoppingApi
import com.sareemarket.app.data.WishlistRepository
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
    private val shopping = FakeShoppingApi()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(openedId: Long) =
        ItemPagerViewModel(
            SavedStateHandle(mapOf("id" to openedId)), CatalogRepository(catalog, collections, fakePhotos(), testJson),
            CartRepository(shopping, testJson), WishlistRepository(shopping, testJson),
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
    fun addingToTheCartCallsTheRepositoryThenReloadsThePageSoItShowsTheCartQuantity() {
        collections.get = { detail(5, listOf(item(10))) }
        var inCart = 0
        catalog.onItem = { itemDetail(it).copy(inCart = inCart) }
        shopping.onSetLine = { _, q -> inCart = q; retrofit2.Response.success(Unit) }
        val vm = vm(10)
        assertEquals(0, vm.state.value.details[10]?.value?.inCart)

        vm.setCartQuantity(10, 2)

        assertTrue("setLine 10 2" in shopping.calls)
        assertEquals(2, vm.state.value.details[10]?.value?.inCart)
        assertFalse(vm.state.value.shoppingBusy)
        assertNull(vm.state.value.shoppingError)
    }

    @Test
    fun aRefusedAddShowsTheServersMessageAndStillReloadsThePage() {
        collections.get = { detail(5, listOf(item(10))) }
        var loads = 0
        catalog.onItem = { loads++; itemDetail(it) }
        shopping.onSetLine = { _, _ -> throw httpError(409, """{"code":"NOT_ENOUGH_STOCK","message":"Only 1 left"}""") }
        val vm = vm(10)
        val before = loads

        vm.setCartQuantity(10, 4)

        assertEquals("Only 1 left", vm.state.value.shoppingError)
        assertEquals(before + 1, loads) // the stock may just have changed
        assertFalse(vm.state.value.shoppingBusy)
    }

    @Test
    fun removingFromTheCartCallsTheRepositoryAndReloads() {
        collections.get = { detail(5, listOf(item(10))) }
        val vm = vm(10)

        vm.removeFromCart(10)

        assertTrue("removeLine 10" in shopping.calls)
        assertNull(vm.state.value.shoppingError)
    }

    @Test
    fun wishlistToggleAddsWhenNotSavedAndRemovesWhenSaved() {
        collections.get = { detail(5, listOf(item(10))) }
        var saved = false
        catalog.onItem = { itemDetail(it).copy(inWishlist = saved) }
        val vm = vm(10)

        saved = true
        vm.toggleWishlist(10)
        assertTrue("wish+ 10" in shopping.calls)

        vm.toggleWishlist(10) // the reload showed it saved, so this removes it
        assertTrue("wish- 10" in shopping.calls)
    }
}
