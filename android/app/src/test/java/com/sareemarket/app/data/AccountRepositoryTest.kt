package com.sareemarket.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRepositoryTest {

    private val api = FakeAuthApi()
    private val store = FakeSessionStore(session(Role.RESELLER))
    private val repo = AuthRepository(api, store, testJson)

    @Test
    fun updateNameKeepsTheStoredSessionInStep() = runTest {
        assertTrue(repo.updateName("Meena").isSuccess)

        assertEquals("Meena", store.saved?.name)
        assertEquals("token-1", store.saved?.token)
    }

    @Test
    fun failedUpdateNameLeavesTheSessionAlone() = runTest {
        api.updateNameResult = { throw networkError() }

        val error = repo.updateName("Meena").exceptionOrNull() as ApiFailure

        assertEquals("NETWORK", error.code)
        assertEquals("Priya", store.saved?.name)
    }

    @Test
    fun wrongCurrentPasswordSurfacesTheServerMessage() = runTest {
        api.changePasswordResult = {
            throw httpError(400, """{"code":"WRONG_PASSWORD","message":"Current password is wrong","fields":{"currentPassword":"is wrong"}}""")
        }

        val error = repo.changePassword("nope", "newpassword2").exceptionOrNull() as ApiFailure

        assertEquals("WRONG_PASSWORD", error.code)
        assertEquals("Current password is wrong", error.message)
        assertEquals(ChangePasswordRequest("nope", "newpassword2"), api.lastPasswordChange)
    }

    @Test
    fun shopDetailsRoundTrip() = runTest {
        assertEquals("Lakshmi Silks", repo.shopDetails().getOrThrow().shopName)

        val edited = ShopDetails("New Silks", "Erode", "9111111111", "9222222222")
        assertEquals(edited, repo.updateShopDetails(edited).getOrThrow())
        assertEquals(edited, api.lastShopUpdate)
    }
}
