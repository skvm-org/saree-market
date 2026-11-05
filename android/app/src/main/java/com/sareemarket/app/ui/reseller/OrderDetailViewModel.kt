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
    val busy: Boolean = false,
    val actionError: String? = null,
)

/** One order: its progress and tracking, and the two things a customer can do to it: cancel it before it ships, or mark it received. */
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

    /** Cancels a confirmed order. [reason] is optional; the app does not refund, the customer settles that with the seller. */
    fun cancel(reason: String? = null) = act(OrderStatus.CONFIRMED) { orders.cancel(orderId, reason) }

    /** The parcel arrived: a shipped order becomes delivered. */
    fun receive() = act(OrderStatus.SHIPPED) { orders.receive(orderId) }

    /** Runs one action, only when the order is in the [allowed] status; the server decides again. */
    private fun act(allowed: OrderStatus, call: suspend () -> Result<Order>) {
        val s = _state.value
        if (s.order?.status != allowed || s.busy) return
        _state.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            call().fold(
                onSuccess = { o -> _state.update { it.copy(order = o, busy = false) } },
                onFailure = { e -> _state.update { it.copy(busy = false, actionError = e.message) } },
            )
        }
    }
}
