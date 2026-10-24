package com.sareemarket.app.data

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

val testJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

val priya = UserDto(id = 7, name = "Priya", phone = "9876543210", role = Role.RESELLER)

class FakeSessionStore(initial: Session? = null) : SessionStore {
    private val current = MutableStateFlow(initial)
    override val session: Flow<Session?> = current
    var saved: Session? = initial
        private set

    override suspend fun save(session: Session) {
        saved = session
        current.value = session
    }

    override suspend fun clear() {
        saved = null
        current.value = null
    }
}

class FakeAuthApi : AuthApi {
    var loginResult: () -> AuthResponse = { AuthResponse("token-1", priya) }
    var registerResult: () -> AuthResponse = { AuthResponse("token-2", priya) }
    var meResult: () -> UserDto = { priya }
    var lastRegister: RegisterRequest? = null
    var loginCalls = 0

    override suspend fun login(request: LoginRequest): AuthResponse {
        loginCalls++
        return loginResult()
    }

    override suspend fun register(request: RegisterRequest): AuthResponse {
        lastRegister = request
        return registerResult()
    }

    override suspend fun me(): UserDto = meResult()
}

fun httpError(code: Int, body: String = ""): HttpException =
    HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))

fun networkError(): IOException = IOException("no route to host")

fun session(role: Role = Role.RESELLER) = Session("token-1", 7, "Priya", "9876543210", role)
