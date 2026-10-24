package com.sareemarket.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class CollectionRepository @Inject constructor(
    private val api: CollectionsApi,
    private val photos: PhotoSource,
    private val json: Json,
) {
    suspend fun referenceData(): Result<ReferenceData> =
        apiCall(json) { ReferenceData(api.sareeTypes(), api.fabrics()) }

    suspend fun myCollections(): Result<List<CollectionSummary>> = apiCall(json) { api.myCollections() }

    suspend fun collection(id: Long): Result<CollectionDetail> = apiCall(json) { api.collection(id) }

    suspend fun create(form: CollectionForm, photoUris: List<String>, onProgress: (Float) -> Unit): Result<CollectionDetail> =
        apiCall(json) {
            api.create(
                title = form.title.text(),
                sareeTypeId = form.sareeTypeId.toString().text(),
                fabricId = form.fabricId.toString().text(),
                price = form.price.toString().text(),
                description = form.description.text(),
                files = photoParts(photos, photoUris, onProgress),
            )
        }

    suspend fun update(id: Long, form: CollectionForm): Result<CollectionDetail> = apiCall(json) {
        api.update(id, UpdateCollectionBody(form.title, form.sareeTypeId, form.fabricId, form.price, form.description))
    }

    suspend fun delete(id: Long): Result<Unit> = apiCall(json) { api.delete(id).requireSuccess() }

    suspend fun addPhotos(id: Long, photoUris: List<String>, onProgress: (Float) -> Unit): Result<CollectionDetail> =
        apiCall(json) { api.addPhotos(id, photoParts(photos, photoUris, onProgress)) }

    /** Sends both fields every time: a blank colour or a null price clears it on the server. */
    suspend fun updateItem(id: Long, colour: String, priceOverride: Int?): Result<Item> = apiCall(json) {
        api.updateItem(id, buildJsonObject {
            put("colour", JsonPrimitive(colour))
            put("priceOverride", priceOverride?.let { JsonPrimitive(it) } ?: JsonNull)
        })
    }

    suspend fun deleteItem(id: Long): Result<Unit> = apiCall(json) { api.deleteItem(id).requireSuccess() }

    private fun String.text(): RequestBody = toRequestBody("text/plain".toMediaType())

    private fun retrofit2.Response<Unit>.requireSuccess() {
        if (!isSuccessful) throw retrofit2.HttpException(this)
    }
}
