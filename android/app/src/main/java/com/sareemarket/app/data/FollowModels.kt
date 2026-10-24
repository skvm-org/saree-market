package com.sareemarket.app.data

import kotlinx.serialization.Serializable

/** INSTANT: tell me about new sarees. MUTED: keep following, but send no notifications. */
@Serializable
enum class NotifyMode { INSTANT, MUTED }

@Serializable
data class FollowItem(val sellerId: Long, val shopName: String, val marketLocation: String, val notifyMode: NotifyMode)

@Serializable
data class FollowBody(val notifyMode: NotifyMode)

@Serializable
enum class NotificationType { NEW_COLLECTION, ITEMS_ADDED, BACK_IN_STOCK }

/** One line in the notifications list. [message] is ready to show; the times are ISO-8601 strings. */
@Serializable
data class AppNotification(
    val id: Long,
    val type: NotificationType,
    val itemCount: Int,
    val sellerId: Long,
    val shopName: String,
    val collectionId: Long,
    val collectionTitle: String,
    val message: String,
    val read: Boolean,
    val createdAt: String,
    val updatedAt: String,
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
