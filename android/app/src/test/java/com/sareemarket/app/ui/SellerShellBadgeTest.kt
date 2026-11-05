package com.sareemarket.app.ui

import com.sareemarket.app.data.OrderSummary
import com.sareemarket.app.ui.seller.sellerBadge
import org.junit.Assert.assertEquals
import org.junit.Test

class SellerShellBadgeTest {

    @Test
    fun theSellerBadgeCountsOrdersStillToShip() {
        assertEquals(3L, sellerBadge(OrderSummary(toShip = 3, unread = 9, shipped = 2)))
    }

    @Test
    fun nothingToShipMeansNoBadge() {
        assertEquals(0L, sellerBadge(OrderSummary(unread = 5, shipped = 2)))
    }
}
