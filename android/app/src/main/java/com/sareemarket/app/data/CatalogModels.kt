package com.sareemarket.app.data

import kotlinx.serialization.Serializable

@Serializable
data class CatalogItem(
    val id: Long,
    val collectionId: Long,
    val collectionTitle: String,
    val sareeType: String,
    val fabric: String,
    val colour: String? = null,
    val price: Int,
    val status: ItemStatus,
    val sellerId: Long,
    val shopName: String,
    /** How far this photo is from the searched picture (0 is identical); only set by a photo search. */
    val distance: Int? = null,
)

@Serializable
data class CatalogPage(
    val items: List<CatalogItem>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int,
)

@Serializable
data class SellerContact(
    val id: Long,
    val shopName: String,
    val marketLocation: String,
    val contactPhone: String? = null,
    val whatsappNumber: String? = null,
)

@Serializable
data class ItemDetail(
    val id: Long,
    val collectionId: Long,
    val collectionTitle: String,
    val collectionDescription: String? = null,
    val sareeType: String,
    val fabric: String,
    val colour: String? = null,
    val priceOverride: Int? = null,
    val price: Int,
    val status: ItemStatus,
    val seller: SellerContact,
)

@Serializable
data class SellerListItem(val id: Long, val shopName: String, val marketLocation: String)

@Serializable
data class SellerPage(
    val id: Long,
    val shopName: String,
    val marketLocation: String,
    val contactPhone: String? = null,
    val whatsappNumber: String? = null,
    val collections: List<CollectionSummary> = emptyList(),
)

enum class CatalogSort(val param: String, val label: String) {
    NEWEST("NEWEST", "Newest uploads"),
    PRICE_ASC("PRICE_ASC", "Price: low to high"),
    PRICE_DESC("PRICE_DESC", "Price: high to low"),
}

/** The price bands from the brief. A null limit means "no limit on that side". */
enum class PriceBand(val label: String, val min: Int?, val max: Int?) {
    UNDER_1K("Under ₹1,000", null, 999),
    FROM_1K("₹1,000–3,000", 1000, 3000),
    FROM_3K("₹3,000–7,000", 3000, 7000),
    FROM_7K("₹7,000–15,000", 7000, 15000),
    ABOVE_15K("Above ₹15,000", 15001, null),
}

/** Everything the search box and the filter sheet can set. */
data class CatalogQuery(
    val q: String = "",
    val typeIds: Set<Long> = emptySet(),
    val fabricIds: Set<Long> = emptySet(),
    val colour: String = "",
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val sellerId: Long? = null,
    val includeSoldOut: Boolean = false,
    val sinceDays: Int? = null,
    val sort: CatalogSort = CatalogSort.NEWEST,
) {
    /** How many filters are on, for the badge on the Filters button. The keyword and the sort do not count. */
    val activeFilterCount: Int
        get() = listOf(
            typeIds.isNotEmpty(),
            fabricIds.isNotEmpty(),
            colour.isNotBlank(),
            minPrice != null || maxPrice != null,
            sellerId != null,
            includeSoldOut,
            sinceDays != null,
        ).count { it }
}
