package com.sareemarket.app.ui.reseller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.CatalogItem
import com.sareemarket.app.data.CatalogQuery
import com.sareemarket.app.data.CatalogRepository
import com.sareemarket.app.data.CollectionSummary
import com.sareemarket.app.data.FollowRepository
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
    /** The picture being searched with (a URI string), or null for a normal catalog search. */
    val imageUri: String? = null,
    /** Photos matching the picture; only filled during a photo search. */
    val items: List<CatalogItem> = emptyList(),
    /** Whole collections, as the sellers posted them; what a normal search lists. */
    val collections: List<CollectionSummary> = emptyList(),
    val totalItems: Long = 0,
    val nextPage: Int = 0,
    val hasMore: Boolean = false,
    /** True while the first page of the current search is loading. */
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    /** True while a pull-to-refresh reload runs; the old results stay on screen meanwhile. */
    val refreshing: Boolean = false,
    val error: String? = null,
    /** Choices for the filter sheet; empty until they have loaded. */
    val reference: ReferenceData? = null,
    val sellers: List<SellerListItem> = emptyList(),
    /** How many sellers the reseller follows: the number on the Following tab. */
    val followedCount: Int = 0,
) {
    /** True when the current search found nothing to list. */
    val noResults get() = if (imageUri != null) items.isEmpty() else collections.isEmpty()
}

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val follows: FollowRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadOptions()
        startOnTheRightTab()
    }

    /** A reseller who follows anyone lands on "New from sellers you follow"; everyone else sees all sellers. */
    private fun startOnTheRightTab() {
        viewModelScope.launch {
            val count = follows.follows().getOrNull()?.size ?: 0
            _state.update { it.copy(followedCount = count, query = it.query.copy(followingOnly = count > 0)) }
            reload()
        }
    }

    /** Called when the catalog comes back into view, since follows may have changed on another screen. */
    fun refreshFollowCount() {
        viewModelScope.launch {
            val count = follows.follows().getOrNull()?.size ?: return@launch
            if (count == _state.value.followedCount) return@launch
            _state.update { it.copy(followedCount = count) }
            if (count == 0 && _state.value.query.followingOnly) setFollowingOnly(false)
        }
    }

    fun setFollowingOnly(on: Boolean) {
        if (on != _state.value.query.followingOnly) apply(_state.value.query.copy(followingOnly = on))
    }

    private fun loadOptions() {
        viewModelScope.launch {
            repository.referenceData().onSuccess { ref -> _state.update { it.copy(reference = ref) } }
            repository.sellers().onSuccess { list -> _state.update { it.copy(sellers = list) } }
        }
    }

    /** Finds sarees that look like this picture. The filters on screen still apply. */
    fun searchByImage(uri: String) {
        _state.update { it.copy(imageUri = uri) }
        reload()
    }

    fun clearImage() {
        _state.update { it.copy(imageUri = null) }
        reload()
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
        apply(CatalogQuery(q = q.q, sort = q.sort, followingOnly = q.followingOnly))
    }

    fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(refreshing = true, loadingMore = false, error = null) }
        loadJob = viewModelScope.launch { fetch(0) }
    }

    fun retry() = if (_state.value.noResults) reload() else loadNextPage()

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
            it.copy(items = emptyList(), collections = emptyList(), totalItems = 0, nextPage = 0, hasMore = false, loading = true, loadingMore = false, error = null)
        }
        loadJob = viewModelScope.launch { fetch(0) }
    }

    private suspend fun fetch(page: Int) {
        val photo = _state.value.imageUri
        if (photo != null) {
            repository.imageSearch(_state.value.query, photo, page).fold(
                onSuccess = { result ->
                    // distinctBy: photos uploaded while scrolling shift the pages, so one can show up twice.
                    val all = if (page == 0) result.items else (_state.value.items + result.items).distinctBy { c -> c.id }
                    showPage(page, result.totalItems, result.totalPages) { it.copy(items = all) }
                },
                onFailure = ::fail,
            )
        } else {
            repository.collections(_state.value.query, page).fold(
                onSuccess = { result ->
                    val all = if (page == 0) result.items else (_state.value.collections + result.items).distinctBy { c -> c.id }
                    showPage(page, result.totalItems, result.totalPages) { it.copy(collections = all) }
                },
                onFailure = ::fail,
            )
        }
    }

    private fun showPage(page: Int, total: Long, totalPages: Int, withResults: (CatalogState) -> CatalogState) {
        _state.update {
            withResults(it).copy(
                totalItems = total,
                nextPage = page + 1,
                hasMore = page + 1 < totalPages,
                loading = false,
                loadingMore = false,
                refreshing = false,
            )
        }
    }

    private fun fail(e: Throwable) {
        _state.update { it.copy(loading = false, loadingMore = false, refreshing = false, error = e.message) }
    }
}
