package com.sareemarket.app.data

import kotlinx.serialization.Serializable

/** INSTANT: tell me about new sarees. MUTED: keep following, but send no notifications. */
@Serializable
enum class NotifyMode { INSTANT, MUTED }

@Serializable
data class FollowItem(
    val sellerId: Long,
    val shopName: String,
    val marketLocation: String,
    val notifyMode: NotifyMode,
    /** ISO-8601 instant of the seller's newest collection, or null if they have none. */
    val lastUploadAt: String? = null,
)

@Serializable
data class FollowBody(val notifyMode: NotifyMode)

@Serializable
enum class NotificationType { NEW_COLLECTION, ITEMS_ADDED, BACK_IN_STOCK, ORDER_UPDATE }

/** One line in the notifications list. [message] is ready to show; the times are ISO-8601 strings. */
@Serializable
data class AppNotification(
    val id: Long,
    val type: NotificationType,
    val itemCount: Int,
    val sellerId: Long,
    val shopName: String,
    /** Set for follower notifications; null for an [NotificationType.ORDER_UPDATE], which has an [orderId] instead. */
    val collectionId: Long? = null,
    val collectionTitle: String? = null,
    val message: String,
    val read: Boolean,
    val createdAt: String,
    val updatedAt: String,
    /** A photo of the collection (or of the first saree ordered), for the round thumbnail. */
    val coverItemId: Long? = null,
    val orderId: Long? = null,
    /** The order's new status for an order update, e.g. CONFIRMED. */
    val orderStatus: String? = null,
)

@Serializable
data class NotificationPage(
    val items: List<AppNotification>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int,
)

@Serializable
data class UnreadCount(val count: Long)
