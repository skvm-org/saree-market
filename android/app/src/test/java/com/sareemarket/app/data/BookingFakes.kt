package com.sareemarket.app.data

import retrofit2.Response

fun booking(
    id: Long,
    status: BookingStatus = BookingStatus.PENDING,
    itemId: Long = 10,
    unread: Boolean = false,
    holdExpiresAt: String? = null,
    note: String? = null,
    itemStatus: ItemStatus = ItemStatus.AVAILABLE,
    createdAt: String = "2026-10-28T10:00:00Z",
    counterparty: BookingParty = BookingParty(1, "Lakshmi Silks", "9876543210", "9876543211"),
) = Booking(
    id, status, note, createdAt, null, holdExpiresAt, unread,
    BookingItem(itemId, 5, "Diwali silks", "Soft silk", "Pure silk", "maroon", 2500, itemStatus), counterparty,
)

fun bookingPage(page: Int, totalPages: Int, perPage: Int = 2, status: BookingStatus = BookingStatus.PENDING) = BookingPage(
    items = (0 until perPage).map { booking(page * 100L + it, status) },
    page = page, pageSize = perPage, totalItems = totalPages.toLong() * perPage, totalPages = totalPages,
)

/** Answers every call with something sensible; tests replace the lambdas they care about and read [calls]. */
class FakeBookingApi : BookingApi {
    val calls = mutableListOf<String>()
    var onBook: (Long, String?) -> Booking = { id, note -> booking(1, itemId = id, note = note) }
    var onMine: (String?, Int) -> BookingPage = { _, page -> bookingPage(page, totalPages = 1) }
    var onReceived: (String?, Int) -> BookingPage = { _, page -> bookingPage(page, totalPages = 1) }
    var onAction: (String, Long) -> Booking = { action, id ->
        booking(
            id,
            when (action) {
                "accept", "extend" -> BookingStatus.ACCEPTED
                "reject" -> BookingStatus.REJECTED
                "sold" -> BookingStatus.SOLD
                else -> BookingStatus.CANCELLED
            },
        )
    }
    var onSeen: () -> Response<Unit> = { Response.success(Unit) }
    var summary: () -> BookingSummary = { BookingSummary() }

    override suspend fun book(itemId: Long, body: BookingBody): Booking {
        calls += "book $itemId ${body.note}"
        return onBook(itemId, body.note)
    }

    override suspend fun mine(status: String?, page: Int): BookingPage {
        calls += "mine $status $page"
        return onMine(status, page)
    }

    override suspend fun received(state: String?, page: Int): BookingPage {
        calls += "received $state $page"
        return onReceived(state, page)
    }

    override suspend fun accept(id: Long) = act("accept", id)

    override suspend fun reject(id: Long) = act("reject", id)

    override suspend fun extend(id: Long) = act("extend", id)

    override suspend fun sold(id: Long) = act("sold", id)

    override suspend fun cancel(id: Long) = act("cancel", id)

    private fun act(action: String, id: Long): Booking {
        calls += "$action $id"
        return onAction(action, id)
    }

    override suspend fun seen(): Response<Unit> {
        calls += "seen"
        return onSeen()
    }

    override suspend fun summary(): BookingSummary {
        calls += "summary"
        return summary.invoke()
    }
}
