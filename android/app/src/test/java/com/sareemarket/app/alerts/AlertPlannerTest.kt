package com.sareemarket.app.alerts

import com.sareemarket.app.data.NotificationType
import com.sareemarket.app.data.note
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPlannerTest {

    private val since = Instant.parse("2026-10-25T10:00:00Z")

    @Test
    fun onlyUnreadNewCollectionsAfterTheLastAlertCount() {
        val items = listOf(
            note(1, updatedAt = "2026-10-25T10:05:00Z"), // new
            note(2, updatedAt = "2026-10-25T09:59:59Z"), // announced already
            note(3, updatedAt = "2026-10-25T10:00:00Z"), // exactly at the last alert: announced already
            note(4, read = true, updatedAt = "2026-10-25T10:06:00Z"), // read in the app
            note(5, type = NotificationType.ITEMS_ADDED, updatedAt = "2026-10-25T10:07:00Z"), // only new collections
            note(6, type = NotificationType.BACK_IN_STOCK, updatedAt = "2026-10-25T10:08:00Z"),
        )

        assertEquals(listOf(1L), AlertPlanner.fresh(items, since).map { it.id })
    }

    @Test
    fun nothingNewMeansNoAlert() {
        assertTrue(AlertPlanner.fresh(emptyList(), since).isEmpty())
        assertTrue(AlertPlanner.fresh(listOf(note(1, updatedAt = "2026-10-24T10:00:00Z")), since).isEmpty())
    }

    @Test
    fun oneCollectionShowsItsOwnMessage() {
        val one = listOf(note(1, message = "Lakshmi Silks added 18 sarees · Diwali soft silks", updatedAt = "2026-10-25T10:05:00Z"))

        assertEquals("Lakshmi Silks added 18 sarees · Diwali soft silks", AlertPlanner.text(one))
    }

    @Test
    fun severalCollectionsAreSummedUp() {
        val three = (1..3L).map { note(it, updatedAt = "2026-10-25T10:05:00Z") }

        assertEquals("3 new collections from sellers you follow", AlertPlanner.text(three))
    }

    @Test
    fun theNewestTimeIsWhatIsRemembered() {
        val fresh = listOf(
            note(1, updatedAt = "2026-10-25T10:05:00Z"),
            note(2, updatedAt = "2026-10-25T10:20:00Z"),
            note(3, updatedAt = "2026-10-25T10:10:00Z"),
        )

        assertEquals(Instant.parse("2026-10-25T10:20:00Z"), AlertPlanner.newest(fresh))
        // after remembering it, the same notifications are not announced again
        assertTrue(AlertPlanner.fresh(fresh, AlertPlanner.newest(fresh)).isEmpty())
    }
}
