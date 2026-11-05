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
    fun receiveWorksOnlyForAShippedOrderAndReplacesIt() {
        api.onOrder = { order(it, OrderStatus.CONFIRMED) }
        val waiting = vm()
        waiting.receive()
        assertTrue(api.calls.none { it.startsWith("receive") })

        api.onOrder = { order(it, OrderStatus.SHIPPED, courier = "DTDC", trackingNumber = "D123") }
        val shipped = vm()
        shipped.receive()

        assertTrue("receive 5" in api.calls)
        assertEquals(OrderStatus.DELIVERED, shipped.state.value.order?.status)
        assertFalse(shipped.state.value.busy)
    }

    @Test
    fun aFailedReceiveShowsTheMessageAndKeepsTheOrder() {
        api.onOrder = { order(it, OrderStatus.SHIPPED) }
        api.onReceive = { throw httpError(409, """{"code":"BAD_STATE","message":"This can't be done to an order that is DELIVERED"}""") }
        val vm = vm()

        vm.receive()

        assertEquals("This can't be done to an order that is DELIVERED", vm.state.value.actionError)
        assertEquals(OrderStatus.SHIPPED, vm.state.value.order?.status)
    }

    @Test
    fun cancelWorksOnlyForAConfirmedOrder() {
        api.onOrder = { order(it, OrderStatus.SHIPPED) }
        val shipped = vm()
        shipped.cancel()
        assertTrue(api.calls.none { it.startsWith("cancel") })

        api.onOrder = { order(it, OrderStatus.CONFIRMED) }
        val waiting = vm()
        waiting.cancel("Ordered by mistake")

        assertTrue("cancel 5 Ordered by mistake" in api.calls)
        assertEquals(OrderStatus.CANCELLED, waiting.state.value.order?.status)
    }

    @Test
    fun cancelWithoutAReasonSendsNone() {
        api.onOrder = { order(it, OrderStatus.CONFIRMED) }
        val vm = vm()

        vm.cancel()

        assertTrue("cancel 5 null" in api.calls)
    }

    @Test
    fun aFailedCancelShowsTheMessageAndKeepsTheOrder() {
        api.onOrder = { order(it, OrderStatus.CONFIRMED) }
        api.onCancel = { _, _ -> throw httpError(409, """{"code":"BAD_STATE","message":"This can't be done to an order that is SHIPPED"}""") }
        val vm = vm()

        vm.cancel()

        assertEquals("This can't be done to an order that is SHIPPED", vm.state.value.actionError)
        assertEquals(OrderStatus.CONFIRMED, vm.state.value.order?.status)
    }

    @Test
    fun theTrackingNumberComesWithTheOrder() {
        api.onOrder = { order(it, OrderStatus.SHIPPED, courier = "DTDC", trackingNumber = "D123") }

        val vm = vm()

        assertEquals("D123", vm.state.value.order?.trackingNumber)
        assertEquals("DTDC", vm.state.value.order?.courier)
    }
}
