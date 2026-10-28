package com.sareemarket.app.ui.reseller

import java.net.URLEncoder

/** The standard UPI deep link: opens whichever UPI app the customer picks with the payee and amount filled in. */
fun upiLink(upiId: String, payee: String, rupees: Int): String {
    fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    return "upi://pay?pa=${enc(upiId)}&pn=${enc(payee)}&am=$rupees.00&cu=INR"
}
