package com.sareemarket.app.ui

import com.sareemarket.app.data.AuthRepository
import com.sareemarket.app.data.FakeAuthApi
import com.sareemarket.app.data.FakeSessionStore
import com.sareemarket.app.data.Role
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.auth.RegisterField
import com.sareemarket.app.ui.auth.RegisterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private val api = FakeAuthApi()
    private val store = FakeSessionStore()
    private val vm = RegisterViewModel(AuthRepository(api, store, testJson))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun fillCommon() {
        vm.onChange(RegisterField.NAME, " Lakshmi ")
        vm.onChange(RegisterField.PHONE, "9876543211")
        vm.onChange(RegisterField.PASSWORD, "password1")
    }

    private fun fillShop() {
        vm.onChange(RegisterField.SHOP_NAME, " Lakshmi Silks ")
        vm.onChange(RegisterField.MARKET_LOCATION, "Dindigul")
        vm.onChange(RegisterField.CONTACT_PHONE, "9876543211")
    }

    @Test
    fun sellerMustFillInShopDetailsBeforeAnyRequestIsSent() {
        fillCommon()

        vm.submit()

        assertEquals(setOf("shopName", "marketLocation", "contactPhone"), vm.state.value.fieldErrors.keys)
        assertNull(api.lastRegister)
    }

    @Test
    fun resellerNeedsNoShopDetails() {
        vm.onRoleChange(Role.CUSTOMER)
        fillCommon()

        vm.submit()

        assertEquals(Role.CUSTOMER, api.lastRegister?.role)
        assertNull(api.lastRegister?.shopName)
        assertNotNull(store.saved)
    }

    @Test
    fun sellerRequestIsTrimmedAndBlankWhatsappIsOmitted() {
        fillCommon()
        fillShop()

        vm.submit()

        val sent = api.lastRegister!!
        assertEquals("Lakshmi", sent.name)
        assertEquals("Lakshmi Silks", sent.shopName)
        assertNull(sent.whatsappNumber)
    }

    @Test
    fun shortPasswordAndBadPhoneAreCaughtOnTheDevice() {
        vm.onRoleChange(Role.CUSTOMER)
        vm.onChange(RegisterField.NAME, "Priya")
        vm.onChange(RegisterField.PHONE, "12345")
        vm.onChange(RegisterField.PASSWORD, "short")

        vm.submit()

        assertEquals(setOf("phone", "password"), vm.state.value.fieldErrors.keys)
        assertNull(api.lastRegister)
    }

    @Test
    fun switchingRoleClearsOldErrors() {
        fillCommon()
        vm.submit()
        assertTrue(vm.state.value.fieldErrors.isNotEmpty())

        vm.onRoleChange(Role.CUSTOMER)

        assertTrue(vm.state.value.fieldErrors.isEmpty())
    }

    @Test
    fun takenPhoneIsShownOnThePhoneField() {
        api.registerResult = {
            throw httpError(409, """{"code":"PHONE_TAKEN","message":"This phone number is already registered"}""")
        }
        vm.onRoleChange(Role.CUSTOMER)
        fillCommon()

        vm.submit()

        assertEquals("This phone number is already registered", vm.state.value.fieldErrors["phone"])
        assertNull(store.saved)
    }

    @Test
    fun serverFieldErrorsAreShownOnTheirFields() {
        api.registerResult = {
            throw httpError(400, """{"code":"VALIDATION_ERROR","message":"Some fields are invalid","fields":{"name":"size must be at most 100"}}""")
        }
        vm.onRoleChange(Role.CUSTOMER)
        fillCommon()

        vm.submit()

        assertEquals("size must be at most 100", vm.state.value.fieldErrors["name"])
    }

    @Test
    fun editingAFieldClearsItsError() {
        fillCommon()
        vm.submit()
        assertTrue("shopName" in vm.state.value.fieldErrors)

        vm.onChange(RegisterField.SHOP_NAME, "Lakshmi Silks")

        assertTrue("shopName" !in vm.state.value.fieldErrors)
    }
}
