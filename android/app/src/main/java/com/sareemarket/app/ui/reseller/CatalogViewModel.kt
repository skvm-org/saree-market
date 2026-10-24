package com.sareemarket.app.ui.reseller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.CatalogItem
import com.sareemarket.app.data.CatalogQuery
import com.sareemarket.app.data.CatalogRepository
import com.sareemarket.app.data.ReferenceData
import com.sareemarket.app.data.SellerListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogState(
    val query: CatalogQuery = CatalogQuery(),
    val items: List<CatalogItem> = emptyList(),
    val totalItems: Long = 0,
    val nextPage: Int = 0,
    val hasMore: Boolean = false,
    /** True while the first page of the current search is loading. */
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    /** Choices for the filter sheet; empty until they have loaded. */
    val reference: ReferenceData? = null,
    val sellers: List<SellerListItem> = emptyList(),
)

@HiltViewModel
class CatalogViewModel @Inject constructor(private val repository: CatalogRepository) : ViewModel() {

    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadOptions()
        reload()
    }

    private fun loadOptions() {
        viewModelScope.launch {
            repository.referenceData().onSuccess { ref -> _state.update { it.copy(reference = ref) } }
            repository.sellers().onSuccess { list -> _state.update { it.copy(sellers = list) } }
        }
    }

    fun setKeyword(text: String) {
        if (text != _state.value.query.q) apply(_state.value.query.copy(q = text))
    }

    fun apply(query: CatalogQuery) {
        _state.update { it.copy(query = query) }
        reload()
    }

    /** Clears every filter but keeps what is typed in the search box and the sort order. */
    fun clearFilters() {
        val q = _state.value.query
        apply(CatalogQuery(q = q.q, sort = q.sort))
    }

    fun retry() = if (_state.value.items.isEmpty()) reload() else loadNextPage()

    fun loadNextPage() {
        val s = _state.value
        if (!s.hasMore || s.loading || s.loadingMore) return
        _state.update { it.copy(loadingMore = true, error = null) }
        loadJob = viewModelScope.launch { fetch(s.nextPage) }
    }

    // A new search cancels the one still loading, so a slow old answer can never replace a newer one.
    private fun reload() {
        loadJob?.cancel()
        _state.update {
            it.copy(items = emptyList(), totalItems = 0, nextPage = 0, hasMore = false, loading = true, loadingMore = false, error = null)
        }
        loadJob = viewModelScope.launch { fetch(0) }
    }

    private suspend fun fetch(page: Int) {
        repository.catalog(_state.value.query, page).fold(
            onSuccess = { result ->
                _state.update {
                    it.copy(
                        items = if (page == 0) result.items else it.items + result.items,
                        totalItems = result.totalItems,
                        nextPage = page + 1,
                        hasMore = page + 1 < result.totalPages,
                        loading = false,
                        loadingMore = false,
                    )
                }
            },
            onFailure = { e -> _state.update { it.copy(loading = false, loadingMore = false, error = e.message) } },
        )
    }
}
