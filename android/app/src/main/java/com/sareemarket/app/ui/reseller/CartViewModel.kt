package com.sareemarket.app.ui.reseller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.Address
import com.sareemarket.app.data.AddressRepository
import com.sareemarket.app.data.Cart
import com.sareemarket.app.data.CartRepository
import com.sareemarket.app.data.CartSeller
import com.sareemarket.app.data.CheckoutResult
import com.sareemarket.app.data.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The checkout sheet for one seller's lines: which address, an optional note, and what came back. */
data class CheckoutUi(
    val seller: CartSeller,
    val addresses: List<Address> = emptyList(),
    val selectedAddressId: Long? = null,
    val loadingAddresses: Boolean = true,
    val note: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    /** Set when checkout answered without making a hold: why nothing was held. */
    val result: CheckoutResult? = null,
)

data class CartState(
    val cart: Cart = Cart(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val busyItemId: Long? = null,
    val checkout: CheckoutUi? = null,
    /** A hold was just made: the screen opens it, then calls [CartViewModel.holdOpened]. */
    val openHoldId: Long? = null,
    /** Sellers the customer is already paying: seller id to the hold's id. Their sarees are reserved, so Pay would clash. */
    val holdBySeller: Map<Long, Long> = emptyMap(),
)

/** The customer's cart, grouped by seller, and the step from a seller's group to a payment hold. */
@HiltViewModel
class CartViewModel @Inject constructor(
    private val cart: CartRepository,
    private val addresses: AddressRepository,
    private val orders: OrderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CartState(cart = cart.cart.value))
    val state: StateFlow<CartState> = _state.asStateFlow()

    init {
        viewModelScope.launch { cart.cart.collect { c -> _state.update { it.copy(cart = c) } } }
        reload()
    }

    fun reload() {
        _state.update { it.copy(loading = it.cart.sellers.isEmpty(), error = null) }
        viewModelScope.launch {
            loadHolds()
            cart.refresh().fold(
                onSuccess = { _state.update { it.copy(loading = false, refreshing = false) } },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.message) } },
            )
        }
    }

    private suspend fun loadHolds() {
        orders.holds().onSuccess { list -> _state.update { it.copy(holdBySeller = list.associate { h -> h.sellerId to h.id }) } }
    }

    fun pullRefresh() {
        _state.update { it.copy(refreshing = true) }
        reload()
    }

    fun setQuantity(itemId: Long, quantity: Int) = change(itemId) { cart.setQuantity(itemId, quantity) }

    fun remove(itemId: Long) = change(itemId) { cart.remove(itemId) }

    private fun change(itemId: Long, call: suspend () -> Result<Unit>) {
        if (_state.value.busyItemId != null) return
        _state.update { it.copy(busyItemId = itemId, error = null) }
        viewModelScope.launch {
            val error = call().exceptionOrNull()?.message
            _state.update { it.copy(busyItemId = null, error = error) }
        }
    }

    fun startCheckout(sellerId: Long) {
        val seller = _state.value.cart.sellers.firstOrNull { it.sellerId == sellerId } ?: return
        if (!seller.acceptsOrders || seller.grandTotal <= 0) return
        _state.update { it.copy(checkout = CheckoutUi(seller)) }
        refreshAddresses()
    }

    /** Looks at the saved addresses again (the customer may just have added one) and keeps or picks the selection. */
    fun refreshAddresses() {
        viewModelScope.launch {
            addresses.list().fold(
                onSuccess = { list ->
                    _state.update { s ->
                        val c = s.checkout ?: return@update s
                        val keep = c.selectedAddressId?.takeIf { id -> list.any { it.id == id } }
                        val pick = keep ?: (list.firstOrNull { it.isDefault } ?: list.firstOrNull())?.id
                        s.copy(checkout = c.copy(addresses = list, selectedAddressId = pick, loadingAddresses = false))
                    }
                },
                onFailure = { e -> _state.update { s -> s.copy(checkout = s.checkout?.copy(loadingAddresses = false, error = e.message)) } },
            )
        }
    }

    fun selectAddress(id: Long?) = _state.update { s -> s.copy(checkout = s.checkout?.copy(selectedAddressId = id, error = null)) }

    fun onNote(note: String) = _state.update { s -> s.copy(checkout = s.checkout?.copy(note = note.take(300))) }

    fun dismissCheckout() = _state.update { it.copy(checkout = null) }

    fun confirmCheckout() {
        val c = _state.value.checkout ?: return
        val address = c.selectedAddressId ?: return
        if (c.busy) return
        _state.update { it.copy(checkout = c.copy(busy = true, error = null, result = null)) }
        viewModelScope.launch {
            orders.checkout(address, c.note, c.seller.sellerId).fold(
                onSuccess = { result ->
                    cart.refresh()
                    val hold = result.holds.firstOrNull()
                    if (hold != null) {
                        _state.update { it.copy(checkout = null, openHoldId = hold.id) }
                    } else {
                        _state.update { s -> s.copy(checkout = s.checkout?.copy(busy = false, result = result)) }
                    }
                },
                onFailure = { e -> _state.update { s -> s.copy(checkout = s.checkout?.copy(busy = false, error = e.message)) } },
            )
        }
    }

    fun holdOpened() = _state.update { it.copy(openHoldId = null) }
}
