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
     * orders newly waiting for confirmation; anyone hears when the number of orders with unseen updates rises.
     */
    fun orderText(lastToConfirm: Int, lastUnread: Int, now: OrderSummary): String? = when {
        now.toConfirm > lastToConfirm -> (now.toConfirm - lastToConfirm).let { if (it == 1) "1 new order to confirm" else "$it new orders to confirm" }
        now.unread > lastUnread -> "An order was updated"
        else -> null
    }

    /** The point to remember so the same collections are not announced twice. */
    fun newest(fresh: List<AppNotification>): Instant = fresh.maxOf { Instant.parse(it.updatedAt) }
}
