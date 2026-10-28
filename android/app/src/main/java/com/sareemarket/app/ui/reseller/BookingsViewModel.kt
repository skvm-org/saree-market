package com.sareemarket.app.ui.reseller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.Booking
import com.sareemarket.app.data.BookingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookingsState(
    val items: List<Booking> = emptyList(),
    val nextPage: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** The request being cancelled right now. */
    val busyId: Long? = null,
)

/** The reseller's own booking requests, open ones first. Opening the list tells the server they were seen. */
@HiltViewModel
class BookingsViewModel @Inject constructor(private val repository: BookingRepository) : ViewModel() {

    private val _state = MutableStateFlow(BookingsState())
    val state: StateFlow<BookingsState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        reload()
    }

    fun reload() {
        job?.cancel()
        _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
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

    fun cancel(id: Long) {
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = id, error = null) }
        viewModelScope.launch {
            repository.cancel(id).fold(
                onSuccess = { b -> _state.update { s -> s.copy(busyId = null, items = s.items.map { if (it.id == b.id) b else it }) } },
                onFailure = { e -> _state.update { it.copy(busyId = null, error = e.message) } },
            )
        }
    }

    private suspend fun fetch(page: Int) {
        repository.mine(null, page).fold(
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
                // The badge clears once the list is on screen; the rows keep their dots until the next visit.
                if (page == 0 && result.items.any { it.unread }) repository.markSeen()
            },
            onFailure = { e -> _state.update { it.copy(loading = false, loadingMore = false, refreshing = false, error = e.message) } },
        )
    }
}
