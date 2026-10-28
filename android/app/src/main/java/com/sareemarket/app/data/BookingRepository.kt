package com.sareemarket.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Booking requests for both roles. [summary] feeds the badges: a seller's pending count and a reseller's unread
 * count. It is refreshed after every change and by the screens' timers.
 */
@Singleton
class BookingRepository @Inject constructor(private val api: BookingApi, private val json: Json) {

    private val _summary = MutableStateFlow(BookingSummary())
    val summary: StateFlow<BookingSummary> = _summary.asStateFlow()

    suspend fun refreshSummary(): Result<BookingSummary> = apiCall(json) { api.summary() }.onSuccess { _summary.value = it }

    /** Forgets the numbers on log out, so the next account does not briefly see them. */
    fun reset() {
        _summary.value = BookingSummary()
    }

    suspend fun book(itemId: Long, note: String?): Result<Booking> = changing { api.book(itemId, BookingBody(note)) }

    suspend fun cancel(id: Long): Result<Booking> = changing { api.cancel(id) }

    suspend fun accept(id: Long): Result<Booking> = changing { api.accept(id) }

    suspend fun reject(id: Long): Result<Booking> = changing { api.reject(id) }

    suspend fun extend(id: Long): Result<Booking> = changing { api.extend(id) }

    suspend fun sold(id: Long): Result<Booking> = changing { api.sold(id) }

    suspend fun mine(filter: BookingFilter?, page: Int): Result<BookingPage> = apiCall(json) { api.mine(filter?.name, page) }

    suspend fun received(state: BookingStatus?, page: Int): Result<BookingPage> = apiCall(json) { api.received(state?.name, page) }

    /** The reseller has looked at their requests: clears the badge. */
    suspend fun markSeen(): Result<Unit> = apiCall(json) { api.seen().check() }.also {
        if (it.isSuccess) _summary.value = _summary.value.copy(unread = 0)
    }

    private fun retrofit2.Response<Unit>.check() {
        if (!isSuccessful) throw retrofit2.HttpException(this)
    }

    private suspend fun <T> changing(block: suspend () -> T): Result<T> =
        apiCall(json, block).also { if (it.isSuccess) refreshSummary() }
}
