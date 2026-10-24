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

        assertEquals(mapOf("title" to "Diwali silks", "sareeTypeId" to "1", "fabricId" to "2", "price" to "2500", "description" to "Festive"), sentFields)
        assertEquals(2, partCount)
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
}
