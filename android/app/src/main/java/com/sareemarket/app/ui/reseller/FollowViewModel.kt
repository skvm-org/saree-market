package com.sareemarket.app.ui.reseller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.FollowRepository
import com.sareemarket.app.data.NotifyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FollowState(
    /** What the reseller just chose, per seller, before the screen's own data is reloaded. Null means unfollowed. */
    val chosen: Map<Long, NotifyMode?> = emptyMap(),
    val error: String? = null,
) {
    /** The mode to show: the reseller's latest choice, else what the server said when the screen loaded. */
    fun modeOf(sellerId: Long, fromServer: String?): NotifyMode? =
        if (chosen.containsKey(sellerId)) chosen[sellerId] else fromServer?.let { NotifyMode.valueOf(it) }
}

/** The Follow button on the seller page and on the item page. Flips at once and goes back if the server refuses. */
@HiltViewModel
class FollowViewModel @Inject constructor(private val repository: FollowRepository) : ViewModel() {

    private val _state = MutableStateFlow(FollowState())
    val state: StateFlow<FollowState> = _state.asStateFlow()

    fun toggle(sellerId: Long, current: NotifyMode?) {
        val target = if (current == null) NotifyMode.INSTANT else null
        _state.update { it.copy(chosen = it.chosen + (sellerId to target), error = null) }
        viewModelScope.launch {
            val result = if (target == null) repository.unfollow(sellerId) else repository.follow(sellerId, target)
            result.onFailure { e -> _state.update { it.copy(chosen = it.chosen + (sellerId to current), error = e.message) } }
        }
    }

    /** The bell on a seller page: turns notifications off or back on for a seller who is already followed. */
    fun toggleMute(sellerId: Long, current: NotifyMode?) {
        if (current == null) return
        val target = if (current == NotifyMode.MUTED) NotifyMode.INSTANT else NotifyMode.MUTED
        _state.update { it.copy(chosen = it.chosen + (sellerId to target), error = null) }
        viewModelScope.launch {
            repository.follow(sellerId, target).onFailure { e -> _state.update { it.copy(chosen = it.chosen + (sellerId to current), error = e.message) } }
        }
    }
}
