package com.sareemarket.app.data

import kotlinx.serialization.Serializable

/** A saved delivery address. */
@Serializable
data class Address(
    val id: Long,
    val name: String,
    val phone: String,
    val line1: String,
    val line2: String? = null,
    val city: String,
    val state: String,
    val pin: String,
    val isDefault: Boolean = false,
)

@Serializable
data class AddressForm(
    val name: String,
    val phone: String,
    val line1: String,
    val line2: String? = null,
    val city: String,
    val state: String,
    val pin: String,
    val makeDefault: Boolean? = null,
)

/**
 * One saree in the cart. [maxQuantity] is how many the seller has now; [lineTotal] counts at most that many and is 0
 * when the saree can't be ordered ([inStock] false).
 */
@Serializable
data class CartLine(
    val itemId: Long,
    val collectionTitle: String,
    val colour: String? = null,
    val unitPrice: Int,
    val quantity: Int,
    val maxQuantity: Int,
    val inStock: Boolean,
    val lineTotal: Int,
)

/** The cart lines of one seller: they are paid for, and shipped, together. */
@Serializable
data class CartSeller(
    val sellerId: Long,
    val shopName: String,
    val acceptsOrders: Boolean,
    val shippingFee: Int,
    val lines: List<CartLine>,
    val itemsTotal: Int,
    val grandTotal: Int,
)

@Serializable
data class Cart(val sellers: List<CartSeller> = emptyList(), val lineCount: Int = 0)

@Serializable
data class QuantityBody(val quantity: Int)
