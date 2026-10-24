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

/** The 15-minute background check for new collections from followed sellers, and the notification it shows. */
@Singleton
class NewCollectionAlerts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AlertPrefs,
) {
    /** Schedules the check. It starts counting from now, so old news is not announced. Safe to call again. */
    suspend fun schedule() {
        if (prefs.lastNotified() == null) prefs.setLastNotified(Instant.now())
        val request = PeriodicWorkRequestBuilder<NewCollectionsWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Stops the check and forgets the last alert. Called on log out. */
    suspend fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        prefs.clear()
    }

    /** Shows one Android notification. Does nothing when the reseller has not allowed notifications. */
    fun show(text: String) {
        val allowed = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, "New collections", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_saree)
            .setContentTitle("Saree Market")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val WORK_NAME = "new-collection-alerts"
        const val CHANNEL_ID = "new_collections"
        const val NOTIFICATION_ID = 1
        const val INTERVAL_MINUTES = 15L // the shortest period WorkManager allows
    }
}
