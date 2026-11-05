package com.sareemarket.app.alerts

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sareemarket.app.data.NotificationRepository
import com.sareemarket.app.data.OrderRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Runs about every 15 minutes while someone is logged in: a new order to confirm (seller), or an order update (either role)? */
@HiltWorker
class OrderAlertsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val orders: OrderRepository,
    private val notifications: NotificationRepository,
    private val prefs: AlertPrefs,
    private val alerts: NewCollectionAlerts,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val summary = orders.refreshSummary().getOrElse { return Result.retry() } // offline: try again later
        val (lastToShip, lastUnread) = prefs.orderMarks()
        AlertPlanner.orderText(lastToShip, lastUnread, summary)?.let { generic ->
            // A customer's alert can say what happened ("Lakshmi Silks shipped your order #12"); a seller has no such rows.
            val latest = notifications.page(0).getOrNull()?.items.orEmpty()
            alerts.showOrder(AlertPlanner.orderUpdateText(generic, latest))
        }
        // Remember the counts even when they went down, so the next increase is announced.
        prefs.setOrderMarks(summary.toShip, summary.unread)
        return Result.success()
    }
}
