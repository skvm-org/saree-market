package com.sareemarket.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRepositoryTest {

    private val api = FakeAuthApi()
    private val store = FakeSessionStore(session(Role.CUSTOMER))
    private val repo = AuthRepository(api, store, testJson)

    @Test
    fun changePhoneKeepsTheStoredSessionInStepAndTheToken() = runTest {
        assertTrue(repo.changePhone("9000000001", "password1").isSuccess)

        assertEquals("9000000001", store.saved?.phone)
        assertEquals("token-1", store.saved?.token)
        assertEquals("password1", api.lastPhoneChange?.currentPassword)
    }

    @Test
    fun aRefusedPhoneChangeLeavesTheSessionAlone() = runTest {
        api.changePhoneResult = { throw httpError(409, """{"code":"PHONE_TAKEN","message":"This phone number is already registered","fields":{}}""") }

        val error = repo.changePhone("9000000001", "password1").exceptionOrNull() as ApiFailure

        assertEquals("PHONE_TAKEN", error.code)
        assertEquals("9876543210", store.saved?.phone)
    }

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
