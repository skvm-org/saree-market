package com.sareemarket.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.AuthRepository
import com.sareemarket.app.data.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppState {
    data object Loading : AppState
    data object LoggedOut : AppState
    data class LoggedIn(val session: Session) : AppState
}

@HiltViewModel
class AppViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {

    val state: StateFlow<AppState> = repository.session
        .map<Session?, AppState> { session -> session?.let(AppState::LoggedIn) ?: AppState.LoggedOut }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)

    init {
        // A saved token can be stale (expired, or the account was disabled): check it once per launch.
        viewModelScope.launch {
            if (repository.session.first() != null) repository.refreshSession()
        }
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }
}
