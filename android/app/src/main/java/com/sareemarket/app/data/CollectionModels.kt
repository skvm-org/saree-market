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
