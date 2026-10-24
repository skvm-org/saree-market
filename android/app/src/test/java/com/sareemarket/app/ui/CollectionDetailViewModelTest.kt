package com.sareemarket.app.ui

import androidx.lifecycle.SavedStateHandle
import com.sareemarket.app.data.CollectionRepository
import com.sareemarket.app.data.FakeCollectionsApi
import com.sareemarket.app.data.FakePhotoSource
import com.sareemarket.app.data.detail
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.item
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.seller.CollectionDetailViewModel
import com.sareemarket.app.ui.seller.CollectionFields
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
class CollectionDetailViewModelTest {

    private val api = FakeCollectionsApi()
    private val photos = mapOf("p1" to ByteArray(10), "p2" to ByteArray(10))
    private val vm by lazy {
        CollectionDetailViewModel(SavedStateHandle(mapOf("id" to 5L)), CollectionRepository(api, FakePhotoSource(photos), testJson))
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsTheCollectionAndReferenceData() {
        assertEquals(detail(), vm.state.value.detail)
        assertNotNull(vm.state.value.reference)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun loadFailureIsShown() {
        api.get = { throw httpError(404, """{"code":"NOT_FOUND","message":"Collection not found"}""") }

        assertEquals("Collection not found", vm.state.value.error)
        assertNull(vm.state.value.detail)
    }

    @Test
    fun savingItemEditsReplacesThatItemOnly() {
        api.onUpdateItem = { id, _ -> item(id, colour = "maroon", override = 1800) }
        var saved = false

        vm.saveItem(10, "maroon", "1800") { saved = true }

        val items = vm.state.value.detail!!.items
        assertTrue(saved)
        assertEquals("maroon", items.first { it.id == 10L }.colour)
        assertNull(items.first { it.id == 11L }.colour)
    }

    @Test
    fun zeroPriceIsRejectedOnTheDeviceAndEmptyMeansNoOverride() {
        var saved = false
        vm.saveItem(10, "red", "0") { saved = true }
        assertFalse(saved)
        assertNotNull(vm.state.value.fieldErrors["priceOverride"])
        assertFalse("updateItem" in api.calls)

        vm.saveItem(10, "red", "") { saved = true }
        assertTrue(saved)
    }

    @Test
    fun deletingAnItemRemovesItFromTheGrid() {
        vm.deleteItem(10) {}

        assertEquals(listOf(11L), vm.state.value.detail!!.items.map { it.id })
    }

    @Test
    fun failedItemDeleteKeepsTheItemAndShowsWhy() {
        api.onDeleteItem = { retrofit2.Response.error(403, okhttp3.ResponseBody.create(null, """{"code":"FORBIDDEN","message":"Not yours"}""")) }

        vm.deleteItem(10) {}

        assertEquals(2, vm.state.value.detail!!.items.size)
        assertEquals("Not yours", vm.state.value.error)
    }

    @Test
    fun savingCollectionDetailsUpdatesTheScreen() {
        api.onUpdate = { detail().copy(title = "Pongal cottons") }
        var saved = false

        vm.saveCollection(CollectionFields("Pongal cottons", 1, 1, "900", "")) { saved = true }

        assertTrue(saved)
        assertEquals("Pongal cottons", vm.state.value.detail!!.title)
    }

    @Test
    fun invalidCollectionEditsAreCaughtBeforeAnyRequest() {
        vm.saveCollection(CollectionFields("", null, null, "", "")) {}

        assertEquals(setOf("title", "sareeTypeId", "fabricId", "price"), vm.state.value.fieldErrors.keys)
        assertFalse("update" in api.calls)
    }

    @Test
    fun addingPhotosShowsTheNewItems() {
        api.onAddPhotos = { detail(items = listOf(item(10), item(11), item(12))) }

        vm.addPhotos(listOf("p1", "p2"))

        assertEquals(3, vm.state.value.detail!!.items.size)
        assertNull(vm.state.value.progress)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun deletingTheCollectionSignalsTheScreenToClose() {
        vm.deleteCollection()

        assertTrue(vm.state.value.deleted)
    }
}
