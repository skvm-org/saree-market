package com.sareemarket.app.data

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import retrofit2.HttpException

@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val sessionStore: SessionStore,
    private val json: Json,
) {
    val session: Flow<Session?> = sessionStore.session

    suspend fun login(phone: String, password: String): Result<Unit> =
        authenticate { api.login(LoginRequest(phone, password)) }

    suspend fun register(request: RegisterRequest): Result<Unit> = authenticate { api.register(request) }

    suspend fun logout() = sessionStore.clear()

    /** Checks the saved token is still accepted. Network trouble keeps the session: the user may just be offline. */
    suspend fun refreshSession() {
        try {
            api.me()
        } catch (e: HttpException) {
            // 401 is handled by AuthInterceptor; 403 means the account was disabled.
            if (e.code() == 403) sessionStore.clear()
        } catch (e: IOException) {
            // offline: keep the session
        }
    }

    private suspend fun authenticate(call: suspend () -> AuthResponse): Result<Unit> = try {
        val response = call()
        val user = response.user
        sessionStore.save(Session(response.token, user.id, user.name, user.phone, user.role))
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Result.failure(toAuthError(e))
    } catch (e: IOException) {
        Result.failure(AuthError("NETWORK", "Can't reach the server. Check your connection and try again."))
    }

    private fun toAuthError(e: HttpException): AuthError {
        val body = e.response()?.errorBody()?.string()
        val error = body?.let { runCatching { json.decodeFromString<ApiError>(it) }.getOrNull() }
        return if (error != null) {
            AuthError(error.code, error.message, error.fields)
        } else {
            AuthError("SERVER", "Something went wrong (${e.code()}). Please try again.")
        }
    }
}
