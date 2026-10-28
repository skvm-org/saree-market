package com.sareemarket.app.alerts

import com.sareemarket.app.data.AppNotification
import com.sareemarket.app.data.BookingSummary
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
     * The text for a booking alert, or null when nothing new has arrived since the last check. A seller's count is
     * requests waiting for an answer; a reseller's is answers not yet looked at. The two never apply to one account.
     */
    fun bookingText(lastPending: Long, lastUnread: Long, now: BookingSummary): String? = when {
        now.pending > lastPending -> (now.pending - lastPending).let { if (it == 1L) "1 new booking request" else "$it new booking requests" }
        now.unread > lastUnread -> (now.unread - lastUnread).let { if (it == 1L) "A seller answered your booking request" else "$it booking requests have answers" }
        else -> null
    }

    /** The point to remember so the same collections are not announced twice. */
    fun newest(fresh: List<AppNotification>): Instant = fresh.maxOf { Instant.parse(it.updatedAt) }
}
