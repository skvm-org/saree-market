package com.sareemarket.app.data

import kotlinx.coroutines.CompletableDeferred

/** What the app sent in one GET /catalog call. */
data class CatalogCall(
    val q: String?, val typeIds: List<Long>?, val fabricIds: List<Long>?, val colour: String?,
    val minPrice: Int?, val maxPrice: Int?, val sellerId: Long?, val status: String, val since: Int?,
    val sort: String, val page: Int,
)

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

    override suspend fun item(id: Long) = onItem(id)
    override suspend fun sellers() = sellers
    override suspend fun seller(id: Long) = onSeller(id)
}
