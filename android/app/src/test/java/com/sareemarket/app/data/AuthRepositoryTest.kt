package com.sareemarket.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    private val api = FakeAuthApi()
    private val store = FakeSessionStore()
    private val repo = AuthRepository(api, store, testJson)

    private fun authError(result: Result<Unit>) = result.exceptionOrNull() as AuthError

    @Test
    fun loginSavesTheSession() = runTest {
        assertTrue(repo.login("9876543210", "password1").isSuccess)

        assertEquals(Session("token-1", 7, "Priya", "9876543210", Role.RESELLER), store.saved)
    }

    @Test
    fun registerSendsSellerFieldsAndSavesTheSession() = runTest {
        val request = RegisterRequest(
            phone = "9876543211", name = "Lakshmi", password = "password1", role = Role.SELLER,
            shopName = "Lakshmi Silks", marketLocation = "Dindigul", contactPhone = "9876543211",
        )

        assertTrue(repo.register(request).isSuccess)

        assertEquals(request, api.lastRegister)
        assertEquals("token-2", store.saved?.token)
    }

    @Test
    fun wrongPasswordSurfacesTheServerMessageAndSavesNothing() = runTest {
        val body = """{"code":"INVALID_CREDENTIALS","message":"Wrong phone number or password","fields":{}}"""
        api.loginResult = { throw httpError(401, body) }

        val error = authError(repo.login("9876543210", "bad-password"))

        assertEquals("INVALID_CREDENTIALS", error.code)
        assertEquals("Wrong phone number or password", error.message)
        assertNull(store.saved)
    }

    @Test
    fun validationErrorKeepsPerFieldMessages() = runTest {
        val body = """{"code":"VALIDATION_ERROR","message":"Some fields are invalid","fields":{"shopName":"is required for sellers"}}"""
        api.registerResult = { throw httpError(400, body) }

        val error = authError(repo.register(RegisterRequest("9876543211", "L", "password1", Role.SELLER)))

        assertEquals(mapOf("shopName" to "is required for sellers"), error.fields)
    }

    @Test
    fun unparseableServerErrorGetsAGenericMessage() = runTest {
        api.loginResult = { throw httpError(500, "<html>oops</html>") }

        val error = authError(repo.login("9876543210", "password1"))

        assertEquals("SERVER", error.code)
        assertTrue(error.message!!.contains("500"))
    }

    @Test
    fun offlineGivesANetworkError() = runTest {
        api.loginResult = { throw networkError() }

        assertEquals("NETWORK", authError(repo.login("9876543210", "password1")).code)
    }

    @Test
    fun logoutClearsTheSession() = runTest {
        store.save(session())

        repo.logout()

        assertNull(store.saved)
    }

    @Test
    fun refreshKeepsTheSessionWhileOffline() = runTest {
        store.save(session())
        api.meResult = { throw networkError() }

        repo.refreshSession()

        assertNotNull(store.saved)
    }

    @Test
    fun refreshLogsOutADisabledAccount() = runTest {
        store.save(session())
        api.meResult = { throw httpError(403) }

        repo.refreshSession()

        assertNull(store.saved)
    }
}
