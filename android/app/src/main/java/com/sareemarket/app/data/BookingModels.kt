package com.sareemarket.app.data

import kotlinx.serialization.Serializable

/** PENDING and ACCEPTED are open; the rest are final. */
@Serializable
enum class BookingStatus {
    PENDING, ACCEPTED, REJECTED, CANCELLED, EXPIRED, SOLD, CLOSED;

    val isOpen get() = this == PENDING || this == ACCEPTED
}

/** The saree a booking request is about. */
@Serializable
data class BookingItem(
    val id: Long,
    val collectionId: Long,
    val collectionTitle: String,
    val sareeType: String,
    val fabric: String,
    val colour: String? = null,
    val price: Int,
    val status: ItemStatus,
)

/** The other side of a request: the shop for a reseller, the reseller for a seller. */
@Serializable
data class BookingParty(
    val id: Long,
    val name: String,
    val phone: String? = null,
    val whatsappNumber: String? = null,
)

@Serializable
data class Booking(
    val id: Long,
    val status: BookingStatus,
    val note: String? = null,
    /** ISO-8601 instants. */
    val createdAt: String,
    val respondedAt: String? = null,
    val holdExpiresAt: String? = null,
    /** For a reseller: the status changed since they last looked. */
    val unread: Boolean = false,
    val item: BookingItem,
    val counterparty: BookingParty,
)

@Serializable
data class BookingPage(
    val items: List<Booking>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int,
)

/** A seller reads the first seven numbers, a reseller reads unread and open. */
@Serializable
data class BookingSummary(
    val received: Long = 0,
    val pending: Long = 0,
    val onHold: Long = 0,
    val responded: Long = 0,
    val sold: Long = 0,
    val rejected: Long = 0,
    val expired: Long = 0,
    val unread: Long = 0,
    val open: Long = 0,
)

/** The reseller's latest request for one photo. */
@Serializable
data class MyBooking(val id: Long, val status: BookingStatus, val holdExpiresAt: String? = null)

@Serializable
data class BookingBody(val note: String? = null)

/** Which requests a list shows. */
enum class BookingFilter { OPEN, HISTORY }
