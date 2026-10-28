package com.sareemarket.app.ui.seller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.Booking
import com.sareemarket.app.data.BookingStatus
import com.sareemarket.app.data.BookingRepository
import com.sareemarket.app.data.BookingSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which requests the list shows; the four count tiles above it are the buttons. [state] is null for every request. */
enum class RequestFilter(val state: BookingStatus?) {
    PENDING(BookingStatus.PENDING), ON_HOLD(BookingStatus.ACCEPTED), SOLD(BookingStatus.SOLD), ALL(null)
}

data class RequestsState(
    val filter: RequestFilter = RequestFilter.PENDING,
    val items: List<Booking> = emptyList(),
    val nextPage: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** The request an action is running on. */
    val busyId: Long? = null,
    val summary: BookingSummary = BookingSummary(),
)

/** The seller's inbox of booking requests: open ones to answer, history to look back on, and the counts above them. */
@HiltViewModel
class RequestsViewModel @Inject constructor(private val repository: BookingRepository) : ViewModel() {

    private val _state = MutableStateFlow(RequestsState())
    val state: StateFlow<RequestsState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch { repository.summary.collect { s -> _state.update { it.copy(summary = s) } } }
        reload()
    }

    fun selectFilter(filter: RequestFilter) {
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

    fun accept(id: Long) = act(id) { repository.accept(id) }

    fun reject(id: Long) = act(id) { repository.reject(id) }

    fun extend(id: Long) = act(id) { repository.extend(id) }

    fun sold(id: Long) = act(id) { repository.sold(id) }

    /** Runs one answer. The lists are fetched again afterwards, since the request usually moves to another tab. */
    private fun act(id: Long, call: suspend () -> Result<Booking>) {
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = id, error = null) }
        viewModelScope.launch {
            val error = call().exceptionOrNull()?.message
            _state.update { it.copy(busyId = null, error = error) }
            fetch(0)
        }
    }

    private suspend fun fetch(page: Int) {
        repository.received(_state.value.filter.state, page).fold(
            onSuccess = { result ->
                _state.update {
                    it.copy(
                        items = if (page == 0) result.items else (it.items + result.items).distinctBy { b -> b.id },
                        nextPage = page + 1,
                        hasMore = page + 1 < result.totalPages,
                        loading = false,
                        loadingMore = false,
                        refreshing = false,
                    )
                }
            },
            onFailure = { e -> _state.update { it.copy(loading = false, loadingMore = false, refreshing = false, error = e.message) } },
        )
    }
}
