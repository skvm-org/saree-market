package com.sareemarket.app.ui.seller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.alerts.NewCollectionAlerts
import com.sareemarket.app.data.AuthRepository
import com.sareemarket.app.data.BookingRepository
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

/** The shop's name and place, shown in the seller home header. Reloaded whenever the home screen comes back into view. */
@HiltViewModel
class SellerShellViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val bookings: BookingRepository,
    private val alerts: NewCollectionAlerts,
) : ViewModel() {

    /** Booking requests waiting for an answer: the number on the Requests tab. */
    val pending: StateFlow<Long> = bookings.summary.map { it.pending }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    fun refreshPending() {
        viewModelScope.launch { bookings.refreshSummary() }
    }

    /** Starts the 15-minute background check for new booking requests (it does nothing if it is already scheduled). */
    fun startAlerts() {
        viewModelScope.launch { alerts.scheduleForSeller() }
    }

    private val _shop = MutableStateFlow<ShopDetails?>(null)
    val shop: StateFlow<ShopDetails?> = _shop.asStateFlow()

    fun reload() {
        viewModelScope.launch { repository.shopDetails().onSuccess { _shop.value = it } }
    }
}
