package com.sareemarket.app.ui

import com.sareemarket.app.data.CollectionRepository
import com.sareemarket.app.data.FakeCollectionsApi
import com.sareemarket.app.data.FakePhotoSource
import com.sareemarket.app.data.MAX_PHOTOS
import com.sareemarket.app.data.detail
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.seller.NewCollectionViewModel
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
class NewCollectionViewModelTest {

    private val api = FakeCollectionsApi()
    private val photos = (1..40).associate { "p$it" to ByteArray(10) }
    private val vm by lazy { NewCollectionViewModel(CollectionRepository(api, FakePhotoSource(photos), testJson)) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun fillValid() {
        vm.onTitle("Diwali silks")
        vm.onType(1)
        vm.onFabric(1)
        vm.onPrice("2500")
        vm.onPhotosPicked(listOf("p1", "p2"))
    }

    @Test
    fun loadsTypesAndFabricsForTheDropdowns() {
        assertEquals(api.types, vm.state.value.reference?.sareeTypes)
        assertEquals(api.fabrics, vm.state.value.reference?.fabrics)
    }

    @Test
    fun emptyFormListsEveryProblemAndSendsNothing() {
        vm.submit()

        assertEquals(setOf("title", "sareeTypeId", "fabricId", "price", "files"), vm.state.value.fieldErrors.keys)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun priceKeepsOnlyDigits() {
        vm.onPrice("₹2,5a00")

        assertEquals("2500", vm.state.value.fields.price)
    }

    @Test
    fun pickedPhotosAreMergedWithoutRepeatsAndCappedAt30() {
        vm.onPhotosPicked((1..20).map { "p$it" })
        vm.onPhotosPicked((10..40).map { "p$it" })

        assertEquals(MAX_PHOTOS, vm.state.value.photos.size)
        assertEquals(MAX_PHOTOS, vm.state.value.photos.distinct().size)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun removingAPhotoDropsIt() {
        vm.onPhotosPicked(listOf("p1", "p2"))
        vm.removePhoto("p1")

        assertEquals(listOf("p2"), vm.state.value.photos)
    }

    @Test
    fun validSubmitUploadsAndReportsTheNewCollectionId() {
        api.onCreate = { _, files ->
            assertEquals(2, files.size)
            detail(id = 42)
        }
        fillValid()

        vm.submit()

        assertEquals(42L, vm.state.value.createdId)
        assertFalse(vm.state.value.uploading)
    }

    @Test
    fun badPhotoFromTheServerIsShownAndNothingIsCreated() {
        api.onCreate = { _, _ ->
            throw httpError(400, """{"code":"INVALID_IMAGE","message":"\"p1.jpg\" is not a valid JPG, PNG or WebP photo"}""")
        }
        fillValid()

        vm.submit()

        assertNull(vm.state.value.createdId)
        assertTrue(vm.state.value.error!!.contains("p1.jpg"))
        assertFalse(vm.state.value.uploading)
    }

    @Test
    fun serverFieldErrorsAppearOnTheirFields() {
        api.onCreate = { _, _ ->
            throw httpError(400, """{"code":"VALIDATION_ERROR","message":"Some fields are invalid","fields":{"sareeTypeId":"Unknown saree type"}}""")
        }
        fillValid()

        vm.submit()

        assertEquals("Unknown saree type", vm.state.value.fieldErrors["sareeTypeId"])
        assertNull(vm.state.value.error)
    }
}
