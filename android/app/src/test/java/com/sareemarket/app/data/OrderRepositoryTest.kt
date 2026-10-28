package com.sareemarket.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderRepositoryTest {

    private val api = FakeOrderApi()
    private val photos = FakePhotoSource(mapOf("content://proof/1" to ByteArray(10)))
    private val repo = OrderRepository(api, photos, testJson)

    @Test
    fun checkoutReturnsHoldsAndAdjustmentsAndRefreshesTheSummary() = runTest {
        api.onCheckout = {
            CheckoutResult(listOf(hold(3)), listOf(LineAdjustment(10, "Diwali silks", 4, 2)), emptyList())
        }
        api.summary = { OrderSummary(activeHolds = 1) }

        val result = repo.checkout(1, "after 5", 9).getOrThrow()

        assertEquals(3L, result.holds.single().id)
        assertEquals(2, result.adjustments.single().reserved)
        assertEquals(listOf("checkout 1 after 5 9", "summary"), api.calls)
        assertEquals(1, repo.summary.value.activeHolds)
    }

    @Test
    fun payWithoutAProofSendsOnlyTheUtr() = runTest {
        repo.pay(3, "412345678901", null)

        assertEquals("payHold 3 false", api.calls.first())
    }

    @Test
    fun payWithAProofSendsTheImagePart() = runTest {
        var sentUtr = ""
        api.onPay = { _, utr, _ -> sentUtr = utr; order(5) }

        repo.pay(3, "412345678901", "content://proof/1")

        assertEquals("payHold 3 true", api.calls.first())
        assertEquals("412345678901", sentUtr)
    }

    @Test
    fun resubmitUsesTheOrderRoute() = runTest {
        repo.resubmit(5, "412345678902", null)

        assertEquals("resubmit 5 false", api.calls.first())
    }

    @Test
    fun serverErrorsBecomeApiFailuresWithTheirCode() = runTest {
        api.onCheckout = { throw httpError(409, """{"code":"HOLD_LIMIT","message":"You can have 3 payments in progress"}""") }
        api.onPay = { _, _, _ -> throw httpError(409, """{"code":"UTR_IN_USE","message":"That UTR is already used on another order"}""") }

        assertEquals("HOLD_LIMIT", (repo.checkout(1, null, null).exceptionOrNull() as ApiFailure).code)
        assertEquals("UTR_IN_USE", (repo.pay(3, "412345678901", null).exceptionOrNull() as ApiFailure).code)
    }

    @Test
    fun markSeenZeroesUnreadInTheSummary() = runTest {
        api.summary = { OrderSummary(unread = 4) }
        repo.refreshSummary()

        repo.markSeen()

        assertEquals(0, repo.summary.value.unread)
    }

    @Test
    fun resetClearsTheSummary() = runTest {
        api.summary = { OrderSummary(unread = 4, activeHolds = 1) }
        repo.refreshSummary()

        repo.reset()

        assertEquals(OrderSummary(), repo.summary.value)
    }

    @Test
    fun cancelRefreshesTheSummary() = runTest {
        val result = repo.cancel(5)

        assertEquals(OrderStatus.CANCELLED, result.getOrNull()?.status)
        assertEquals(listOf("cancel 5", "summary"), api.calls)
    }

    @Test
    fun mineSendsTheStateFilter() = runTest {
        repo.mine(OrderStatus.SHIPPED, 1)

        assertEquals("mine SHIPPED 1", api.calls.single())
    }
}
