package com.sareemarket.app.data

import kotlinx.serialization.Serializable

/**
 * An order is CONFIRMED when the customer submits the UTR, SHIPPED when the seller adds the tracking number and
 * DELIVERED when the customer taps Received. [UNKNOWN] is any status a newer server sends that this app does not know,
 * so one new value cannot fail a whole page of orders (see `coerceInputValues` in the network module).
 */
@Serializable
enum class OrderStatus(val label: String) {
    CONFIRMED("Confirmed"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled"),
    UNKNOWN("Updated");

    val isActive: Boolean get() = this != DELIVERED && this != CANCELLED
}

/** A saree as it was when the hold or order was made. */
@Serializable
data class OrderLine(
    val itemId: Long? = null,
    val title: String,
    val colour: String? = null,
    val unitPrice: Int,
    val quantity: Int,
)

@Serializable
data class AddressSnapshot(
    val name: String,
    val phone: String,
    val line1: String,
    val line2: String? = null,
    val city: String,
    val state: String,
    val pin: String,
)

/** Stock held for the customer until [expiresAt] while they pay the seller's UPI ID. */
@Serializable
data class Hold(
    val id: Long,
    val sellerId: Long,
    val shopName: String,
    val sellerUpiId: String? = null,
    val lines: List<OrderLine>,
    val itemsTotal: Int,
    val shippingFee: Int,
    val grandTotal: Int,
    val address: AddressSnapshot,
    val note: String? = null,
    val expiresAt: String,
)

/** The saved address an open payment hold should be delivered to instead. */
@Serializable
data class HoldAddressBody(val addressId: Long)

@Serializable
data class CheckoutBody(val addressId: Long, val note: String? = null, val sellerId: Long? = null)

/** The customer wanted [requested] but only [reserved] could be held (0: it could not be ordered). */
@Serializable
data class LineAdjustment(val itemId: Long, val title: String, val requested: Int, val reserved: Int)

/** [reason]: NOT_ACCEPTING_ORDERS, NOTHING_IN_STOCK or HOLD_EXISTS. */
@Serializable
data class SkippedSeller(val sellerId: Long, val shopName: String, val reason: String)

@Serializable
data class CheckoutResult(
    val holds: List<Hold> = emptyList(),
    val adjustments: List<LineAdjustment> = emptyList(),
    val skipped: List<SkippedSeller> = emptyList(),
)

@Serializable
data class StatusEntry(val status: OrderStatus = OrderStatus.UNKNOWN, val at: String)

/** The customer, as the seller sees them. Only the seller's view carries it. */
@Serializable
data class OrderParty(val name: String, val phone: String, val id: Long? = null, val photoVersion: Long? = null)

/** Both are required: the courier the parcel went with and the number the customer can track it by. */
@Serializable
data class ShipBody(val courier: String, val trackingNumber: String)

@Serializable
data class CancelBody(val reason: String? = null)

@Serializable
data class Order(
    val id: Long,
    val status: OrderStatus = OrderStatus.UNKNOWN,
    val sellerId: Long,
    val shopName: String,
    val sellerUpiId: String? = null,
    val lines: List<OrderLine>,
    val itemsTotal: Int,
    val shippingFee: Int,
    val grandTotal: Int,
    val address: AddressSnapshot,
    val note: String? = null,
    val paymentReference: String? = null,
    val hasProof: Boolean = false,
    val cancelReason: String? = null,
    val cancelledBy: String? = null,
    val courier: String? = null,
    val trackingNumber: String? = null,
    val unread: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val history: List<StatusEntry> = emptyList(),
    /** Who ordered; set only in the seller's view of their own orders. */
    val customer: OrderParty? = null,
)

@Serializable
data class OrderPage(val items: List<Order>, val page: Int, val pageSize: Int, val totalItems: Long, val totalPages: Int)

@Serializable
data class OrderSummary(
    val unread: Int = 0,
    val activeHolds: Int = 0,
    /** Seller only: confirmed orders still waiting for a tracking number. */
    val toShip: Int = 0,
    val shipped: Int = 0,
)
