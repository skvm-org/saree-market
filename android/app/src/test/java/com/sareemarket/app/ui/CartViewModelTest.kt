package com.sareemarket.app.ui

import com.sareemarket.app.data.AddressRepository
import com.sareemarket.app.data.CartRepository
import com.sareemarket.app.data.CheckoutResult
import com.sareemarket.app.data.FakeOrderApi
import com.sareemarket.app.data.FakePhotoSource
import com.sareemarket.app.data.FakeShoppingApi
import com.sareemarket.app.data.LineAdjustment
import com.sareemarket.app.data.OrderRepository
import com.sareemarket.app.data.SkippedSeller
import com.sareemarket.app.data.cartLine
import com.sareemarket.app.data.cartOf
import com.sareemarket.app.data.cartSeller
import com.sareemarket.app.data.hold
import com.sareemarket.app.data.homeAddress
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.reseller.CartViewModel
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
class CartViewModelTest {

    private val shopping = FakeShoppingApi()
    private val orderApi = FakeOrderApi()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = CartViewModel(
        CartRepository(shopping, testJson), AddressRepository(shopping, testJson),
        OrderRepository(orderApi, FakePhotoSource(), testJson),
    )

    @Test
    fun reloadShowsTheGroupedCart() {
        shopping.cart = { cartOf(cartSeller(1), cartSeller(2)) }

        val vm = vm()

        assertEquals(listOf(1L, 2L), vm.state.value.cart.sellers.map { it.sellerId })
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun sellersWithAnOpenPaymentAreMarkedWithTheirHold() {
        shopping.cart = { cartOf(cartSeller(1), cartSeller(2)) }
        orderApi.holds = { listOf(hold(9)) } // the fake hold belongs to seller 1

        val vm = vm()

        assertEquals(mapOf(1L to 9L), vm.state.value.holdBySeller)
    }

    @Test
    fun settingAQuantityShowsTheNewTotals() {
        var qty = 1
        shopping.cart = { cartOf(cartSeller(1, listOf(cartLine(10, quantity = qty)))) }
        shopping.onSetLine = { _, q -> qty = q; retrofit2.Response.success(Unit) }
        val vm = vm()

        vm.setQuantity(10, 3)

        assertEquals(2400, vm.state.value.cart.sellers.single().itemsTotal)
        assertNull(vm.state.value.busyItemId)
    }

    @Test
    fun removingALineUpdatesTheCart() {
        var lines = listOf(cartLine(10), cartLine(11))
        shopping.cart = { cartOf(cartSeller(1, lines)) }
        shopping.onRemoveLine = { id -> lines = lines.filter { it.itemId != id }; retrofit2.Response.success(Unit) }
        val vm = vm()

        vm.remove(10)

        assertEquals(listOf(11L), vm.state.value.cart.sellers.single().lines.map { it.itemId })
    }

    @Test
    fun aFailedChangeKeepsTheCartAndShowsTheServersMessage() {
        shopping.cart = { cartOf(cartSeller(1)) }
        shopping.onSetLine = { _, _ -> throw httpError(409, """{"code":"NOT_ENOUGH_STOCK","message":"Only 2 left"}""") }
        val vm = vm()

        vm.setQuantity(10, 9)

        assertEquals("Only 2 left", vm.state.value.error)
        assertEquals(1, vm.state.value.cart.sellers.size)
    }

    @Test
    fun startingCheckoutPreselectsTheDefaultAddress() {
        shopping.cart = { cartOf(cartSeller(1)) }
        val vm = vm()

        vm.startCheckout(1)

        assertEquals(homeAddress.id, vm.state.value.checkout?.selectedAddressId)
        assertEquals(1L, vm.state.value.checkout?.seller?.sellerId)
    }

    @Test
    fun aSellerNotAcceptingOrdersCannotStartCheckout() {
        shopping.cart = { cartOf(cartSeller(1, acceptsOrders = false)) }
        val vm = vm()

        vm.startCheckout(1)

        assertNull(vm.state.value.checkout)
    }

    @Test
    fun checkoutWithoutAnAddressIsBlocked() {
        shopping.cart = { cartOf(cartSeller(1)) }
        val vm = vm()
        vm.startCheckout(1)
        vm.selectAddress(null)

        vm.confirmCheckout()

        assertTrue(orderApi.calls.none { it.startsWith("checkout") })
    }

    @Test
    fun confirmingCheckoutOpensTheNewHoldAndRefreshesTheCart() {
        shopping.cart = { cartOf(cartSeller(1)) }
        orderApi.onCheckout = { CheckoutResult(listOf(hold(42)), emptyList(), emptyList()) }
        val vm = vm()
        vm.startCheckout(1)
        vm.onNote(" after 5 ")

        vm.confirmCheckout()

        assertEquals(42L, vm.state.value.openHoldId)
        assertNull(vm.state.value.checkout)
        assertTrue("checkout 1 after 5 1" in orderApi.calls)
        assertEquals(2, shopping.calls.count { it == "cart" })
    }

    @Test
    fun theHoldIsOpenedOnlyOnce() {
        shopping.cart = { cartOf(cartSeller(1)) }
        orderApi.onCheckout = { CheckoutResult(listOf(hold(42)), emptyList(), emptyList()) }
        val vm = vm()
        vm.startCheckout(1)
        vm.confirmCheckout()

        vm.holdOpened()

        assertNull(vm.state.value.openHoldId)
    }

    @Test
    fun aCheckoutFailureKeepsTheCartAndShowsTheServersMessage() {
        shopping.cart = { cartOf(cartSeller(1)) }
        orderApi.onCheckout = { throw httpError(409, """{"code":"HOLD_LIMIT","message":"You can have 3 payments in progress; finish or cancel one first"}""") }
        val vm = vm()
        vm.startCheckout(1)

        vm.confirmCheckout()

        assertEquals("You can have 3 payments in progress; finish or cancel one first", vm.state.value.checkout?.error)
        assertEquals(1, vm.state.value.cart.sellers.size)
        assertNull(vm.state.value.openHoldId)
    }

    @Test
    fun whenNoHoldWasMadeTheReasonsAreShown() {
        shopping.cart = { cartOf(cartSeller(1)) }
        orderApi.onCheckout = {
            CheckoutResult(emptyList(), listOf(LineAdjustment(10, "Diwali silks", 2, 0)), listOf(SkippedSeller(1, "Lakshmi Silks", "NOTHING_IN_STOCK")))
        }
        val vm = vm()
        vm.startCheckout(1)

        vm.confirmCheckout()

        assertNull(vm.state.value.openHoldId)
        assertNotNull(vm.state.value.checkout?.result)
        assertEquals("NOTHING_IN_STOCK", vm.state.value.checkout?.result?.skipped?.single()?.reason)
    }

    @Test
    fun dismissingCheckoutClosesTheSheet() {
        shopping.cart = { cartOf(cartSeller(1)) }
        val vm = vm()
        vm.startCheckout(1)

        vm.dismissCheckout()

        assertNull(vm.state.value.checkout)
    }
}
