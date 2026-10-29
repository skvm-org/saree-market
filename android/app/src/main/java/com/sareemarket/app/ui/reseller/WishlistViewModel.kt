package com.sareemarket.app.ui.reseller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.CatalogItem
import com.sareemarket.app.data.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WishlistState(
    val items: List<CatalogItem> = emptyList(),
    val nextPage: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** The saree being taken off the wishlist right now. */
    val busyId: Long? = null,
)

/** The sarees the customer saved, newest saved first. */
@HiltViewModel
class WishlistViewModel @Inject constructor(private val repository: WishlistRepository) : ViewModel() {

    private val _state = MutableStateFlow(WishlistState())
    val state: StateFlow<WishlistState> = _state.asStateFlow()

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

    fun remove(itemId: Long) {
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = itemId, error = null) }
        viewModelScope.launch {
            repository.remove(itemId).fold(
                onSuccess = { _state.update { s -> s.copy(busyId = null, items = s.items.filterNot { it.id == itemId }) } },
                onFailure = { e -> _state.update { it.copy(busyId = null, error = e.message) } },
            )
        }
    }

    private suspend fun fetch(page: Int) {
        repository.page(page).fold(
            onSuccess = { result ->
                _state.update {
                    it.copy(
                        items = if (page == 0) result.items else (it.items + result.items).distinctBy { c -> c.id },
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
