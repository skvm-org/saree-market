package com.sareemarket.app.ui

import com.sareemarket.app.ui.reseller.upiLink
import org.junit.Assert.assertEquals
import org.junit.Test

class UpiLinkTest {

    @Test
    fun buildsAPayLinkWithRupeesAndPaise() {
        assertEquals(
            "upi://pay?pa=lakshmi%40okaxis&pn=Lakshmi+Silks&am=850.00&cu=INR",
            upiLink("lakshmi@okaxis", "Lakshmi Silks", 850),
        )
    }

    @Test
    fun encodesSpecialCharactersInTheShopName() {
        assertEquals("upi://pay?pa=a%40b&pn=A+%26+B&am=1.00&cu=INR", upiLink("a@b", "A & B", 1))
    }
}
