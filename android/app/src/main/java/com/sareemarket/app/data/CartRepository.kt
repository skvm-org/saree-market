package com.sareemarket.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** The customer's cart. [cart] is what the Cart tab and its badge show; every change refreshes it. */
@Singleton
class CartRepository @Inject constructor(private val api: ShoppingApi, private val json: Json) {

    private val _cart = MutableStateFlow(Cart())
    val cart: StateFlow<Cart> = _cart.asStateFlow()

    suspend fun refresh(): Result<Cart> = apiCall(json) { api.cart() }.onSuccess { _cart.value = it }

    suspend fun setQuantity(itemId: Long, quantity: Int): Result<Unit> =
        changing { api.setLine(itemId, QuantityBody(quantity)).requireSuccess() }

    suspend fun remove(itemId: Long): Result<Unit> = changing { api.removeLine(itemId).requireSuccess() }

    /** Forgets the cart on log out, so the next account does not briefly see it. */
    fun reset() {
        _cart.value = Cart()
    }

    private suspend fun changing(block: suspend () -> Unit): Result<Unit> =
        apiCall(json, block).also { if (it.isSuccess) refresh() }
}
