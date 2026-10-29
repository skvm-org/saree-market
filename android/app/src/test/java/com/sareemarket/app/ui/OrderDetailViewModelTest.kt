package com.sareemarket.app.ui

import androidx.lifecycle.SavedStateHandle
import com.sareemarket.app.data.FakeOrderApi
import com.sareemarket.app.data.FakePhotoSource
import com.sareemarket.app.data.OrderRepository
import com.sareemarket.app.data.OrderStatus
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.order
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.reseller.OrderDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailViewModelTest {

    private val api = FakeOrderApi()
    private fun vm(id: Long = 5) = OrderDetailViewModel(
        SavedStateHandle(mapOf("id" to id)),
        OrderRepository(api, FakePhotoSource(mapOf("content://proof/1" to ByteArray(10))), testJson),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsTheOrder() {
        api.onOrder = { order(it, OrderStatus.SHIPPED) }

        val vm = vm()

        assertEquals(OrderStatus.SHIPPED, vm.state.value.order?.status)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun resubmitNeedsTwelveDigitsAndARejectedOrder() {
        api.onOrder = { order(it, OrderStatus.PAYMENT_REJECTED) }
        val vm = vm()

        vm.onUtr("41234567890")
        vm.resubmit()
        assertTrue(api.calls.none { it.startsWith("resubmit") })

        vm.onUtr("412345678902")
        vm.resubmit()
        assertTrue("resubmit 5 false" in api.calls)
    }

    @Test
    fun resubmitIsIgnoredWhenTheOrderIsNotRejected() {
        api.onOrder = { order(it, OrderStatus.CONFIRMED) }
        val vm = vm()
        vm.onUtr("412345678902")

        vm.resubmit()

        assertTrue(api.calls.none { it.startsWith("resubmit") })
    }

    @Test
    fun resubmitReplacesTheOrderAndClearsTheForm() {
        api.onOrder = { order(it, OrderStatus.PAYMENT_REJECTED) }
        api.onPay = { id, _, _ -> order(id, OrderStatus.PAYMENT_SUBMITTED) }
        val vm = vm()
        vm.onUtr("412345678902")
        vm.onProof("content://proof/1")

        vm.resubmit()

        assertEquals(OrderStatus.PAYMENT_SUBMITTED, vm.state.value.order?.status)
        assertEquals("", vm.state.value.utr)
        assertNull(vm.state.value.proof)
        assertTrue("resubmit 5 true" in api.calls)
    }

    @Test
    fun aRefusedResubmitShowsTheServersMessage() {
        api.onOrder = { order(it, OrderStatus.PAYMENT_REJECTED) }
        api.onPay = { _, _, _ -> throw httpError(409, """{"code":"UTR_IN_USE","message":"That UTR is already used on another order"}""") }
        val vm = vm()
        vm.onUtr("412345678902")

        vm.resubmit()

        assertEquals("That UTR is already used on another order", vm.state.value.actionError)
        assertEquals(OrderStatus.PAYMENT_REJECTED, vm.state.value.order?.status)
    }

    @Test
    fun cancelWorksOnlyForSubmittedOrRejectedOrders() {
        api.onOrder = { order(it, OrderStatus.SHIPPED) }
        val shipped = vm()
        shipped.cancel()
        assertTrue(api.calls.none { it.startsWith("cancel") })

        api.onOrder = { order(it, OrderStatus.PAYMENT_SUBMITTED) }
        val waiting = vm()
        waiting.cancel()

        assertTrue("cancel 5" in api.calls)
        assertEquals(OrderStatus.CANCELLED, waiting.state.value.order?.status)
    }

    @Test
    fun aFailedCancelShowsTheMessageAndKeepsTheOrder() {
        api.onOrder = { order(it, OrderStatus.PAYMENT_SUBMITTED) }
        api.onCancel = { throw httpError(409, """{"code":"BAD_STATE","message":"This can't be done to an order that is CONFIRMED"}""") }
        val vm = vm()

        vm.cancel()

        assertEquals("This can't be done to an order that is CONFIRMED", vm.state.value.actionError)
        assertEquals(OrderStatus.PAYMENT_SUBMITTED, vm.state.value.order?.status)
    }
}
