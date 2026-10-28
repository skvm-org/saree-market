package com.sareemarket.app.ui.reseller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.alerts.NewCollectionAlerts
import com.sareemarket.app.data.CartRepository
import com.sareemarket.app.data.NotificationRepository
import com.sareemarket.app.data.OrderRepository
import com.sareemarket.app.data.OrderSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The number on the Orders tab: order updates not yet seen, plus payments the customer still has to finish. */
internal fun orderBadge(summary: OrderSummary): Long = (summary.unread + summary.activeHolds).toLong()

/** What the customer screens share: the numbers on the bottom bar and bell, and the background check for news. */
@HiltViewModel
class ResellerShellViewModel @Inject constructor(
    private val notifications: NotificationRepository,
    private val alerts: NewCollectionAlerts,
    private val cart: CartRepository,
    private val orders: OrderRepository,
) : ViewModel() {

    /** New collections from followed sellers: the number on the bell. */
    val unread: StateFlow<Long> = notifications.unread

    val cartCount: StateFlow<Long> = cart.cart.map { it.lineCount.toLong() }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val orderBadge: StateFlow<Long> = orders.summary.map(::orderBadge).stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    fun refreshUnread() {
        viewModelScope.launch {
            notifications.refreshUnread()
            cart.refresh()
            orders.refreshSummary()
        }
    }

    /** Starts the 15-minute background check (it does nothing if it is already scheduled). */
    fun startAlerts() {
        viewModelScope.launch { alerts.schedule() }
    }
}
