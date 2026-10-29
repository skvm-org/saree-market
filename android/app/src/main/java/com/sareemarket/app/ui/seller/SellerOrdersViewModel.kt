package com.sareemarket.app.ui.seller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.Order
import com.sareemarket.app.data.OrderRepository
import com.sareemarket.app.data.OrderStatus
import com.sareemarket.app.data.OrderSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which orders the list shows; the tiles above it are the buttons. [state] is null for every order. */
enum class SellerOrderFilter(val state: OrderStatus?) {
    TO_CONFIRM(OrderStatus.PAYMENT_SUBMITTED),
    TO_PACK(OrderStatus.CONFIRMED),
    TO_SHIP(OrderStatus.PACKED),
    SHIPPED(OrderStatus.SHIPPED),
    ALL(null),
}

data class SellerOrdersState(
    val filter: SellerOrderFilter = SellerOrderFilter.TO_CONFIRM,
    val items: List<Order> = emptyList(),
    val nextPage: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** The order an action is running on. */
    val busyId: Long? = null,
    val summary: OrderSummary = OrderSummary(),
)

/** The seller's orders: who paid, what to confirm, pack and ship, and the counts above the list. */
@HiltViewModel
class SellerOrdersViewModel @Inject constructor(private val repository: OrderRepository) : ViewModel() {

    private val _state = MutableStateFlow(SellerOrdersState())
    val state: StateFlow<SellerOrdersState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch { repository.summary.collect { s -> _state.update { it.copy(summary = s) } } }
        reload()
    }

    fun selectFilter(filter: SellerOrderFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter, items = emptyList(), nextPage = 0, hasMore = false) }
        reload()
    }

    fun reload() {
        job?.cancel()
        _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
        job = viewModelScope.launch {
            repository.refreshSummary()
            fetch(0)
        }
    }

    fun pullRefresh() {
        job?.cancel()
        _state.update { it.copy(refreshing = true, error = null) }
        job = viewModelScope.launch {
            repository.refreshSummary()
            fetch(0)
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.loading || s.loadingMore) return
        _state.update { it.copy(loadingMore = true, error = null) }
        job = viewModelScope.launch { fetch(s.nextPage) }
    }

    fun confirmPayment(id: Long) = act(id) { repository.confirmPayment(id) }

    fun pack(id: Long) = act(id) { repository.pack(id) }

    fun deliver(id: Long) = act(id) { repository.deliver(id) }

    fun ship(id: Long, courier: String?, trackingNumber: String?) = act(id) { repository.ship(id, courier, trackingNumber) }

    fun rejectPayment(id: Long, reason: String) = needingReason(reason) { act(id) { repository.rejectPayment(id, reason) } }

    fun cancel(id: Long, reason: String) = needingReason(reason) { act(id) { repository.cancelAsSeller(id, reason) } }

    private fun needingReason(reason: String, run: () -> Unit) {
        if (reason.isBlank()) {
            _state.update { it.copy(error = "Tell the customer why.") }
            return
        }
        run()
    }

    /** Runs one action. The list and counts are fetched again afterwards, even after a failure: the order may have moved on. */
    private fun act(id: Long, call: suspend () -> Result<Order>) {
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = id, error = null) }
        viewModelScope.launch {
            val error = call().exceptionOrNull()?.message
            _state.update { it.copy(busyId = null, error = error) }
            repository.refreshSummary()
            fetch(0)
        }
    }

    private suspend fun fetch(page: Int) {
        repository.received(_state.value.filter.state, page).fold(
            onSuccess = { result ->
                _state.update {
                    it.copy(
                        items = if (page == 0) result.items else (it.items + result.items).distinctBy { o -> o.id },
                        nextPage = page + 1,
                        hasMore = page + 1 < result.totalPages,
                        loading = false,
                        loadingMore = false,
                        refreshing = false,
                    )
                }
                // The badge clears once the list is on screen; the rows keep their dots until the next visit.
                if (page == 0 && (result.items.any { it.unread } || repository.summary.value.unread > 0)) repository.markSeen()
            },
            onFailure = { e -> _state.update { it.copy(loading = false, loadingMore = false, refreshing = false, error = e.message) } },
        )
    }
}
