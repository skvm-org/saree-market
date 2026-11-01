package com.sareemarket.app.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import retrofit2.Response

fun item(id: Long, colour: String? = null, override: Int? = null, status: ItemStatus = ItemStatus.AVAILABLE) =
    Item(id, colour, override, override ?: 2500, status)

fun detail(id: Long = 5, items: List<Item> = listOf(item(10), item(11))) =
    CollectionDetail(id, 1, "Diwali silks", 2500, "Festive", items)

fun summary(id: Long = 5) = CollectionSummary(id, "Diwali silks", 2500, 2, 2, 0, 10)

fun RequestBody.text(): String = Buffer().also { writeTo(it) }.readUtf8()

class FakePhotoSource(private val files: Map<String, ByteArray> = emptyMap()) : PhotoSource {
    override fun info(photo: String) = PhotoInfo("$photo.jpg", "image/jpeg", files.getValue(photo).size.toLong())
    override fun open(photo: String): InputStream = ByteArrayInputStream(files.getValue(photo))
}

class FakeCollectionsApi : CollectionsApi {
    var mine: () -> List<CollectionSummary> = { listOf(summary()) }
    var get: () -> CollectionDetail = { detail() }
    var onCreate: (fields: Map<String, String>, files: List<MultipartBody.Part>) -> CollectionDetail = { _, _ -> detail() }
    var onUpdate: (UpdateCollectionBody) -> CollectionDetail = { detail() }
    var onAddPhotos: (List<MultipartBody.Part>) -> CollectionDetail = { detail() }
    var onUpdateItem: (Long, JsonObject) -> Item = { id, _ -> item(id) }
    var onSetAllStatus: (ItemStatus) -> CollectionDetail = { detail() }
    var onDelete: () -> Response<Unit> = { Response.success(Unit) }
    var onDeleteItem: () -> Response<Unit> = { Response.success(Unit) }
    val calls = mutableListOf<String>()

    override suspend fun myCollections() = mine()
    override suspend fun collection(id: Long) = get()

    override suspend fun create(
        title: RequestBody, price: RequestBody,
        description: RequestBody, quantity: RequestBody, files: List<MultipartBody.Part>,
    ): CollectionDetail {
        calls += "create"
        val fields = mapOf(
            "title" to title.text(),
            "price" to price.text(), "description" to description.text(), "quantity" to quantity.text(),
        )
        return onCreate(fields, files)
    }

    override suspend fun update(id: Long, body: UpdateCollectionBody): CollectionDetail {
        calls += "update"
        return onUpdate(body)
    }

    override suspend fun setAllStatus(id: Long, body: StatusBody): CollectionDetail {
        calls += "setAllStatus"
        return onSetAllStatus(body.status)
    }

    override suspend fun delete(id: Long): Response<Unit> {
        calls += "delete"
        return onDelete()
    }

    var lastAddQuantity: String? = null

    override suspend fun addPhotos(id: Long, quantity: RequestBody, files: List<MultipartBody.Part>): CollectionDetail {
        calls += "addPhotos"
        lastAddQuantity = quantity.text()
        return onAddPhotos(files)
    }

    override suspend fun updateItem(id: Long, patch: JsonObject): Item {
        calls += "updateItem"
        return onUpdateItem(id, patch)
    }

    override suspend fun deleteItem(id: Long): Response<Unit> {
        calls += "deleteItem"
        return onDeleteItem()
    }
}
