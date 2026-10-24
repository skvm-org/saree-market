package com.sareemarket.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class CatalogRepository @Inject constructor(
    private val api: CatalogApi,
    private val collectionsApi: CollectionsApi,
    private val json: Json,
) {
    suspend fun catalog(query: CatalogQuery, page: Int): Result<CatalogPage> = apiCall(json) {
        api.catalog(
            q = query.q.trim().ifEmpty { null },
            typeIds = query.typeIds.sorted().ifEmpty { null },
            fabricIds = query.fabricIds.sorted().ifEmpty { null },
            colour = query.colour.trim().ifEmpty { null },
            minPrice = query.minPrice,
            maxPrice = query.maxPrice,
            sellerId = query.sellerId,
            status = if (query.includeSoldOut) "ALL" else "AVAILABLE",
            since = query.sinceDays,
            sort = query.sort.param,
            page = page,
        )
    }

    suspend fun item(id: Long): Result<ItemDetail> = apiCall(json) { api.item(id) }

    suspend fun seller(id: Long): Result<SellerPage> = apiCall(json) { api.seller(id) }

    suspend fun sellers(): Result<List<SellerListItem>> = apiCall(json) { api.sellers() }

    suspend fun referenceData(): Result<ReferenceData> =
        apiCall(json) { ReferenceData(collectionsApi.sareeTypes(), collectionsApi.fabrics()) }

    suspend fun collection(id: Long): Result<CollectionDetail> = apiCall(json) { collectionsApi.collection(id) }
}
