package com.sareemarket.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Checkout, payment holds and orders. [summary] feeds the Orders badge (unread updates and payments in progress); it
 * is refreshed after every change and by the shell's timer.
 */
@Singleton
class OrderRepository @Inject constructor(
    private val api: OrderApi,
    private val photos: PhotoSource,
    private val json: Json,
) {

    private val _summary = MutableStateFlow(OrderSummary())
    val summary: StateFlow<OrderSummary> = _summary.asStateFlow()

    suspend fun refreshSummary(): Result<OrderSummary> = apiCall(json) { api.summary() }.onSuccess { _summary.value = it }

    /** Forgets the numbers on log out. */
    fun reset() {
        _summary.value = OrderSummary()
    }

    suspend fun checkout(addressId: Long, note: String?, sellerId: Long?): Result<CheckoutResult> =
        changing { api.checkout(CheckoutBody(addressId, note?.trim()?.ifEmpty { null }, sellerId)) }

    suspend fun holds(): Result<List<Hold>> = apiCall(json) { api.holds() }

    suspend fun cancelHold(id: Long): Result<Unit> = changing { api.cancelHold(id).requireSuccess() }

    /** The customer paid: [utr] is the 12-digit reference from their UPI app, [proof] an optional screenshot (a URI string). */
    suspend fun pay(holdId: Long, utr: String, proof: String?): Result<Order> =
        changing { api.payHold(holdId, text(utr), proof?.let { proofPart(photos, it) }) }

    /** After a rejection: a corrected UTR and, optionally, a new screenshot. */
    suspend fun resubmit(orderId: Long, utr: String, proof: String?): Result<Order> =
        changing { api.resubmit(orderId, text(utr), proof?.let { proofPart(photos, it) }) }

    suspend fun mine(state: OrderStatus?, page: Int): Result<OrderPage> = apiCall(json) { api.mine(state?.name, page) }

    // ---- the seller's side ----

    suspend fun received(state: OrderStatus?, page: Int): Result<OrderPage> = apiCall(json) { api.received(state?.name, page) }

    suspend fun confirmPayment(id: Long): Result<Order> = changing { api.confirmPayment(id) }

    suspend fun rejectPayment(id: Long, reason: String): Result<Order> = changing { api.rejectPayment(id, RejectBody(reason.trim())) }

    suspend fun pack(id: Long): Result<Order> = changing { api.pack(id) }

    /** Courier and tracking number are optional; blank ones are sent as nothing. */
    suspend fun ship(id: Long, courier: String?, trackingNumber: String?): Result<Order> =
        changing { api.ship(id, ShipBody(courier?.trim()?.ifEmpty { null }, trackingNumber?.trim()?.ifEmpty { null })) }

    suspend fun deliver(id: Long): Result<Order> = changing { api.deliver(id) }

    suspend fun cancelAsSeller(id: Long, reason: String): Result<Order> = changing { api.cancelWithReason(id, CancelBody(reason.trim())) }

    suspend fun order(id: Long): Result<Order> = apiCall(json) { api.order(id) }

    suspend fun cancel(id: Long): Result<Order> = changing { api.cancel(id) }

    /** The customer has looked at their orders: clears the badge. */
    suspend fun markSeen(): Result<Unit> = apiCall(json) { api.seen().requireSuccess() }.also {
        if (it.isSuccess) _summary.value = _summary.value.copy(unread = 0)
    }

    private fun text(value: String): RequestBody = value.toRequestBody("text/plain".toMediaType())

    private suspend fun <T> changing(block: suspend () -> T): Result<T> =
        apiCall(json, block).also { if (it.isSuccess) refreshSummary() }
}
