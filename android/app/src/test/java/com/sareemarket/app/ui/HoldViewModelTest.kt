package com.sareemarket.app.ui

import androidx.lifecycle.SavedStateHandle
import com.sareemarket.app.data.AddressRepository
import com.sareemarket.app.data.AddressSnapshot
import com.sareemarket.app.data.CartRepository
import com.sareemarket.app.data.FakeOrderApi
import com.sareemarket.app.data.FakePhotoSource
import com.sareemarket.app.data.FakeShoppingApi
import com.sareemarket.app.data.OrderRepository
import com.sareemarket.app.data.hold
import com.sareemarket.app.data.homeAddress
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.order
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.reseller.HoldViewModel
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
class HoldViewModelTest {

    private val orderApi = FakeOrderApi()
    private val shopping = FakeShoppingApi()

    init {
        orderApi.holds = { listOf(hold(3)) }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(id: Long = 3) = HoldViewModel(
        SavedStateHandle(mapOf("id" to id)),
        OrderRepository(orderApi, FakePhotoSource(mapOf("content://proof/1" to ByteArray(10))), testJson),
        CartRepository(shopping, testJson),
        AddressRepository(shopping, testJson),
    )

    private val office = homeAddress.copy(id = 7, name = "Office", line1 = "5 Market Street", city = "Chennai", pin = "600001", isDefault = false)

    @Test
    fun loadsTheHoldByItsId() {
        orderApi.holds = { listOf(hold(2), hold(3)) }

        val vm = vm(3)

        assertEquals(3L, vm.state.value.hold?.id)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun aMissingHoldMeansTheWindowEnded() {
        orderApi.holds = { listOf(hold(2)) }

        val vm = vm(3)

        assertNull(vm.state.value.hold)
        assertTrue(vm.state.value.expired)
        assertTrue(vm.state.value.error!!.contains("check My orders"))
    }

    @Test
    fun theUtrKeepsOnlyDigitsAndTwelveCharacters() {
        val vm = vm()

        vm.onUtr("41-23 4567 8901 2345")

        assertEquals("412345678901", vm.state.value.utr)
    }

    @Test
    fun cannotPayWithAShortUtr() {
        val vm = vm()

        vm.onUtr("41234567890")
        assertFalse(vm.state.value.canPay)
        vm.onUtr("412345678901")
        assertTrue(vm.state.value.canPay)
    }

    @Test
    fun payingOpensTheNewOrder() {
        orderApi.onPay = { _, _, _ -> order(77) }
        val vm = vm()
        vm.onUtr("412345678901")

        vm.pay()

        assertEquals(77L, vm.state.value.paidOrderId)
        assertTrue("payHold 3 false" in orderApi.calls)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun aChosenScreenshotIsSentWithThePayment() {
        val vm = vm()
        vm.onUtr("412345678901")
        vm.onProof("content://proof/1")

        vm.pay()

        assertTrue("payHold 3 true" in orderApi.calls)
    }

    @Test
    fun aRejectedPaymentShowsTheServersMessage() {
        orderApi.onPay = { _, _, _ -> throw httpError(409, """{"code":"UTR_IN_USE","message":"That UTR is already used on another order"}""") }
        val vm = vm()
        vm.onUtr("412345678901")

        vm.pay()

        assertEquals("That UTR is already used on another order", vm.state.value.payError)
        assertNull(vm.state.value.paidOrderId)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun anExpiredHoldAnswerEndsTheWindow() {
        orderApi.onPay = { _, _, _ -> throw httpError(409, """{"code":"HOLD_EXPIRED","message":"This payment window has ended; contact the seller if you already paid"}""") }
        val vm = vm()
        vm.onUtr("412345678901")

        vm.pay()

        assertTrue(vm.state.value.expired)
        assertFalse(vm.state.value.canPay)
    }

    @Test
    fun markingExpiredDisablesPaying() {
        val vm = vm()
        vm.onUtr("412345678901")

        vm.markExpired()

        assertFalse(vm.state.value.canPay)
    }

    @Test
    fun cancellingReleasesTheHoldAndRefreshesTheCart() {
        val vm = vm()

        vm.cancel()

        assertTrue("cancelHold 3" in orderApi.calls)
        assertTrue("cart" in shopping.calls)
        assertTrue(vm.state.value.closed)
    }

    @Test
    fun openingTheAddressSheetLoadsTheSavedAddressesAndSelectsTheOneTheHoldGoesTo() {
        shopping.addressList = { listOf(office, homeAddress) }
        val vm = vm()

        vm.openAddressSheet()

        val sheet = vm.state.value.addressSheet!!
        assertEquals(listOf(7L, homeAddress.id), sheet.addresses.map { it.id })
        assertEquals(homeAddress.id, sheet.selectedId)
        assertFalse(sheet.loading)
    }

    @Test
    fun applyingAnAddressReplacesTheHoldAndClosesTheSheet() {
        shopping.addressList = { listOf(homeAddress, office) }
        orderApi.onChangeAddress = { id, _ ->
            hold(id).copy(address = AddressSnapshot("Office", "9876543210", "5 Market Street", null, "Chennai", "Tamil Nadu", "600001"))
        }
        val vm = vm()
        vm.openAddressSheet()
        vm.selectAddress(7)

        vm.applyAddress()

        assertTrue("holdAddress 3 7" in orderApi.calls)
        assertEquals("Office", vm.state.value.hold?.address?.name)
        assertEquals(850, vm.state.value.hold?.grandTotal)
        assertNull(vm.state.value.addressSheet)
    }

    @Test
    fun anAddressAddedWhileTheSheetIsOpenBecomesTheSelection() {
        val vm = vm()
        vm.openAddressSheet()
        shopping.addressList = { listOf(homeAddress, office) }

        vm.refreshAddresses()

        assertEquals(7L, vm.state.value.addressSheet?.selectedId)
    }

    @Test
    fun anEditedAddressKeepsItsSelectionSoTheEditCanBeApplied() {
        shopping.addressList = { listOf(homeAddress, office) }
        val vm = vm()
        vm.openAddressSheet()
        vm.selectAddress(homeAddress.id)
        shopping.addressList = { listOf(homeAddress.copy(line1 = "9 New Road"), office) }

        vm.refreshAddresses()

        assertEquals(homeAddress.id, vm.state.value.addressSheet?.selectedId)
        vm.applyAddress()
        assertTrue("holdAddress 3 ${homeAddress.id}" in orderApi.calls)
    }

    @Test
    fun whenTheEditedSavedAddressNoLongerMatchesTheHoldNothingIsPreselected() {
        shopping.addressList = { listOf(homeAddress.copy(line1 = "9 New Road")) }
        val vm = vm()

        vm.openAddressSheet()

        assertNull(vm.state.value.addressSheet?.selectedId)
        vm.applyAddress()
        assertTrue(orderApi.calls.none { it.startsWith("holdAddress") })
    }

    @Test
    fun aLapsedHoldAnswerMarksItExpiredAndShowsTheMessageInTheSheet() {
        orderApi.onChangeAddress = { _, _ -> throw httpError(409, """{"code":"HOLD_EXPIRED","message":"This payment window has ended"}""") }
        val vm = vm()
        vm.openAddressSheet()

        vm.applyAddress()

        assertTrue(vm.state.value.expired)
        assertEquals("This payment window has ended", vm.state.value.addressSheet?.error)
        assertFalse(vm.state.value.addressSheet?.busy ?: true)
    }

    @Test
    fun anExpiredHoldCannotOpenTheAddressSheet() {
        val vm = vm()
        vm.markExpired()

        vm.openAddressSheet()

        assertNull(vm.state.value.addressSheet)
    }

    @Test
    fun closingTheSheetKeepsTheHoldAsItWas() {
        val vm = vm()
        vm.openAddressSheet()

        vm.closeAddressSheet()

        assertNull(vm.state.value.addressSheet)
        assertEquals(homeAddress.name, vm.state.value.hold?.address?.name)
    }
}
