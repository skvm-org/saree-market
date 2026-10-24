package com.sareemarket.app.ui

/** Rupees with Indian digit grouping: 125000 becomes ₹1,25,000. */
fun formatRupees(amount: Int): String {
    val digits = amount.toString()
    if (digits.length <= 3) return "₹$digits"
    val lakhs = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return "₹$lakhs,${digits.takeLast(3)}"
}
