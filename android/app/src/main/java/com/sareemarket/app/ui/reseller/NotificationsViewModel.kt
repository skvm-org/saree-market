package com.sareemarket.app.ui.reseller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.AppNotification
import com.sareemarket.app.data.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsState(
    val items: List<AppNotification> = emptyList(),
    val nextPage: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    val hasUnread: Boolean get() = items.any { !it.read }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(private val repository: NotificationRepository) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    /** Loads the newest page again. Called each time the screen is shown. */
    fun refresh() {
        viewModelScope.launch { fetch(0) }
        viewModelScope.launch { repository.refreshUnread() }
    }

    /** Pull-to-refresh: shows the indicator while the newest page reloads. */
    fun pullRefresh() {
        _state.update { it.copy(refreshing = true) }
        refresh()
    }

    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.loading || s.loadingMore) return
        _state.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch { fetch(s.nextPage) }
    }

    /** Tapping one marks it read at once; the number on the bell then catches up with the server. */
    fun open(notification: AppNotification) {
        if (notification.read) return
        _state.update { s -> s.copy(items = s.items.map { if (it.id == notification.id) it.copy(read = true) else it }) }
        viewModelScope.launch {
            repository.markRead(notification.id).onFailure { e ->
                _state.update { s -> s.copy(items = s.items.map { if (it.id == notification.id) it.copy(read = false) else it }, error = e.message) }
            }
        }
    }

    fun markAllRead() {
        val before = _state.value.items
        _state.update { s -> s.copy(items = s.items.map { it.copy(read = true) }) }
        viewModelScope.launch {
            repository.markAllRead().onFailure { e -> _state.update { it.copy(items = before, error = e.message) } }
        }
    }

    private suspend fun fetch(page: Int) {
        repository.page(page).fold(
            onSuccess = { result ->
                _state.update {
                    it.copy(
                        // distinctBy: a notification that grew while the reseller scrolled can move between pages.
                        items = if (page == 0) result.items else (it.items + result.items).distinctBy { n -> n.id },
                        nextPage = page + 1,
                        hasMore = page + 1 < result.totalPages,
                        loading = false,
                        loadingMore = false,
                        refreshing = false,
                        error = null,
                    )
                }
            },
            onFailure = { e -> _state.update { it.copy(loading = false, loadingMore = false, refreshing = false, error = e.message) } },
        )
    }
}
