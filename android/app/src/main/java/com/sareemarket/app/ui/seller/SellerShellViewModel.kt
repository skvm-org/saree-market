package com.sareemarket.app.ui.seller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.alerts.NewCollectionAlerts
import com.sareemarket.app.data.AuthRepository
import com.sareemarket.app.data.OrderRepository
import com.sareemarket.app.data.OrderSummary
import com.sareemarket.app.data.ShopDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The number on the Orders tab: only orders that need the seller to act, not every update. */
internal fun sellerBadge(summary: OrderSummary): Long = summary.toConfirm.toLong()

/** The shop's name and place, shown in the seller home header. Reloaded whenever the home screen comes back into view. */
@HiltViewModel
class SellerShellViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val orders: OrderRepository,
    private val alerts: NewCollectionAlerts,
) : ViewModel() {

    /** Orders whose payment waits for the seller to confirm: the number on the Orders tab. */
    val pending: StateFlow<Long> = orders.summary.map(::sellerBadge).stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    fun refreshPending() {
        viewModelScope.launch { orders.refreshSummary() }
    }

    /** Starts the 15-minute background check for new orders (it does nothing if it is already scheduled). */
    fun startAlerts() {
        viewModelScope.launch { alerts.scheduleForSeller() }
    }

    private val _shop = MutableStateFlow<ShopDetails?>(null)
    val shop: StateFlow<ShopDetails?> = _shop.asStateFlow()

    fun reload() {
        viewModelScope.launch { repository.shopDetails().onSuccess { _shop.value = it } }
    }
}
