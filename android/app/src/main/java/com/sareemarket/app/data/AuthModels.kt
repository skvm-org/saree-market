package com.sareemarket.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class Role { SELLER, RESELLER, ADMIN }

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
)

/** The backend's standard error body: `{ "code", "message", "fields" }`. */
@Serializable
data class ApiError(val code: String, val message: String, val fields: Map<String, String> = emptyMap())

/** What the app keeps between launches. */
data class Session(val token: String, val userId: Long, val name: String, val phone: String, val role: Role)
