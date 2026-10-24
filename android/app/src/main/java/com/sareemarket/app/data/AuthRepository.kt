package com.sareemarket.app.data

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
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

    private suspend fun authenticate(call: suspend () -> AuthResponse): Result<Unit> =
        apiCall(json, call).map { response ->
            val user = response.user
            sessionStore.save(Session(response.token, user.id, user.name, user.phone, user.role))
        }
}
