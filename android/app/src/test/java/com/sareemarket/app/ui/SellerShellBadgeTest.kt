package com.sareemarket.app.ui

import com.sareemarket.app.data.OrderSummary
import com.sareemarket.app.ui.seller.sellerBadge
import org.junit.Assert.assertEquals
import org.junit.Test

class SellerShellBadgeTest {

    @Test
    fun theSellerBadgeCountsOrdersWaitingForConfirmation() {
        assertEquals(3L, sellerBadge(OrderSummary(toConfirm = 3, unread = 9, toPack = 2)))
    }

    @Test
    fun nothingToConfirmMeansNoBadge() {
        assertEquals(0L, sellerBadge(OrderSummary(unread = 5, toPack = 2, toShip = 1)))
    }
}
