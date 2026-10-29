package com.sareemarket.app.alerts

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sareemarket.app.data.OrderRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Runs about every 15 minutes while someone is logged in: a new order to confirm (seller), or an order update (either role)? */
@HiltWorker
class OrderAlertsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val orders: OrderRepository,
    private val prefs: AlertPrefs,
    private val alerts: NewCollectionAlerts,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val summary = orders.refreshSummary().getOrElse { return Result.retry() } // offline: try again later
        val (lastToConfirm, lastUnread) = prefs.orderMarks()
        AlertPlanner.orderText(lastToConfirm, lastUnread, summary)?.let(alerts::showOrder)
        // Remember the counts even when they went down, so the next increase is announced.
        prefs.setOrderMarks(summary.toConfirm, summary.unread)
        return Result.success()
    }
}
