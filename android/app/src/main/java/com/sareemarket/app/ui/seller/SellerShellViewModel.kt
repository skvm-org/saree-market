package com.sareemarket.app.ui.seller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.AuthRepository
import com.sareemarket.app.data.ShopDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The shop's name and place, shown in the seller home header. Reloaded whenever the home screen comes back into view. */
@HiltViewModel
class SellerShellViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {

    private val _shop = MutableStateFlow<ShopDetails?>(null)
    val shop: StateFlow<ShopDetails?> = _shop.asStateFlow()

    fun reload() {
        viewModelScope.launch { repository.shopDetails().onSuccess { _shop.value = it } }
    }
}
