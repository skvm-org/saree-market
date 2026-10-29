package com.sareemarket.app.ui

import com.sareemarket.app.data.AuthRepository
import com.sareemarket.app.data.FakeAuthApi
import com.sareemarket.app.data.FakeSessionStore
import com.sareemarket.app.data.Role
import com.sareemarket.app.data.ShopDetails
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.session
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.profile.ChangePasswordViewModel
import com.sareemarket.app.ui.profile.ProfileViewModel
import com.sareemarket.app.ui.profile.ShopDetailsViewModel
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
class ProfileViewModelsTest {

    private val api = FakeAuthApi()
    private val store = FakeSessionStore(session(Role.SELLER))
    private val repo = AuthRepository(api, store, testJson)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun profileStartsFromTheStoredSession() {
        val vm = ProfileViewModel(repo)

        assertEquals("Priya", vm.state.value.name)
        assertEquals("9876543210", vm.state.value.phone)
        assertEquals(Role.SELLER, vm.state.value.role)
    }

    @Test
    fun blankNameIsRefusedWithoutCallingTheServer() {
        val vm = ProfileViewModel(repo)
        vm.onNameChange("   ")

        vm.save()

        assertNotNull(vm.state.value.error)
        assertEquals("Priya", store.saved?.name)
    }

    @Test
    fun savingTheNameUpdatesTheSessionAndShowsSaved() {
        val vm = ProfileViewModel(repo)
        vm.onNameChange("  Meena ")

        vm.save()

        assertTrue(vm.state.value.saved)
        assertEquals("Meena", store.saved?.name)
    }

    @Test
    fun editingAfterSavingClearsTheSavedMessage() {
        val vm = ProfileViewModel(repo)
        vm.save()

        vm.onNameChange("Priyanka")

        assertFalse(vm.state.value.saved)
    }

    @Test
    fun passwordFormChecksLengthAndMatchBeforeCallingTheServer() {
        val vm = ChangePasswordViewModel(repo)
        vm.onCurrent("password1")
        vm.onNew("short")
        vm.onConfirm("short")
        vm.submit()
        assertEquals("Use at least 8 characters", vm.state.value.fieldErrors["new"])

        vm.onNew("newpassword2")
        vm.onConfirm("different-one")
        vm.submit()
        assertEquals("The new passwords do not match", vm.state.value.fieldErrors["confirm"])
        assertNull(api.lastPasswordChange)
    }

    @Test
    fun passwordChangeSucceedsAndClearsTheForm() {
        val vm = ChangePasswordViewModel(repo)
        vm.onCurrent("password1")
        vm.onNew("newpassword2")
        vm.onConfirm("newpassword2")

        vm.submit()

        assertTrue(vm.state.value.done)
        assertEquals("", vm.state.value.new)
        assertEquals("newpassword2", api.lastPasswordChange?.newPassword)
    }

    @Test
    fun wrongCurrentPasswordShowsTheServerMessageAndKeepsTheForm() {
        api.changePasswordResult = { throw httpError(400, """{"code":"WRONG_PASSWORD","message":"Current password is wrong","fields":{}}""") }
        val vm = ChangePasswordViewModel(repo)
        vm.onCurrent("nope")
        vm.onNew("newpassword2")
        vm.onConfirm("newpassword2")

        vm.submit()

        assertEquals("Current password is wrong", vm.state.value.fieldErrors["current"])
        assertFalse(vm.state.value.done)
        assertEquals("newpassword2", vm.state.value.new)
    }

    @Test
    fun shopDetailsLoadIntoTheForm() {
        val vm = ShopDetailsViewModel(repo)

        assertFalse(vm.state.value.loading)
        assertEquals("Lakshmi Silks", vm.state.value.shopName)
        assertEquals("", vm.state.value.whatsapp)
    }

    @Test
    fun shopDetailsValidateThePhonesAndSendBlankWhatsappAsNull() {
        val vm = ShopDetailsViewModel(repo)
        vm.onContactPhone("12ab34")
        vm.save()
        assertEquals("Enter a 10-digit mobile number", vm.state.value.fieldErrors["contactPhone"])

        vm.onContactPhone("9111111111")
        vm.onWhatsapp("123")
        vm.save()
        assertNotNull(vm.state.value.fieldErrors["whatsapp"])

        vm.onWhatsapp("")
        vm.onShopName(" New Silks ")
        vm.save()
        assertTrue(vm.state.value.saved)
        assertEquals(ShopDetails("New Silks", "Dindigul", "9111111111", null, upiId = "", shippingFee = 0), api.lastShopUpdate)
    }

    @Test
    fun shopDetailsLoadTheUpiIdAndShippingFee() {
        api.shopResult = { ShopDetails("Lakshmi Silks", "Dindigul", "9876500001", null, "lakshmi@okaxis", 60) }

        val vm = ShopDetailsViewModel(repo)

        assertEquals("lakshmi@okaxis", vm.state.value.upiId)
        assertEquals("60", vm.state.value.shippingFee)
        assertFalse(vm.state.value.dirty)
    }

    @Test
    fun savingSendsTheUpiIdAndShippingFee() {
        val vm = ShopDetailsViewModel(repo)
        vm.onUpiId(" lakshmi.silks@okaxis ")
        vm.onShippingFee("60")

        vm.save()

        assertTrue(vm.state.value.saved)
        assertEquals("lakshmi.silks@okaxis", api.lastShopUpdate?.upiId)
        assertEquals(60, api.lastShopUpdate?.shippingFee)
    }

    @Test
    fun anEmptyUpiIdIsSentEmptySoTheServerClearsIt() {
        api.shopResult = { ShopDetails("Lakshmi Silks", "Dindigul", "9876500001", null, "old@ybl", 40) }
        val vm = ShopDetailsViewModel(repo)
        vm.onUpiId("")

        vm.save()

        assertEquals("", api.lastShopUpdate?.upiId)
        assertEquals(40, api.lastShopUpdate?.shippingFee)
    }

    @Test
    fun aBadUpiIdIsRejectedWithoutCallingTheServer() {
        for (bad in listOf("abc", "a@b", "x y@okaxis", "@okaxis")) {
            api.lastShopUpdate = null
            val vm = ShopDetailsViewModel(repo)
            vm.onUpiId(bad)

            vm.save()

            assertNotNull("$bad should be refused", vm.state.value.fieldErrors["upiId"])
            assertEquals(null, api.lastShopUpdate)
        }
    }

    @Test
    fun aShippingFeeAboveTenThousandIsRejected() {
        val vm = ShopDetailsViewModel(repo)
        vm.onShippingFee("10001")

        vm.save()

        assertNotNull(vm.state.value.fieldErrors["shippingFee"])
        assertEquals("10000", ShopDetailsViewModel(repo).also { it.onShippingFee("10000") }.state.value.shippingFee)
    }

    @Test
    fun anEmptyShippingFeeMeansFree() {
        val vm = ShopDetailsViewModel(repo)
        vm.onShippingFee("")

        vm.save()

        assertEquals(0, api.lastShopUpdate?.shippingFee)
    }

    @Test
    fun changingOnlyTheUpiIdMakesTheFormDirty() {
        val vm = ShopDetailsViewModel(repo)
        assertFalse(vm.state.value.dirty)

        vm.onUpiId("shop@ybl")

        assertTrue(vm.state.value.dirty)
    }

    @Test
    fun failedShopLoadShowsTheErrorAndRetryReloads() {
        var fail = true
        api.shopResult = { if (fail) throw httpError(500) else ShopDetails("Lakshmi Silks", "Dindigul", "9876500001", null) }
        val vm = ShopDetailsViewModel(repo)
        assertNotNull(vm.state.value.error)

        fail = false
        vm.load()

        assertNull(vm.state.value.error)
        assertEquals("Lakshmi Silks", vm.state.value.shopName)
    }

    @Test
    fun leavingAFieldChecksJustThatField() {
        val vm = ChangePasswordViewModel(repo)
        vm.onNew("short")

        vm.onBlur("new")
        assertEquals("Use at least 8 characters", vm.state.value.fieldErrors["new"])
        assertNull(vm.state.value.fieldErrors["current"]) // untouched fields stay quiet

        vm.onNew("longenough1")
        vm.onBlur("new")
        assertNull(vm.state.value.fieldErrors["new"])
    }

    @Test
    fun leavingTheRepeatFieldEmptyIsNotYetAMismatch() {
        val vm = ChangePasswordViewModel(repo)
        vm.onNew("longenough1")

        vm.onBlur("confirm")
        assertNull(vm.state.value.fieldErrors["confirm"])

        vm.onConfirm("other")
        vm.onBlur("confirm")
        assertEquals("The new passwords do not match", vm.state.value.fieldErrors["confirm"])
    }

    @Test
    fun shopFieldsAreCheckedWhenLeft() {
        val vm = ShopDetailsViewModel(repo)
        vm.onShopName("")
        vm.onBlur("shopName")
        assertEquals("Enter your shop name", vm.state.value.fieldErrors["shopName"])
        vm.onShopName("Silks")
        assertNull(vm.state.value.fieldErrors["shopName"]) // typing clears the message
    }

    @Test
    fun blankNameIsFlaggedWhenLeft() {
        val vm = ProfileViewModel(repo)
        vm.onNameChange("")
        vm.onBlur()
        assertEquals("Enter your name", vm.state.value.error)
    }
}
