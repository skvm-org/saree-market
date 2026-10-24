package com.sareemarket.app.alerts

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sareemarket.app.data.NotificationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant

/** Runs about every 15 minutes while a reseller is logged in: any new collection to announce? */
@HiltWorker
class NewCollectionsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifications: NotificationRepository,
    private val prefs: AlertPrefs,
    private val alerts: NewCollectionAlerts,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val since = prefs.lastNotified() ?: Instant.now().also { prefs.setLastNotified(it) }
        val page = notifications.page(0).getOrElse { return Result.retry() } // offline: try again later
        notifications.refreshUnread()
        val fresh = AlertPlanner.fresh(page.items, since)
        if (fresh.isNotEmpty()) {
            alerts.show(AlertPlanner.text(fresh))
            prefs.setLastNotified(AlertPlanner.newest(fresh))
        }
        return Result.success()
    }
}
