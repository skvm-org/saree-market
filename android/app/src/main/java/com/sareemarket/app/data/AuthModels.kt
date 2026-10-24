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

/** The backend's standard error body: `{ "code", "message", "fields" }`. */
@Serializable
data class ApiError(val code: String, val message: String, val fields: Map<String, String> = emptyMap())

/** What the app keeps between launches. */
data class Session(val token: String, val userId: Long, val name: String, val phone: String, val role: Role)

/** An auth call failed; [message] is safe to show the user. */
class AuthError(val code: String, message: String, val fields: Map<String, String> = emptyMap()) : Exception(message)
