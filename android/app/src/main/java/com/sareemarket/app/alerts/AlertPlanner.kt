package com.sareemarket.app.alerts

import com.sareemarket.app.data.AppNotification
import com.sareemarket.app.data.OrderSummary
import com.sareemarket.app.data.NotificationType
import java.time.Instant

/** Decides what, if anything, the background check shows as an Android notification. */
object AlertPlanner {

    /** New collections from followed sellers that arrived after the last alert and are still unread. */
    fun fresh(items: List<AppNotification>, since: Instant): List<AppNotification> =
        items.filter { !it.read && it.type == NotificationType.NEW_COLLECTION && Instant.parse(it.updatedAt).isAfter(since) }

    fun text(fresh: List<AppNotification>): String =
        if (fresh.size == 1) fresh.first().message else "${fresh.size} new collections from sellers you follow"

    /**
     * The text for an order alert, or null when nothing new has arrived since the last check. A seller hears about
     * orders newly waiting to be shipped; anyone hears when the number of orders with unseen updates rises.
     */
    fun orderText(lastToShip: Int, lastUnread: Int, now: OrderSummary): String? = when {
        now.toShip > lastToShip -> (now.toShip - lastToShip).let { if (it == 1) "1 new order to ship" else "$it new orders to ship" }
        now.unread > lastUnread -> ORDER_UPDATED
        else -> null
    }

    /**
     * "An order was updated" made specific: the newest unread order update in [notifications] ("Lakshmi Silks shipped
     * your order #12"), or [generic] when there is none (a seller's unread orders have no notification rows).
     */
    fun orderUpdateText(generic: String, notifications: List<AppNotification>): String =
        if (generic != ORDER_UPDATED) generic
        else notifications.filter { !it.read && it.type == NotificationType.ORDER_UPDATE }
            .maxByOrNull { Instant.parse(it.updatedAt) }?.message ?: generic

    const val ORDER_UPDATED = "An order was updated"

    /** The point to remember so the same collections are not announced twice. */
    fun newest(fresh: List<AppNotification>): Instant = fresh.maxOf { Instant.parse(it.updatedAt) }
}
