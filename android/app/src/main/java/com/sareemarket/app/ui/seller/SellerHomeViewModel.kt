package com.sareemarket.app.ui.seller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.CollectionRepository
import com.sareemarket.app.data.CollectionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SellerHomeState {
    data object Loading : SellerHomeState
    data class Loaded(val collections: List<CollectionSummary>) : SellerHomeState
    data class Failed(val message: String) : SellerHomeState
}

@HiltViewModel
class SellerHomeViewModel @Inject constructor(private val repository: CollectionRepository) : ViewModel() {

    private val _state = MutableStateFlow<SellerHomeState>(SellerHomeState.Loading)
    val state: StateFlow<SellerHomeState> = _state.asStateFlow()

    /** Called whenever the screen comes back into view, so counts are fresh after editing a collection. */
    fun refresh() {
        // Keep showing the old list while reloading; only a first load or a failure shows the spinner.
        if (_state.value is SellerHomeState.Failed) _state.value = SellerHomeState.Loading
        viewModelScope.launch {
            repository.myCollections().fold(
                onSuccess = { _state.value = SellerHomeState.Loaded(it) },
                onFailure = {
                    if (_state.value !is SellerHomeState.Loaded) {
                        _state.value = SellerHomeState.Failed(it.message.orEmpty())
                    }
                },
            )
        }
    }
}
