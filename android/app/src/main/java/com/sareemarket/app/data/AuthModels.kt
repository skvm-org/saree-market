package com.sareemarket.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class Role { SELLER, CUSTOMER, ADMIN }

@Serializable
data class UserDto(val id: Long, val name: String, val phone: String, val role: Role)

@Serializable
data class AuthResponse(val token: String, val user: UserDto)

@Serializable
data class LoginRequest(val phone: String, val password: String)

@Serializable
data class RegisterRequest(
    val phone: String,
    val name: String,
    val password: String,
    val role: Role,
    val shopName: String? = null,
    val marketLocation: String? = null,
    val contactPhone: String? = null,
    val whatsappNumber: String? = null,
)

@Serializable
data class UpdateNameRequest(val name: String)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

/** A seller's own shop details, as read and as saved. */
@Serializable
data class ShopDetails(
    val shopName: String,
    val marketLocation: String,
    val contactPhone: String,
    val whatsappNumber: String? = null,
    /** Where customers pay this shop; empty clears it. Without one nobody can order from the shop. */
    val upiId: String? = null,
    /**
     * Flat shipping fee added to each order from the shop, in whole rupees. Always written, even at 0: a missing field means
     * "keep the current fee" to the server, so a seller could never switch to free shipping.
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault
    val shippingFee: Int = 0,
)

/** The backend's standard error body: `{ "code", "message", "fields" }`. */
@Serializable
data class ApiError(val code: String, val message: String, val fields: Map<String, String> = emptyMap())

/** What the app keeps between launches. */
data class Session(val token: String, val userId: Long, val name: String, val phone: String, val role: Role)
