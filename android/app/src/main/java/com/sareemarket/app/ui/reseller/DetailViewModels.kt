package com.sareemarket.app.ui.reseller

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.CatalogRepository
import com.sareemarket.app.data.CollectionDetail
import com.sareemarket.app.data.ItemDetail
import com.sareemarket.app.data.SellerPage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A read-only screen that loads one thing by the id in its navigation route. */
data class LoadState<T>(val value: T? = null, val loading: Boolean = true, val error: String? = null)

abstract class LoadViewModel<T>(private val load: suspend () -> Result<T>) : ViewModel() {

    private val _state = MutableStateFlow(LoadState<T>())
    val state: StateFlow<LoadState<T>> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            load().fold(
                onSuccess = { v -> _state.value = LoadState(v, loading = false) },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }
}

@HiltViewModel
class ItemDetailViewModel @Inject constructor(handle: SavedStateHandle, repository: CatalogRepository) :
    LoadViewModel<ItemDetail>({ repository.item(checkNotNull(handle["id"])) })

@HiltViewModel
class SellerPageViewModel @Inject constructor(handle: SavedStateHandle, repository: CatalogRepository) :
    LoadViewModel<SellerPage>({ repository.seller(checkNotNull(handle["id"])) })

@HiltViewModel
class ResellerCollectionViewModel @Inject constructor(handle: SavedStateHandle, repository: CatalogRepository) :
    LoadViewModel<CollectionDetail>({ repository.collection(checkNotNull(handle["id"])) })
