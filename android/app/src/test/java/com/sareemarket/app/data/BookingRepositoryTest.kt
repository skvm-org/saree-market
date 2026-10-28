package com.sareemarket.app.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingRepositoryTest {

    private val api = FakeBookingApi()
    private val repo = BookingRepository(api, testJson)

    @Test
    fun bookingSendsTheNoteAndRefreshesTheBadges() = runTest {
        api.summary = { BookingSummary(unread = 2) }

        val result = repo.book(10, "after 5 pm")

        assertEquals(listOf("book 10 after 5 pm", "summary"), api.calls)
        assertEquals(10L, result.getOrNull()?.item?.id)
        assertEquals(2L, repo.summary.value.unread)
    }

    @Test
    fun aBlankNoteIsNotSent() = runTest {
        repo.book(10, null)

        assertEquals("book 10 null", api.calls.first())
    }

    @Test
    fun aFailedActionKeepsTheServersMessageAndDoesNotRefresh() = runTest {
        api.onAction = { _, _ -> throw httpError(409, """{"code":"ITEM_NOT_AVAILABLE","message":"This saree is already on hold or sold out"}""") }

        val result = repo.accept(7)

        assertEquals("This saree is already on hold or sold out", result.exceptionOrNull()?.message)
        assertEquals("ITEM_NOT_AVAILABLE", (result.exceptionOrNull() as ApiFailure).code)
        assertEquals(listOf("accept 7"), api.calls)
    }

    @Test
    fun offlineBecomesANetworkError() = runTest {
        api.onMine = { _, _ -> throw networkError() }

        assertEquals("NETWORK", (repo.mine(null, 0).exceptionOrNull() as ApiFailure).code)
    }

    @Test
    fun everyActionCallsItsOwnEndpoint() = runTest {
        repo.accept(1)
        repo.reject(2)
        repo.extend(3)
        repo.sold(4)
        repo.cancel(5)

        assertEquals(listOf("accept 1", "reject 2", "extend 3", "sold 4", "cancel 5"), api.calls.filter { it != "summary" })
    }

    @Test
    fun listsPassTheFilterAndPage() = runTest {
        repo.mine(null, 0)
        repo.received(BookingStatus.ACCEPTED, 1)
        repo.received(null, 2)

        assertEquals(listOf("mine null 0", "received ACCEPTED 1", "received null 2"), api.calls)
    }

    @Test
    fun markingSeenClearsTheBadgeAtOnce() = runTest {
        api.summary = { BookingSummary(unread = 3) }
        repo.refreshSummary()

        repo.markSeen()

        assertEquals(0L, repo.summary.value.unread)
        assertTrue("seen" in api.calls)
    }

    @Test
    fun resetForgetsTheNumbers() = runTest {
        api.summary = { BookingSummary(pending = 4) }
        repo.refreshSummary()

        repo.reset()

        assertEquals(0L, repo.summary.value.pending)
    }

    @Test
    fun statusesThatAreOpenAreExactlyPendingAndAccepted() {
        assertEquals(setOf(BookingStatus.PENDING, BookingStatus.ACCEPTED), BookingStatus.entries.filter { it.isOpen }.toSet())
    }

    @Test
    fun theServersOnHoldStatusAndBookingFieldsDecode() {
        val detail = testJson.decodeFromString<ItemDetail>(
            """{"id":1,"collectionId":5,"collectionTitle":"Silks","sareeType":"Soft silk","fabric":"Pure silk","price":2500,
               "status":"ON_HOLD","seller":{"id":1,"shopName":"A","marketLocation":"B"},
               "myBooking":{"id":9,"status":"ACCEPTED","holdExpiresAt":"2026-10-29T10:00:00Z"}}""",
        )

        assertEquals(ItemStatus.ON_HOLD, detail.status)
        assertEquals(BookingStatus.ACCEPTED, detail.myBooking?.status)
        assertNull(testJson.decodeFromString<ItemDetail>(
            """{"id":1,"collectionId":5,"collectionTitle":"S","sareeType":"a","fabric":"b","price":1,"status":"AVAILABLE",
               "seller":{"id":1,"shopName":"A","marketLocation":"B"}}""",
        ).myBooking)
    }
}
