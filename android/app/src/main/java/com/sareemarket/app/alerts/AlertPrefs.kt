package com.sareemarket.app.alerts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.alertStore: DataStore<Preferences> by preferencesDataStore(name = "alerts")

/** Remembers when the last Android notification was shown, so each new collection is announced once. */
@Singleton
class AlertPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val store = context.alertStore

    suspend fun lastNotified(): Instant? = store.data.first()[LAST]?.let { Instant.ofEpochMilli(it) }

    suspend fun setLastNotified(time: Instant) {
        store.edit { it[LAST] = time.toEpochMilli() }
    }

    /** What the last booking check saw: (requests waiting, answers unseen). */
    suspend fun bookingMarks(): Pair<Long, Long> = store.data.first().let { (it[PENDING_MARK] ?: 0L) to (it[UNREAD_MARK] ?: 0L) }

    suspend fun setBookingMarks(pending: Long, unread: Long) {
        store.edit {
            it[PENDING_MARK] = pending
            it[UNREAD_MARK] = unread
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val LAST = longPreferencesKey("last_notified")
        val PENDING_MARK = longPreferencesKey("booking_pending")
        val UNREAD_MARK = longPreferencesKey("booking_unread")
    }
}
