package com.sareemarket.app.alerts

import org.junit.Assert.assertNull
import com.sareemarket.app.data.OrderSummary
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

    @Test
    fun announcesNewOrdersToConfirm() {
        assertEquals("1 new order to confirm", AlertPlanner.orderText(2, 0, OrderSummary(toConfirm = 3)))
        assertEquals("3 new orders to confirm", AlertPlanner.orderText(0, 0, OrderSummary(toConfirm = 3)))
    }

    @Test
    fun announcesAnUpdateWhenTheUnreadCountRises() {
        assertEquals("An order was updated", AlertPlanner.orderText(0, 1, OrderSummary(unread = 2)))
    }

    @Test
    fun anOrderUpdateAlertSaysWhatTheSellerDid() {
        val confirmed = note(1, type = NotificationType.ORDER_UPDATE, updatedAt = "2026-11-03T10:00:00Z", message = "Lakshmi Silks confirmed your order #12")
        val packed = note(2, type = NotificationType.ORDER_UPDATE, updatedAt = "2026-11-03T11:00:00Z", message = "Lakshmi Silks packed your order #12")
        val old = note(3, read = true, type = NotificationType.ORDER_UPDATE, updatedAt = "2026-11-03T12:00:00Z", message = "Lakshmi Silks shipped your order #9")
        val follow = note(4, updatedAt = "2026-11-03T13:00:00Z")

        assertEquals("Lakshmi Silks packed your order #12", AlertPlanner.orderUpdateText(AlertPlanner.ORDER_UPDATED, listOf(confirmed, packed, old, follow)))
    }

    @Test
    fun theGenericOrderTextStaysWhenThereIsNothingSpecificToSay() {
        assertEquals(AlertPlanner.ORDER_UPDATED, AlertPlanner.orderUpdateText(AlertPlanner.ORDER_UPDATED, emptyList()))
        assertEquals("2 new orders to confirm", AlertPlanner.orderUpdateText("2 new orders to confirm", listOf(note(1, type = NotificationType.ORDER_UPDATE))))
    }

    @Test
    fun newOrdersWinOverUpdates() {
        assertEquals("2 new orders to confirm", AlertPlanner.orderText(0, 0, OrderSummary(toConfirm = 2, unread = 5)))
    }

    @Test
    fun staysQuietWhenNothingRose() {
        assertNull(AlertPlanner.orderText(3, 2, OrderSummary(toConfirm = 3, unread = 2)))
        assertNull(AlertPlanner.orderText(0, 0, OrderSummary()))
    }

    @Test
    fun staysQuietWhenCountsFell() {
        // answered or seen in the app: the next rise is announced against the lower mark
        assertNull(AlertPlanner.orderText(3, 2, OrderSummary(toConfirm = 1, unread = 0)))
    }
}
