package com.sareemarket.app

import com.sareemarket.app.ui.formatRupees
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatRupeesTest {
    @Test
    fun groupsDigitsTheIndianWay() {
        assertEquals("₹0", formatRupees(0))
        assertEquals("₹999", formatRupees(999))
        assertEquals("₹1,000", formatRupees(1000))
        assertEquals("₹2,450", formatRupees(2450))
        assertEquals("₹15,000", formatRupees(15000))
        assertEquals("₹1,25,000", formatRupees(125000))
        assertEquals("₹12,34,567", formatRupees(1234567))
    }
}
