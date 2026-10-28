package com.sareemarket.app.ui.reseller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.alerts.NewCollectionAlerts
import com.sareemarket.app.data.BookingRepository
import com.sareemarket.app.data.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the reseller screens share: the unread number on the bottom bar, and the background check for news. */
@HiltViewModel
class ResellerShellViewModel @Inject constructor(
    private val notifications: NotificationRepository,
    private val alerts: NewCollectionAlerts,
    private val bookings: BookingRepository,
) : ViewModel() {

    val unread: StateFlow<Long> = notifications.unread

    /** Booking answers the reseller has not looked at yet: the number on the Bookings tab. */
    val bookingUnread: StateFlow<Long> = bookings.summary.map { it.unread }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    fun refreshUnread() {
        viewModelScope.launch {
            notifications.refreshUnread()
            bookings.refreshSummary()
        }
    }

    /** Starts the 15-minute background check (it does nothing if it is already scheduled). */
    fun startAlerts() {
        viewModelScope.launch { alerts.schedule() }
    }
}
