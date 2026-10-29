package com.sareemarket.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server reads a missing field as "keep what you have", so a zero must really be written into the request. */
class ShopDetailsJsonTest {

    @Test
    fun aZeroShippingFeeIsStillSent() {
        val body = testJson.encodeToString(ShopDetails("Lakshmi Silks", "Dindigul", "9876500001", null, upiId = "lakshmi@okaxis", shippingFee = 0))

        assertTrue(body, "\"shippingFee\":0" in body)
    }

    @Test
    fun anEmptyUpiIdIsStillSentSoTheServerClearsIt() {
        val body = testJson.encodeToString(ShopDetails("Lakshmi Silks", "Dindigul", "9876500001", null, upiId = "", shippingFee = 60))

        assertTrue(body, "\"upiId\":\"\"" in body)
    }

    @Test
    fun anUntouchedUpiIdIsLeftOutSoTheServerKeepsIt() {
        val body = testJson.encodeToString(ShopDetails("Lakshmi Silks", "Dindigul", "9876500001", null, upiId = null, shippingFee = 60))

        assertFalse(body, "upiId" in body)
    }
}
