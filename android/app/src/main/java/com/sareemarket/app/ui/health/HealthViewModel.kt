package com.sareemarket.app.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HealthUiState {
    data object Loading : HealthUiState
    data class Loaded(val status: String) : HealthUiState
    data class Failed(val message: String) : HealthUiState
}

@HiltViewModel
class HealthViewModel @Inject constructor(private val repository: HealthRepository) : ViewModel() {

    private val _state = MutableStateFlow<HealthUiState>(HealthUiState.Loading)
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = HealthUiState.Loading
        viewModelScope.launch {
            _state.value = repository.status().fold(
                onSuccess = { HealthUiState.Loaded(it) },
                onFailure = { HealthUiState.Failed(it.message ?: it.javaClass.simpleName) },
            )
        }
    }
}
