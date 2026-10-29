package com.sareemarket.app.ui

import com.sareemarket.app.data.AddressRepository
import com.sareemarket.app.data.FakeShoppingApi
import com.sareemarket.app.data.homeAddress
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.reseller.AddressesViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddressesViewModelTest {

    private val api = FakeShoppingApi()
    private fun vm() = AddressesViewModel(AddressRepository(api, testJson))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun AddressesViewModel.fillValid() {
        onField("name", "Shop")
        onField("phone", "9876543210")
        onField("line1", "5 Market Street")
        onField("city", "Chennai")
        onField("state", "Tamil Nadu")
        onField("pin", "600001")
    }

    @Test
    fun loadShowsTheSavedAddresses() {
        val vm = vm()

        assertEquals(listOf(homeAddress), vm.state.value.items)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun invalidFieldsAreRejectedWithoutCallingTheServer() {
        val vm = vm()
        vm.startAdd()
        vm.onField("phone", "12345")
        vm.onField("pin", "12")

        vm.save()

        val errors = vm.state.value.fieldErrors
        assertEquals(setOf("name", "phone", "line1", "city", "state", "pin"), errors.keys)
        assertTrue(api.calls.none { it.startsWith("createAddress") })
    }

    @Test
    fun aValidFormIsCreatedThenTheListReloadsAndTheFormCloses() {
        val vm = vm()
        vm.startAdd()
        vm.fillValid()

        vm.save()

        assertTrue("createAddress Shop" in api.calls)
        assertNull(vm.state.value.form)
        assertFalse(vm.state.value.saving)
        assertEquals(2, api.calls.count { it == "addresses" })
    }

    @Test
    fun serverFieldErrorsAreShownAndTheFormStaysOpen() {
        api.onAddress = { throw httpError(400, """{"code":"VALIDATION_ERROR","message":"Some fields are invalid","fields":{"pin":"must be a 6-digit PIN code"}}""") }
        val vm = vm()
        vm.startAdd()
        vm.fillValid()

        vm.save()

        assertEquals("must be a 6-digit PIN code", vm.state.value.fieldErrors["pin"])
        assertNotNull(vm.state.value.form)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun theElevenhAddressShowsTheServersMessage() {
        api.onAddress = { throw httpError(409, """{"code":"ADDRESS_LIMIT","message":"You can save up to 10 addresses; delete one first"}""") }
        val vm = vm()
        vm.startAdd()
        vm.fillValid()

        vm.save()

        assertEquals("You can save up to 10 addresses; delete one first", vm.state.value.formError)
        assertNotNull(vm.state.value.form)
    }

    @Test
    fun editPrefillsTheFormAndUpdates() {
        val vm = vm()

        vm.startEdit(homeAddress)
        assertEquals("Home", vm.state.value.form?.name)
        vm.onField("name", "Mum's house")
        vm.save()

        assertTrue("updateAddress 1 Mum's house" in api.calls)
        assertNull(vm.state.value.form)
    }

    @Test
    fun deleteCallsTheApiAndReloads() {
        val vm = vm()

        vm.delete(1)

        assertTrue("deleteAddress 1" in api.calls)
        assertEquals(2, api.calls.count { it == "addresses" })
    }

    @Test
    fun cancellingTheFormDiscardsIt() {
        val vm = vm()
        vm.startAdd()
        vm.onField("name", "Draft")

        vm.cancelForm()

        assertNull(vm.state.value.form)
    }
}
