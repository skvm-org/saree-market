package com.sareemarket.app.data

/** The two plain links behind the Call and WhatsApp buttons. Neither needs an integration. */
object ContactLinks {

    fun dial(phone: String) = "tel:${phone.filter { it.isDigit() || it == '+' }}"

    /** wa.me wants the country code. Numbers are stored as 10 digits, so India's 91 is added. */
    fun whatsapp(number: String): String {
        val digits = number.filter { it.isDigit() }
        val international = if (digits.length == 10) "91$digits" else digits
        return "https://wa.me/$international"
    }
}
