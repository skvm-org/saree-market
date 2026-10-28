package com.sareemarket.app.ui

import com.sareemarket.app.data.OrderSummary
import com.sareemarket.app.ui.reseller.orderBadge
import org.junit.Assert.assertEquals
import org.junit.Test

class ShellBadgesTest {

    @Test
    fun theOrdersBadgeAddsUnreadUpdatesAndPaymentsInProgress() {
        assertEquals(5L, orderBadge(OrderSummary(unread = 3, activeHolds = 2)))
    }

    @Test
    fun theOrdersBadgeIgnoresTheSellerOnlyCounts() {
        assertEquals(0L, orderBadge(OrderSummary(toConfirm = 4, toPack = 2, toShip = 1, shipped = 9)))
    }

    @Test
    fun nothingGoingOnMeansNoBadge() {
        assertEquals(0L, orderBadge(OrderSummary()))
    }
}
