package com.sareemarket.app.ui.reseller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.alerts.NewCollectionAlerts
import com.sareemarket.app.data.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the reseller screens share: the unread number on the bottom bar, and the background check for news. */
@HiltViewModel
class ResellerShellViewModel @Inject constructor(
    private val notifications: NotificationRepository,
    private val alerts: NewCollectionAlerts,
) : ViewModel() {

    val unread: StateFlow<Long> = notifications.unread

    fun refreshUnread() {
        viewModelScope.launch { notifications.refreshUnread() }
    }

    /** Starts the 15-minute background check (it does nothing if it is already scheduled). */
    fun startAlerts() {
        viewModelScope.launch { alerts.schedule() }
    }
}
