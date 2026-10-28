package com.sareemarket.app.alerts

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sareemarket.app.data.BookingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Runs about every 15 minutes while someone is logged in: a new booking request (seller) or an answer (reseller)? */
@HiltWorker
class BookingAlertsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val bookings: BookingRepository,
    private val prefs: AlertPrefs,
    private val alerts: NewCollectionAlerts,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val summary = bookings.refreshSummary().getOrElse { return Result.retry() } // offline: try again later
        val (lastPending, lastUnread) = prefs.bookingMarks()
        AlertPlanner.bookingText(lastPending, lastUnread, summary)?.let(alerts::showBooking)
        // Remember the counts even when they went down, so the next increase is announced.
        prefs.setBookingMarks(summary.pending, summary.unread)
        return Result.success()
    }
}
