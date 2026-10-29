package com.sareemarket.app.data

import kotlinx.serialization.Serializable

/** PAYMENT_REJECTED: the seller could not find the payment, and the customer has two hours to send a correct UTR. */
@Serializable
enum class OrderStatus(val label: String) {
    PAYMENT_SUBMITTED("Waiting for seller"),
    PAYMENT_REJECTED("Payment not found"),
    CONFIRMED("Confirmed"),
    PACKED("Packed"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled");

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
data class StatusEntry(val status: OrderStatus, val at: String)

/** The customer, as the seller sees them. Only the seller's view carries it. */
@Serializable
data class OrderParty(val name: String, val phone: String)

@Serializable
data class RejectBody(val reason: String)

@Serializable
data class ShipBody(val courier: String? = null, val trackingNumber: String? = null)

@Serializable
data class CancelBody(val reason: String? = null)

@Serializable
data class Order(
    val id: Long,
    val status: OrderStatus,
    val sellerId: Long,
    val shopName: String,
    val sellerUpiId: String? = null,
    val customer: OrderParty? = null,
    val lines: List<OrderLine>,
    val itemsTotal: Int,
    val shippingFee: Int,
    val grandTotal: Int,
    val address: AddressSnapshot,
    val note: String? = null,
    val paymentReference: String? = null,
    val hasProof: Boolean = false,
    val rejectionReason: String? = null,
    /** Set while PAYMENT_REJECTED: the customer must send a correct UTR before this instant. */
    val paymentDeadline: String? = null,
    val cancelReason: String? = null,
    val cancelledBy: String? = null,
    val courier: String? = null,
    val trackingNumber: String? = null,
    val unread: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val history: List<StatusEntry> = emptyList(),
)

@Serializable
data class OrderPage(val items: List<Order>, val page: Int, val pageSize: Int, val totalItems: Long, val totalPages: Int)

@Serializable
data class OrderSummary(
    val unread: Int = 0,
    val activeHolds: Int = 0,
    val toConfirm: Int = 0,
    val toPack: Int = 0,
    val toShip: Int = 0,
    val shipped: Int = 0,
)
