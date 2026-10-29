package com.sareemarket.app.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sareemarket.app.MainActivity
import com.sareemarket.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The 15-minute background checks and the Android notifications they show: new collections from followed sellers
 * (customers), and new orders or order updates (both roles).
 */
@Singleton
class NewCollectionAlerts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AlertPrefs,
) {
    /** Schedules the checks for a customer. They start counting from now, so old news is not announced. Safe to call again. */
    suspend fun schedule() {
        if (prefs.lastNotified() == null) prefs.setLastNotified(Instant.now())
        dropLegacyBookingChecks()
        enqueue<NewCollectionsWorker>(WORK_NAME)
        enqueue<OrderAlertsWorker>(ORDER_WORK_NAME)
    }

    /** A seller has no followers to hear from; only orders are checked. */
    suspend fun scheduleForSeller() {
        dropLegacyBookingChecks()
        enqueue<OrderAlertsWorker>(ORDER_WORK_NAME)
    }

    /** Older app versions scheduled a booking check whose class no longer exists; stop it so WorkManager never tries to run it. */
    private fun dropLegacyBookingChecks() {
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_BOOKING_WORK_NAME)
    }

    private inline fun <reified W : androidx.work.ListenableWorker> enqueue(name: String) {
        val request = PeriodicWorkRequestBuilder<W>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Stops the checks and forgets the last alerts. Called on log out. */
    suspend fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(ORDER_WORK_NAME)
        dropLegacyBookingChecks()
        prefs.clear()
    }

    /** Shows one Android notification about new collections. Does nothing when notifications are not allowed. */
    fun show(text: String) = notify(text, CHANNEL_ID, "New collections", NOTIFICATION_ID)

    /** The same, for orders; a separate channel and id so the two never replace each other. */
    fun showOrder(text: String) = notify(text, ORDER_CHANNEL_ID, "Orders", ORDER_NOTIFICATION_ID)

    private fun notify(text: String, channelId: String, channelName: String, id: Int) {
        val allowed = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_saree)
            .setContentTitle("Saree Market")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private companion object {
        const val WORK_NAME = "new-collection-alerts"
        const val CHANNEL_ID = "new_collections"
        const val NOTIFICATION_ID = 1
        const val ORDER_WORK_NAME = "order-alerts"
        const val ORDER_CHANNEL_ID = "orders"
        const val ORDER_NOTIFICATION_ID = 3
        const val LEGACY_BOOKING_WORK_NAME = "booking-alerts"
        const val INTERVAL_MINUTES = 15L // the shortest period WorkManager allows
    }
}
