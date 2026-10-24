package com.sareemarket.app.data

import kotlinx.coroutines.CompletableDeferred

/** What the app sent in one GET /catalog call. */
data class CatalogCall(
    val q: String?, val typeIds: List<Long>?, val fabricIds: List<Long>?, val colour: String?,
    val minPrice: Int?, val maxPrice: Int?, val sellerId: Long?, val status: String, val since: Int?,
    val sort: String, val page: Int,
)

/** What the app sent in one photo search: the picture's file name plus the filters. */
data class ImageCall(val fileName: String?, val partName: String?, val call: CatalogCall)

/** Two pretend photos on the phone, named like the URIs the picker returns. */
fun fakePhotos() = FakePhotoSource(mapOf("photo-1" to ByteArray(100), "photo-2" to ByteArray(100)))

fun card(id: Long, status: ItemStatus = ItemStatus.AVAILABLE) = CatalogItem(
    id, 5, "Diwali silks", "Soft silk", "Pure silk", "maroon", 2500, status, 1, "Lakshmi Silks",
)

fun page(number: Int, totalPages: Int, perPage: Int = 2, total: Long = totalPages.toLong() * perPage) = CatalogPage(
    items = (0 until perPage).map { card(number * 100L + it) },
    page = number, pageSize = perPage, totalItems = total, totalPages = totalPages,
)

val lakshmi = SellerContact(1, "Lakshmi Silks", "Chinnalapatti", "9876543210", "9876543211")

fun itemDetail(id: Long = 10, seller: SellerContact = lakshmi) = ItemDetail(
    id, 5, "Diwali silks", "Festive", "Soft silk", "Pure silk", "maroon", null, 2500, ItemStatus.AVAILABLE, seller,
)

class FakeCatalogApi : CatalogApi {
    val calls = mutableListOf<CatalogCall>()
    var onCatalog: (CatalogCall) -> CatalogPage = { page(it.page, totalPages = 3) }
    var sellers = listOf(SellerListItem(1, "Lakshmi Silks", "Chinnalapatti"))
    var onItem: (Long) -> ItemDetail = { itemDetail(it) }
    var onSeller: (Long) -> SellerPage = { SellerPage(it, "Lakshmi Silks", "Chinnalapatti", "9876543210", null, listOf(summary())) }

    val imageCalls = mutableListOf<ImageCall>()
    var onImageSearch: (ImageCall) -> CatalogPage = { page(it.call.page, totalPages = 2).let { p -> p.copy(items = p.items.map { c -> c.copy(distance = 3) }) } }

    /** When set, a search for q == "slow" waits for this before answering, to test late answers. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun catalog(
        q: String?, typeIds: List<Long>?, fabricIds: List<Long>?, colour: String?, minPrice: Int?, maxPrice: Int?,
        sellerId: Long?, status: String, since: Int?, sort: String, page: Int,
    ): CatalogPage {
        val call = CatalogCall(q, typeIds, fabricIds, colour, minPrice, maxPrice, sellerId, status, since, sort, page)
        calls += call
        if (q == "slow") gate?.await()
        return onCatalog(call)
    }

    override suspend fun imageSearch(
        image: okhttp3.MultipartBody.Part, q: String?, typeIds: List<Long>?, fabricIds: List<Long>?, colour: String?,
        minPrice: Int?, maxPrice: Int?, sellerId: Long?, status: String, since: Int?, page: Int,
    ): CatalogPage {
        val disposition = image.headers?.get("Content-Disposition").orEmpty()
        val call = ImageCall(
            fileName = Regex("filename=\"([^\"]*)\"").find(disposition)?.groupValues?.get(1),
            partName = Regex("name=\"([^\"]*)\"").find(disposition)?.groupValues?.get(1),
            call = CatalogCall(q, typeIds, fabricIds, colour, minPrice, maxPrice, sellerId, status, since, "", page),
        )
        imageCalls += call
        return onImageSearch(call)
    }

    override suspend fun item(id: Long) = onItem(id)
    override suspend fun sellers() = sellers
    override suspend fun seller(id: Long) = onSeller(id)
}
