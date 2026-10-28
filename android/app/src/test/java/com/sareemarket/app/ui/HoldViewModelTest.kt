package com.sareemarket.app.ui

import androidx.lifecycle.SavedStateHandle
import com.sareemarket.app.data.CartRepository
import com.sareemarket.app.data.FakeOrderApi
import com.sareemarket.app.data.FakePhotoSource
import com.sareemarket.app.data.FakeShoppingApi
import com.sareemarket.app.data.OrderRepository
import com.sareemarket.app.data.hold
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
    )

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
        assertNotNull(vm.state.value.error)
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
}
