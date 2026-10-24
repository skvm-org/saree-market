package com.sareemarket.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactLinksTest {

    @Test
    fun dialKeepsDigitsAndPlus() {
        assertEquals("tel:9876543210", ContactLinks.dial("98765 43210"))
        assertEquals("tel:+919876543210", ContactLinks.dial("+91 98765-43210"))
    }

    @Test
    fun whatsappAddsIndiaCodeToTenDigitNumbers() {
        assertEquals("https://wa.me/919876543211", ContactLinks.whatsapp("9876543211"))
        assertEquals("https://wa.me/919876543211", ContactLinks.whatsapp("98765 43211"))
    }

    @Test
    fun whatsappLeavesNumbersThatAlreadyHaveACountryCode() {
        assertEquals("https://wa.me/919876543211", ContactLinks.whatsapp("+91 98765 43211"))
        assertEquals("https://wa.me/14155550123", ContactLinks.whatsapp("+1 (415) 555-0123"))
    }
}
