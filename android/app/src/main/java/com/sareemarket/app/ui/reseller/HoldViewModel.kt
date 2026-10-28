package com.sareemarket.app.ui.reseller

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.ApiFailure
import com.sareemarket.app.data.CartRepository
import com.sareemarket.app.data.Hold
import com.sareemarket.app.data.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HoldState(
    val hold: Hold? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val utr: String = "",
    /** The screenshot the customer picked, as a URI string. */
    val proof: String? = null,
    val busy: Boolean = false,
    val payError: String? = null,
    /** The two hours ran out (or the hold is gone): nothing can be paid here any more. */
    val expired: Boolean = false,
    /** The payment went through: the screen opens this order, then calls [HoldViewModel.paidHandled]. */
    val paidOrderId: Long? = null,
    /** The customer released the hold: the screen goes back to the cart. */
    val closed: Boolean = false,
) {
    val canPay: Boolean get() = hold != null && utr.length == UTR_LENGTH && !busy && !expired
}

private const val UTR_LENGTH = 12

/** One payment hold: how long is left, whom to pay, and the UTR (plus optional screenshot) that turns it into an order. */
@HiltViewModel
class HoldViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val orders: OrderRepository,
    private val cart: CartRepository,
) : ViewModel() {

    private val holdId: Long = checkNotNull(handle["id"])

    private val _state = MutableStateFlow(HoldState())
    val state: StateFlow<HoldState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            orders.holds().fold(
                onSuccess = { list ->
                    val hold = list.firstOrNull { it.id == holdId }
                    _state.update {
                        if (hold == null) it.copy(loading = false, expired = true, error = "This payment window has ended.")
                        else it.copy(loading = false, hold = hold)
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }

    fun onUtr(value: String) = _state.update { it.copy(utr = value.filter(Char::isDigit).take(UTR_LENGTH), payError = null) }

    fun onProof(uri: String?) = _state.update { it.copy(proof = uri) }

    fun markExpired() = _state.update { it.copy(expired = true) }

    fun pay() {
        val s = _state.value
        if (!s.canPay) return
        _state.update { it.copy(busy = true, payError = null) }
        viewModelScope.launch {
            orders.pay(holdId, s.utr, s.proof).fold(
                onSuccess = { order -> _state.update { it.copy(busy = false, paidOrderId = order.id) } },
                onFailure = { e ->
                    val ended = (e as? ApiFailure)?.code == "HOLD_EXPIRED"
                    _state.update { it.copy(busy = false, payError = e.message, expired = it.expired || ended) }
                },
            )
        }
    }

    fun paidHandled() = _state.update { it.copy(paidOrderId = null) }

    /** Gives the sarees back and returns the customer to the cart. */
    fun cancel() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, payError = null) }
        viewModelScope.launch {
            orders.cancelHold(holdId).fold(
                onSuccess = {
                    cart.refresh()
                    _state.update { it.copy(busy = false, closed = true) }
                },
                onFailure = { e -> _state.update { it.copy(busy = false, payError = e.message) } },
            )
        }
    }
}
