package com.sareemarket.app.ui

import com.sareemarket.app.data.AuthRepository
import com.sareemarket.app.data.FakeAuthApi
import com.sareemarket.app.data.FakeSessionStore
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.networkError
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.auth.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val api = FakeAuthApi()
    private val store = FakeSessionStore()
    private val vm = LoginViewModel(AuthRepository(api, store, testJson))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun phoneKeepsOnlyTenDigits() {
        vm.onPhoneChange("98a421-44521 99")

        assertEquals("9842144521", vm.state.value.phone)
    }

    @Test
    fun submitWithIncompleteFormShowsAMessageAndSkipsTheNetwork() {
        vm.onPhoneChange("98421")
        vm.onPasswordChange("password1")

        vm.submit()

        assertNotNull(vm.state.value.error)
        assertEquals(0, api.loginCalls)
    }

    @Test
    fun successfulLoginSavesTheSessionAndStopsLoading() {
        vm.onPhoneChange("9876543210")
        vm.onPasswordChange("password1")

        vm.submit()

        assertNotNull(store.saved)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun wrongPasswordShowsTheServerMessage() {
        api.loginResult = {
            throw httpError(401, """{"code":"INVALID_CREDENTIALS","message":"Wrong phone number or password"}""")
        }
        vm.onPhoneChange("9876543210")
        vm.onPasswordChange("bad-password")

        vm.submit()

        assertEquals("Wrong phone number or password", vm.state.value.error)
        assertFalse(vm.state.value.loading)
        assertNull(store.saved)
    }

    @Test
    fun offlineShowsAConnectionMessage() {
        api.loginResult = { throw networkError() }
        vm.onPhoneChange("9876543210")
        vm.onPasswordChange("password1")

        vm.submit()

        assertEquals(
            "Can't reach the server. Check your connection and try again.",
            vm.state.value.error,
        )
    }

    @Test
    fun editingAFieldClearsTheError() {
        vm.submit()
        assertNotNull(vm.state.value.error)

        vm.onPasswordChange("x")

        assertNull(vm.state.value.error)
    }
}
