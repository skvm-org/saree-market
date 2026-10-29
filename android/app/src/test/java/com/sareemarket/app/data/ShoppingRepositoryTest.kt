package com.sareemarket.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingRepositoryTest {

    private val api = FakeShoppingApi()
    private val cart = CartRepository(api, testJson)
    private val wishlist = WishlistRepository(api, testJson)
    private val addresses = AddressRepository(api, testJson)

    @Test
    fun cartRefreshStoresTheCart() = runTest {
        api.cart = { cartOf(cartSeller(1)) }

        cart.refresh()

        assertEquals(1, cart.cart.value.sellers.size)
        assertEquals(1, cart.cart.value.lineCount)
    }

    @Test
    fun settingAQuantityRefreshesTheCart() = runTest {
        cart.setQuantity(10, 3)

        assertEquals(listOf("setLine 10 3", "cart"), api.calls)
    }

    @Test
    fun removingALineRefreshesTheCart() = runTest {
        cart.remove(10)

        assertEquals(listOf("removeLine 10", "cart"), api.calls)
    }

    @Test
    fun aFailureKeepsThePreviousCartAndReturnsTheServersMessage() = runTest {
        api.cart = { cartOf(cartSeller(1)) }
        cart.refresh()
        api.onSetLine = { _, _ -> throw httpError(409, """{"code":"NOT_ENOUGH_STOCK","message":"Only 2 left"}""") }

        val result = cart.setQuantity(10, 9)

        assertEquals("Only 2 left", result.exceptionOrNull()?.message)
        assertEquals(1, cart.cart.value.lineCount)
        assertEquals(listOf("cart", "setLine 10 9"), api.calls)
    }

    @Test
    fun resetEmptiesTheCart() = runTest {
        api.cart = { cartOf(cartSeller(1)) }
        cart.refresh()

        cart.reset()

        assertTrue(cart.cart.value.sellers.isEmpty())
    }

    @Test
    fun wishlistAddAndRemoveCallTheApi() = runTest {
        wishlist.add(7)
        wishlist.remove(7)
        wishlist.page(2)

        assertEquals(listOf("wish+ 7", "wish- 7", "wishlist 2"), api.calls)
    }

    @Test
    fun addressCreateReturnsTheAddress() = runTest {
        val result = addresses.create(AddressForm("Home", "9876543210", "12 Gandhi Road", null, "Erode", "Tamil Nadu", "638001", true))

        assertEquals(homeAddress, result.getOrNull())
        assertEquals(listOf("createAddress Home"), api.calls)
    }

    @Test
    fun addressLimitMessageSurvives() = runTest {
        api.onAddress = { throw httpError(409, """{"code":"ADDRESS_LIMIT","message":"You can save up to 10 addresses; delete one first"}""") }

        val result = addresses.create(AddressForm("Home", "9876543210", "x", null, "c", "s", "638001", null))

        assertEquals("ADDRESS_LIMIT", (result.exceptionOrNull() as ApiFailure).code)
    }
}
