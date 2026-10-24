package com.sareemarket.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Adds the JWT to every request, and logs out when the server says the token is no longer valid. */
class AuthInterceptor(private val sessionStore: SessionStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // OkHttp calls this on its own thread, so blocking here does not touch the UI.
        val token = runBlocking { sessionStore.session.first()?.token }
        val request = if (token == null) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        }
        val response = chain.proceed(request)
        if (token != null && response.code == 401) {
            runBlocking { sessionStore.clear() }
        }
        return response
    }
}
