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

    /** Delivers an open hold to another saved address. Totals and the countdown do not change. */
    suspend fun changeHoldAddress(holdId: Long, addressId: Long): Result<Hold> =
        apiCall(json) { api.changeHoldAddress(holdId, HoldAddressBody(addressId)) }

    /** The customer paid: [utr] is the 12-digit reference from their UPI app, [proof] an optional screenshot (a URI string). */
    suspend fun pay(holdId: Long, utr: String, proof: String?): Result<Order> =
        changing { api.payHold(holdId, text(utr), proof?.let { proofPart(photos, it) }) }

    suspend fun mine(state: OrderStatus?, page: Int): Result<OrderPage> = apiCall(json) { api.mine(state?.name, page) }

    // ---- the seller's side ----

    suspend fun received(state: OrderStatus?, page: Int): Result<OrderPage> = apiCall(json) { api.received(state?.name, page) }

    /** The parcel is with the courier. Both values are required; the server tells the customer. */
    suspend fun ship(id: Long, courier: String, trackingNumber: String): Result<Order> =
        changing { api.ship(id, ShipBody(courier.trim(), trackingNumber.trim())) }

    /** The seller corrects a mistyped courier or tracking number on a shipped order. */
    suspend fun updateTracking(id: Long, courier: String, trackingNumber: String): Result<Order> =
        changing { api.updateTracking(id, ShipBody(courier.trim(), trackingNumber.trim())) }

    suspend fun cancelAsSeller(id: Long, reason: String): Result<Order> = changing { api.cancel(id, CancelBody(reason.trim())) }

    suspend fun order(id: Long): Result<Order> = apiCall(json) { api.order(id) }

    /** The customer cancels a confirmed order; [reason] is optional. The app does not refund: that is settled with the seller. */
    suspend fun cancel(id: Long, reason: String? = null): Result<Order> =
        changing { api.cancel(id, CancelBody(reason?.trim()?.ifEmpty { null })) }

    /** The customer got the parcel. */
    suspend fun receive(id: Long): Result<Order> = changing { api.receive(id) }

    /** The customer has looked at their orders: clears the badge. */
    suspend fun markSeen(): Result<Unit> = apiCall(json) { api.seen().requireSuccess() }.also {
        if (it.isSuccess) _summary.value = _summary.value.copy(unread = 0)
    }

    private fun text(value: String): RequestBody = value.toRequestBody("text/plain".toMediaType())

    private suspend fun <T> changing(block: suspend () -> T): Result<T> =
        apiCall(json, block).also { if (it.isSuccess) refreshSummary() }
}
