package com.sareemarket.app.ui.reseller

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.Order
import com.sareemarket.app.data.OrderRepository
import com.sareemarket.app.data.OrderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderDetailState(
    val order: Order? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** The corrected UTR typed after a rejection. */
    val utr: String = "",
    val proof: String? = null,
    val busy: Boolean = false,
    val actionError: String? = null,
)

/** One order: its progress, and the two things a customer can still do to it (send a corrected UTR, or cancel). */
@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val orders: OrderRepository,
) : ViewModel() {

    private val orderId: Long = checkNotNull(handle["id"])

    private val _state = MutableStateFlow(OrderDetailState())
    val state: StateFlow<OrderDetailState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.update { it.copy(loading = it.order == null, error = null) }
        viewModelScope.launch {
            orders.order(orderId).fold(
                onSuccess = { o -> _state.update { it.copy(order = o, loading = false) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }

    fun onUtr(value: String) = _state.update { it.copy(utr = value.filter(Char::isDigit).take(12), actionError = null) }

    fun onProof(uri: String?) = _state.update { it.copy(proof = uri) }

    fun resubmit() {
        val s = _state.value
        if (s.order?.status != OrderStatus.PAYMENT_REJECTED || s.utr.length != 12 || s.busy) return
        _state.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            orders.resubmit(orderId, s.utr, s.proof).fold(
                onSuccess = { o -> _state.update { it.copy(order = o, busy = false, utr = "", proof = null) } },
                onFailure = { e -> _state.update { it.copy(busy = false, actionError = e.message) } },
            )
        }
    }

    fun cancel() {
        val s = _state.value
        val status = s.order?.status
        if ((status != OrderStatus.PAYMENT_SUBMITTED && status != OrderStatus.PAYMENT_REJECTED) || s.busy) return
        _state.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            orders.cancel(orderId).fold(
                onSuccess = { o -> _state.update { it.copy(order = o, busy = false) } },
                onFailure = { e -> _state.update { it.copy(busy = false, actionError = e.message) } },
            )
        }
    }
}
