package com.sareemarket.app.ui

import androidx.lifecycle.SavedStateHandle
import com.sareemarket.app.data.CatalogRepository
import com.sareemarket.app.data.FakeCatalogApi
import com.sareemarket.app.data.FakeCollectionsApi
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.reseller.ItemDetailViewModel
import com.sareemarket.app.ui.reseller.ResellerCollectionViewModel
import com.sareemarket.app.ui.reseller.SellerPageViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelsTest {

    private val catalogApi = FakeCatalogApi()
    private val collectionsApi = FakeCollectionsApi()
    private val repo by lazy { CatalogRepository(catalogApi, collectionsApi, testJson) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun handle(id: Long) = SavedStateHandle(mapOf("id" to id))

    @Test
    fun itemDetailLoadsTheItemNamedInTheRoute() {
        val vm = ItemDetailViewModel(handle(10), repo)

        assertEquals(10L, vm.state.value.value?.id)
        assertEquals("9876543210", vm.state.value.value?.seller?.contactPhone)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun missingItemShowsTheServerMessageAndReloadRecovers() {
        var found = false
        catalogApi.onItem = { id ->
            if (!found) throw httpError(404, """{"code":"NOT_FOUND","message":"Photo not found"}""")
            com.sareemarket.app.data.itemDetail(id)
        }
        val vm = ItemDetailViewModel(handle(10), repo)
        assertEquals("Photo not found", vm.state.value.error)
        assertNull(vm.state.value.value)

        found = true
        vm.reload()
        assertEquals(10L, vm.state.value.value?.id)
        assertNull(vm.state.value.error)
    }

    @Test
    fun sellerPageLoadsShopAndCollections() {
        val vm = SellerPageViewModel(handle(1), repo)

        assertEquals("Lakshmi Silks", vm.state.value.value?.shopName)
        assertEquals(1, vm.state.value.value?.collections?.size)
    }

    @Test
    fun resellerCollectionPageLoadsTheCollection() {
        val vm = ResellerCollectionViewModel(handle(5), repo)

        assertEquals("Diwali silks", vm.state.value.value?.title)
        assertEquals(2, vm.state.value.value?.items?.size)
    }
}
