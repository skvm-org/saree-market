package com.sareemarket.app.ui.reseller

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.CartRepository
import com.sareemarket.app.data.WishlistRepository
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

/**
 * The saree page and its neighbours: the item that was opened, then the other photos of the same collection so the
 * reseller can swipe between them. Each photo's details load when its page comes near the screen.
 */
data class PagerState(
    /** The photos of the collection in order, or just the opened one until (or unless) the collection loads. */
    val ids: List<Long> = emptyList(),
    val startIndex: Int = 0,
    val details: Map<Long, LoadState<ItemDetail>> = emptyMap(),
    /** True while a cart or wishlist change is on its way. */
    val shoppingBusy: Boolean = false,
    val shoppingError: String? = null,
)

@HiltViewModel
class ItemPagerViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repository: CatalogRepository,
    private val cart: CartRepository,
    private val wishlist: WishlistRepository,
) : ViewModel() {

    private val openedId: Long = checkNotNull(handle["id"])

    private val _state = MutableStateFlow(PagerState(ids = listOf(openedId)))
    val state: StateFlow<PagerState> = _state.asStateFlow()

    init {
        _state.update { it.copy(details = it.details + (openedId to LoadState(loading = true))) }
        viewModelScope.launch {
            val opened = fetch(openedId) ?: return@launch
            repository.collection(opened.collectionId).onSuccess { c ->
                val ids = c.items.map { it.id }
                if (openedId in ids) _state.update { it.copy(ids = ids, startIndex = ids.indexOf(openedId)) }
            }
        }
    }

    /** Loads one photo's details unless they are already loaded or loading. */
    fun ensure(id: Long) {
        if (_state.value.details[id]?.let { it.value != null || it.loading } == true) return
        _state.update { it.copy(details = it.details + (id to LoadState(loading = true))) }
        viewModelScope.launch { fetch(id) }
    }

    /** Re-reads photos that are already showing, so a cart or wishlist change made elsewhere (or stock that moved) is not stale. */
    fun refresh(ids: List<Long>) {
        ids.forEach { id -> if (_state.value.details[id]?.value != null) viewModelScope.launch { fetch(id) } }
    }

    private suspend fun fetch(id: Long): ItemDetail? = repository.item(id).fold(
        onSuccess = { d ->
            _state.update { it.copy(details = it.details + (id to LoadState(d, loading = false))) }
            d
        },
        onFailure = { e ->
            _state.update { it.copy(details = it.details + (id to LoadState(loading = false, error = e.message))) }
            null
        },
    )

    /** Puts [quantity] of this photo in the cart (or changes it). The page reloads either way, since its stock may have changed. */
    /** [onSaved] runs only when the save worked ("Buy now" opens the cart then). */
    fun setCartQuantity(id: Long, quantity: Int, onSaved: () -> Unit = {}) = shopping(id, onSaved) { cart.setQuantity(id, quantity) }

    fun removeFromCart(id: Long) = shopping(id) { cart.remove(id) }

    /** Saves the photo to the wishlist, or takes it off when it is already saved. */
    fun toggleWishlist(id: Long) {
        val saved = _state.value.details[id]?.value?.inWishlist ?: return
        shopping(id) { if (saved) wishlist.remove(id) else wishlist.add(id) }
    }

    private fun shopping(id: Long, onSaved: () -> Unit = {}, call: suspend () -> Result<Unit>) {
        if (_state.value.shoppingBusy) return
        _state.update { it.copy(shoppingBusy = true, shoppingError = null) }
        viewModelScope.launch {
            val error = call().exceptionOrNull()?.message
            fetch(id)
            _state.update { it.copy(shoppingBusy = false, shoppingError = error) }
            if (error == null) onSaved()
        }
    }

    fun retry(id: Long) {
        _state.update { it.copy(details = it.details - id) }
        ensure(id)
    }
}

@HiltViewModel
class SellerPageViewModel @Inject constructor(handle: SavedStateHandle, repository: CatalogRepository) :
    LoadViewModel<SellerPage>({ repository.seller(checkNotNull(handle["id"])) })

@HiltViewModel
class ResellerCollectionViewModel @Inject constructor(handle: SavedStateHandle, repository: CatalogRepository) :
    LoadViewModel<CollectionDetail>({ repository.collection(checkNotNull(handle["id"])) })
