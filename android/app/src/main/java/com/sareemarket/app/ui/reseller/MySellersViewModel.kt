package com.sareemarket.app.ui.reseller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.FollowItem
import com.sareemarket.app.data.FollowRepository
import com.sareemarket.app.data.NotifyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MySellersState(
    val sellers: List<FollowItem> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** The seller just unfollowed, kept briefly so the screen can offer Undo. */
    val undo: FollowItem? = null,
    val error: String? = null,
)

@HiltViewModel
class MySellersViewModel @Inject constructor(private val repository: FollowRepository) : ViewModel() {

    private val _state = MutableStateFlow(MySellersState())
    val state: StateFlow<MySellersState> = _state.asStateFlow()

    /** Reloads the list. Called each time the screen is shown, since follows can change on other screens. */
    fun load() {
        viewModelScope.launch {
            repository.follows().fold(
                onSuccess = { list -> _state.update { it.copy(sellers = list, loading = false, refreshing = false, error = null) } },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.message.takeIf { _ -> it.sellers.isEmpty() }) } },
            )
        }
    }

    /** Pull-to-refresh: shows the indicator while the list reloads. */
    fun pullRefresh() {
        _state.update { it.copy(refreshing = true) }
        load()
    }

    /** Muted keeps the follow (and the Following tab) but sends no notifications. */
    fun setMuted(sellerId: Long, muted: Boolean) {
        val mode = if (muted) NotifyMode.MUTED else NotifyMode.INSTANT
        val before = _state.value.sellers
        _state.update { s -> s.copy(sellers = s.sellers.map { if (it.sellerId == sellerId) it.copy(notifyMode = mode) else it }) }
        viewModelScope.launch {
            repository.follow(sellerId, mode).onFailure { e -> _state.update { it.copy(sellers = before, error = e.message) } }
        }
    }

    fun unfollow(sellerId: Long) {
        val before = _state.value.sellers
        val removed = before.firstOrNull { it.sellerId == sellerId } ?: return
        _state.update { s -> s.copy(sellers = s.sellers.filterNot { it.sellerId == sellerId }, undo = removed) }
        viewModelScope.launch {
            repository.unfollow(sellerId).onFailure { e -> _state.update { it.copy(sellers = before, undo = null, error = e.message) } }
        }
    }

    /** Follows the seller again with the notification setting they had. */
    fun undoUnfollow() {
        val removed = _state.value.undo ?: return
        _state.update { it.copy(undo = null) }
        viewModelScope.launch {
            repository.follow(removed.sellerId, removed.notifyMode).fold(
                onSuccess = { load() },
                onFailure = { e -> _state.update { it.copy(error = e.message) } },
            )
        }
    }

    fun dismissUndo() = _state.update { it.copy(undo = null) }

    fun clearError() = _state.update { it.copy(error = null) }
}
