package com.sareemarket.app.data

import kotlinx.serialization.Serializable

@Serializable
data class RefItem(val id: Long, val name: String)

data class ReferenceData(val sareeTypes: List<RefItem>, val fabrics: List<RefItem>)

/** One row of the seller dashboard. [coverItemId] is null for a collection with no photos. */
@Serializable
data class CollectionSummary(
    val id: Long,
    val title: String,
    val sareeType: String,
    val fabric: String,
    val price: Int,
    val itemCount: Long,
    val availableCount: Long,
    val soldOutCount: Long,
    val coverItemId: Long? = null,
    /** Up to three photos for the strip on the dashboard card. */
    val previewItemIds: List<Long> = emptyList(),
    /** ISO-8601 instant the collection was created. */
    val createdAt: String? = null,
    /** Who sells it; set only on the reseller catalog, where cards come from many shops. */
    val sellerId: Long? = null,
    val shopName: String? = null,
)

@Serializable
enum class ItemStatus { AVAILABLE, SOLD_OUT }

@Serializable
data class Item(
    val id: Long,
    val colour: String? = null,
    val priceOverride: Int? = null,
    val effectivePrice: Int,
    val status: ItemStatus,
    /** Pieces the seller has left. 0 means out of stock. */
    val quantity: Int = 1,
)

@Serializable
data class CollectionDetail(
    val id: Long,
    val sellerId: Long,
    val title: String,
    val sareeType: RefItem,
    val fabric: RefItem,
    val price: Int,
    val description: String? = null,
    val items: List<Item> = emptyList(),
)

/** What the seller types in the create and edit forms. */
data class CollectionForm(
    val title: String,
    val sareeTypeId: Long,
    val fabricId: Long,
    val price: Int,
    val description: String,
)

@Serializable
data class UpdateCollectionBody(
    val title: String,
    val sareeTypeId: Long,
    val fabricId: Long,
    val price: Int,
    val description: String,
)

@Serializable
data class StatusBody(val status: ItemStatus)

const val MAX_PHOTOS = 30
