package com.sareemarket.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

@Singleton
class NotificationRepository @Inject constructor(private val api: NotificationsApi, private val json: Json) {

    private val _unread = MutableStateFlow(0L)

    /** How many notifications are unread: the number on the bell. Refreshed by [refreshUnread] and by reading. */
    val unread: StateFlow<Long> = _unread.asStateFlow()

    suspend fun page(page: Int): Result<NotificationPage> = apiCall(json) { api.notifications(page) }

    suspend fun refreshUnread(): Result<Long> = apiCall(json) { api.unreadCount().count }.onSuccess { _unread.value = it }

    suspend fun markRead(id: Long): Result<Unit> = apiCall(json) { api.markRead(id).requireSuccess() }.also { refreshUnread() }

    suspend fun markAllRead(): Result<Unit> = apiCall(json) { api.markAllRead().requireSuccess() }.also { refreshUnread() }

    /** Forgets the count when someone logs out, so the next account does not briefly see it. */
    fun reset() {
        _unread.value = 0
    }
}
