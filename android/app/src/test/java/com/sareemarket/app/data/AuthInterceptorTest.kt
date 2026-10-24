package com.sareemarket.app.data

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun call(store: FakeSessionStore, status: Int = 200): String? {
        server.enqueue(MockResponse.Builder().code(status).build())
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(store)).build()
        client.newCall(Request.Builder().url(server.url("/api/v1/anything")).build()).execute().close()
        return server.takeRequest().headers["Authorization"]
    }

    @Test
    fun addsTheBearerTokenWhenLoggedIn() {
        assertEquals("Bearer token-1", call(FakeSessionStore(session())))
    }

    @Test
    fun sendsNoAuthorizationHeaderWhenLoggedOut() {
        assertNull(call(FakeSessionStore()))
    }

    @Test
    fun logsOutWhenTheServerRejectsTheToken() {
        val store = FakeSessionStore(session())

        call(store, status = 401)

        assertNull(store.saved)
    }

    @Test
    fun otherErrorsKeepTheSession() {
        val store = FakeSessionStore(session())

        call(store, status = 500)

        assertNotNull(store.saved)
    }
}
