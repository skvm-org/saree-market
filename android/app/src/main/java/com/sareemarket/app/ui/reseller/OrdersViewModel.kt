package com.sareemarket.app.ui.reseller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.Hold
import com.sareemarket.app.data.Order
import com.sareemarket.app.data.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrdersState(
    /** Payments in progress, soonest to run out first. */
    val holds: List<Hold> = emptyList(),
    val items: List<Order> = emptyList(),
    val nextPage: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)

/** The customer's payments in progress and their orders, newest first. Opening the list tells the server they were seen. */
@HiltViewModel
class OrdersViewModel @Inject constructor(private val repository: OrderRepository) : ViewModel() {

    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        reload()
    }

    fun reload() {
        job?.cancel()
        _state.update { it.copy(loading = it.items.isEmpty() && it.holds.isEmpty(), error = null) }
        job = viewModelScope.launch { fetch(0) }
    }

    fun pullRefresh() {
        job?.cancel()
        _state.update { it.copy(refreshing = true, error = null) }
        job = viewModelScope.launch { fetch(0) }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.loading || s.loadingMore) return
        _state.update { it.copy(loadingMore = true, error = null) }
        job = viewModelScope.launch { fetch(s.nextPage) }
    }

    private suspend fun fetch(page: Int) {
        if (page == 0) repository.holds().onSuccess { holds -> _state.update { it.copy(holds = holds) } }
        repository.mine(null, page).fold(
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
                // The unread order may sit on a later page, so the server's own count decides too.
                if (page == 0 && (result.items.any { it.unread } || repository.summary.value.unread > 0)) repository.markSeen()
            },
            onFailure = { e -> _state.update { it.copy(loading = false, loadingMore = false, refreshing = false, error = e.message) } },
        )
    }
}
