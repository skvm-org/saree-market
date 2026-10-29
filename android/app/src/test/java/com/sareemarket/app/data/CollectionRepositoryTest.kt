package com.sareemarket.app.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionRepositoryTest {

    private val api = FakeCollectionsApi()
    private val photos = FakePhotoSource(mapOf("a" to ByteArray(150_000), "b" to ByteArray(50_000)))
    private val repo = CollectionRepository(api, photos, testJson)
    private val form = CollectionForm("Diwali silks", 1, 2, 2500, "Festive")

    @Test
    fun createSendsFormFieldsAndOneFilesPartPerPhoto() = runTest {
        var sentFields: Map<String, String> = emptyMap()
        var partCount = 0
        api.onCreate = { fields, files ->
            sentFields = fields
            partCount = files.size
            detail()
        }

        assertTrue(repo.create(form, listOf("a", "b")) {}.isSuccess)

        assertEquals(mapOf("title" to "Diwali silks", "sareeTypeId" to "1", "fabricId" to "2", "price" to "2500", "description" to "Festive", "quantity" to "1"), sentFields)
        assertEquals(2, partCount)
    }

    @Test
    fun createSendsTheChosenQuantity() = runTest {
        var sent = ""
        api.onCreate = { fields, _ -> sent = fields.getValue("quantity"); detail() }

        repo.create(form, listOf("a"), quantity = 7) {}

        assertEquals("7", sent)
    }

    @Test
    fun addedPhotosStartWithOnePiece() = runTest {
        repo.addPhotos(5, listOf("a")) {}

        assertEquals("1", api.lastAddQuantity)
    }

    @Test
    fun updateItemSendsTheQuantityOnlyWhenGiven() = runTest {
        var sent: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
        api.onUpdateItem = { id, patch -> sent = patch; item(id) }

        repo.updateItem(10, "maroon", null)
        assertTrue("quantity" !in sent)

        repo.updateItem(10, "maroon", null, quantity = 4)
        assertEquals(JsonPrimitive(4), sent.getValue("quantity"))
    }

    @Test
    fun uploadProgressGrowsToOneAcrossAllPhotos() = runTest {
        api.onCreate = { _, files ->
            files.forEach { it.body.writeTo(Buffer()) } // the real client streams each photo like this
            detail()
        }
        val seen = mutableListOf<Float>()

        repo.create(form, listOf("a", "b")) { seen += it }

        assertTrue(seen.isNotEmpty())
        assertEquals(seen.sorted(), seen) // never goes backwards
        assertEquals(1f, seen.last(), 0.0001f)
        assertTrue(seen.first() < 1f)
    }

    @Test
    fun failedUploadSurfacesTheServerMessageAndFields() = runTest {
        val body = """{"code":"INVALID_IMAGE","message":"\"x.jpg\" is not a valid JPG, PNG or WebP photo","fields":{}}"""
        api.onCreate = { _, _ -> throw httpError(400, body) }

        val failure = repo.create(form, listOf("a")) {}.exceptionOrNull() as ApiFailure

        assertEquals("INVALID_IMAGE", failure.code)
        assertTrue(failure.message!!.contains("x.jpg"))
    }

    @Test
    fun updateItemSendsBothFieldsAndAnExplicitNullToClearThePrice() = runTest {
        var sent: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
        api.onUpdateItem = { id, patch -> sent = patch; item(id) }

        repo.updateItem(10, "maroon", null)

        assertEquals("maroon", sent.getValue("colour").jsonPrimitive.content)
        assertEquals(JsonNull, sent.getValue("priceOverride"))
        repo.updateItem(10, "", 1800)
        assertEquals(JsonPrimitive(1800), sent.getValue("priceOverride"))
    }

    @Test
    fun deleteTreatsNonSuccessResponsesAsFailures() = runTest {
        api.onDelete = { retrofit2.Response.error(403, """{"code":"FORBIDDEN","message":"Not yours"}""".toResponseBodyJson()) }

        val failure = repo.delete(5).exceptionOrNull() as ApiFailure

        assertEquals("FORBIDDEN", failure.code)
    }

    @Test
    fun offlineReadsReportANetworkError() = runTest {
        api.mine = { throw networkError() }

        assertEquals("NETWORK", (repo.myCollections().exceptionOrNull() as ApiFailure).code)
    }

    private fun String.toResponseBodyJson() =
        toResponseBody("application/json".toMediaType())

    @Test
    fun itemStatusChangeSendsOnlyTheStatus() = runTest {
        var sent: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
        api.onUpdateItem = { id, patch -> sent = patch; item(id, status = ItemStatus.SOLD_OUT) }

        val result = repo.setItemStatus(10, ItemStatus.SOLD_OUT)

        assertEquals(setOf("status"), sent.keys)
        assertEquals("SOLD_OUT", sent.getValue("status").jsonPrimitive.content)
        assertEquals(ItemStatus.SOLD_OUT, result.getOrNull()?.status)
    }

    @Test
    fun markAllSendsTheChosenStatusAndReturnsTheCollection() = runTest {
        var sent: ItemStatus? = null
        api.onSetAllStatus = { sent = it; detail(items = listOf(item(10, status = it), item(11, status = it))) }

        val result = repo.setAllStatus(5, ItemStatus.SOLD_OUT)

        assertEquals(ItemStatus.SOLD_OUT, sent)
        assertTrue(result.getOrNull()!!.items.all { it.status == ItemStatus.SOLD_OUT })
    }

    @Test
    fun aSummaryWithoutAnOnHoldCountDecodes() {
        val summary = testJson.decodeFromString<CollectionSummary>(
            """{"id":5,"title":"Diwali silks","sareeType":"Kanjivaram","fabric":"Pure silk","price":2500,"itemCount":3,
            "availableCount":2,"soldOutCount":1,"coverItemId":10,"previewItemIds":[10,11]}""",
        )

        assertEquals(3L, summary.itemCount)
        assertEquals(1L, summary.soldOutCount)
    }

}
