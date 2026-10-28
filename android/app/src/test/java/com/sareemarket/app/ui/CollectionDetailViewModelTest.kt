package com.sareemarket.app.ui

import androidx.lifecycle.SavedStateHandle
import com.sareemarket.app.data.CollectionRepository
import com.sareemarket.app.data.FakeCollectionsApi
import com.sareemarket.app.data.FakePhotoSource
import com.sareemarket.app.data.detail
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.data.item
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.seller.CollectionDetailViewModel
import com.sareemarket.app.ui.seller.CollectionFields
import com.sareemarket.app.ui.seller.ItemFilter
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

    private fun status(id: Long) = vm.state.value.detail!!.items.first { it.id == id }.status

    @Test
    fun togglingFlipsTheCardAndTellsTheServer() {
        var sent: ItemStatus? = null
        api.onUpdateItem = { id, patch -> sent = ItemStatus.valueOf(patch.getValue("status").toString().trim('"')); item(id, status = sent!!) }

        vm.toggleStatus(10)

        assertEquals(ItemStatus.SOLD_OUT, status(10))
        assertEquals(ItemStatus.SOLD_OUT, sent)
        assertEquals(ItemStatus.AVAILABLE, status(11)) // other cards untouched
        assertNull(vm.state.value.error)
    }

    @Test
    fun togglingTwiceGoesBackToAvailable() {
        vm.toggleStatus(10)
        vm.toggleStatus(10)

        assertEquals(ItemStatus.AVAILABLE, status(10))
    }

    @Test
    fun cardSnapsBackAndShowsWhyWhenTheServerRefuses() {
        api.onUpdateItem = { _, _ -> throw httpError(403, """{"code":"FORBIDDEN","message":"Not yours"}""") }

        vm.toggleStatus(10)

        assertEquals(ItemStatus.AVAILABLE, status(10))
        assertEquals("Not yours", vm.state.value.error)
    }

    @Test
    fun markAllSoldOutUpdatesEveryCard() {
        api.onSetAllStatus = { s -> detail(items = listOf(item(10, status = s), item(11, status = s))) }

        vm.markAll(ItemStatus.SOLD_OUT)

        assertEquals(2, vm.state.value.count(ItemStatus.SOLD_OUT))
        assertEquals(0, vm.state.value.count(ItemStatus.AVAILABLE))
    }

    @Test
    fun failedMarkAllLeavesTheCardsAsTheyWere() {
        api.onSetAllStatus = { throw httpError(500) }

        vm.markAll(ItemStatus.SOLD_OUT)

        assertEquals(2, vm.state.value.count(ItemStatus.AVAILABLE))
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun filtersShowOnlyTheMatchingCardsAndCountsStayFull() {
        api.get = { detail(items = listOf(item(10), item(11, status = ItemStatus.SOLD_OUT), item(12))) }

        vm.setFilter(ItemFilter.SOLD_OUT)
        assertEquals(listOf(11L), vm.state.value.visibleItems.map { it.id })
        vm.setFilter(ItemFilter.AVAILABLE)
        assertEquals(listOf(10L, 12L), vm.state.value.visibleItems.map { it.id })
        vm.setFilter(ItemFilter.ALL)
        assertEquals(3, vm.state.value.visibleItems.size)
        assertEquals(2, vm.state.value.count(ItemStatus.AVAILABLE))
        assertEquals(1, vm.state.value.count(ItemStatus.SOLD_OUT))
    }

    @Test
    fun markAllOffersUndoNamingHowManyChanged() {
        api.get = { detail(items = listOf(item(10), item(11, status = ItemStatus.SOLD_OUT), item(12))) }
        api.onSetAllStatus = { st -> detail(items = listOf(item(10, status = st), item(11, status = st), item(12, status = st))) }

        vm.markAll(ItemStatus.SOLD_OUT)

        assertEquals("Marked 2 sold out", vm.state.value.undo?.message)
    }

    @Test
    fun undoAfterMarkAllOfMixedStockPutsEachSareeBack() {
        api.get = { detail(items = listOf(item(10), item(11, status = ItemStatus.SOLD_OUT))) }
        api.onSetAllStatus = { st -> detail(items = listOf(item(10, status = st), item(11, status = st))) }
        vm.markAll(ItemStatus.SOLD_OUT)
        api.calls.clear()

        vm.undoMarkAll()

        assertEquals(1, api.calls.count { it == "updateItem" }) // only the one that changed
        assertNull(vm.state.value.undo)
    }

    @Test
    fun undoWhenEverythingWasTheSameIsOneBulkCall() {
        api.get = { detail(items = listOf(item(10), item(11))) }
        vm.markAll(ItemStatus.SOLD_OUT)
        api.calls.clear()

        vm.undoMarkAll()

        assertEquals(listOf("setAllStatus"), api.calls)
    }

    @Test
    fun markAllWhenNothingWouldChangeDoesNothing() {
        api.get = { detail(items = listOf(item(10, status = ItemStatus.SOLD_OUT))) }

        vm.markAll(ItemStatus.SOLD_OUT)

        assertTrue(api.calls.none { it == "setAllStatus" })
        assertNull(vm.state.value.undo)
    }

    @Test
    fun theOnHoldFilterShowsOnlyHeldPhotosAndCountsThem() {
        api.get = { detail(items = listOf(item(10), item(11, status = ItemStatus.ON_HOLD), item(12, status = ItemStatus.SOLD_OUT))) }

        vm.setFilter(ItemFilter.ON_HOLD)

        assertEquals(listOf(11L), vm.state.value.visibleItems.map { it.id })
        assertEquals(1, vm.state.value.count(ItemStatus.ON_HOLD))
        assertEquals(1, vm.state.value.count(ItemStatus.AVAILABLE))
    }

    @Test
    fun aHeldPhotoIsNotFlippedByTheStockToggle() {
        api.get = { detail(items = listOf(item(10, status = ItemStatus.ON_HOLD))) }
        vm.state.value // load

        vm.toggleStatus(10)

        assertFalse("updateItem" in api.calls)
        assertEquals(ItemStatus.ON_HOLD, vm.state.value.detail?.items?.single()?.status)
    }

    @Test
    fun markAllSoldOutDoesNotCountOrUndoHeldPhotos() {
        api.get = { detail(items = listOf(item(10), item(11, status = ItemStatus.ON_HOLD), item(12))) }
        api.onSetAllStatus = { s -> detail(items = listOf(item(10, status = s), item(11, status = ItemStatus.ON_HOLD), item(12, status = s))) }

        vm.markAll(ItemStatus.SOLD_OUT)

        assertEquals("Marked 2 sold out", vm.state.value.undo?.message)
        assertEquals(setOf(10L, 12L), vm.state.value.undo?.previous?.keys)
        assertEquals(ItemStatus.ON_HOLD, vm.state.value.detail?.items?.first { it.id == 11L }?.status)
    }
}
