package com.sareemarket.app.ui

import com.sareemarket.app.data.ShopDetails
import com.sareemarket.app.ui.components.orderProofUrl
import com.sareemarket.app.ui.seller.shopHasUpiId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SellerShopHelpersTest {

    private fun shop(upi: String?) = ShopDetails("Lakshmi Silks", "Dindigul", "9876500001", null, upi, 60)

    @Test
    fun aLoadedShopWithNoUpiIdDoesNotHaveOne() {
        assertFalse(shopHasUpiId(shop(null))) // the server returns null for a cleared or never-set UPI ID
        assertFalse(shopHasUpiId(shop("")))
        assertFalse(shopHasUpiId(shop("   ")))
    }

    @Test
    fun aShopWithAUpiIdHasOne() {
        assertTrue(shopHasUpiId(shop("lakshmi@okaxis")))
    }

    @Test
    fun beforeTheShopLoadsNoBannerFlashes() {
        assertTrue(shopHasUpiId(null))
    }

    @Test
    fun theProofUrlChangesWhenTheOrderIsUpdatedSoANewScreenshotIsNotServedFromTheCache() {
        val before = orderProofUrl(5, "2026-10-28T10:00:00Z")
        val after = orderProofUrl(5, "2026-10-28T10:30:00Z")

        assertNotEquals(before, after)
        assertEquals(before.substringBefore('?'), after.substringBefore('?'))
        assertTrue(before.contains("/orders/5/proof"))
    }
}
