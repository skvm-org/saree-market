package com.sareemarket.app.ui

/** Rupees with Indian digit grouping: 125000 becomes ₹1,25,000. */
fun formatRupees(amount: Int): String {
    val digits = amount.toString()
    if (digits.length <= 3) return "₹$digits"
    val lakhs = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return "₹$lakhs,${digits.takeLast(3)}"
}

/** "Cotton · Silk", or just "Cotton" when the type and the fabric are the same word. */
fun typeAndFabric(type: String, fabric: String) = if (type.equals(fabric, ignoreCase = true)) type else "$type · $fabric"

/** How long a hold has left: "ends in 5 h", "ends in 40 min", or "ended" once it is over. */
fun timeLeft(iso: String, now: java.time.Instant = java.time.Instant.now()): String {
    val minutes = java.time.Duration.between(now, java.time.Instant.parse(iso)).toMinutes()
    return when {
        minutes <= 0 -> "ended"
        minutes < 60 -> "ends in $minutes min"
        else -> "ends in ${(minutes + 30) / 60} h"
    }
}

/** "just now", "5 min ago", "3 h ago", "yesterday", "4 days ago", then the date. [iso] is an ISO-8601 instant. */
fun timeAgo(iso: String, now: java.time.Instant = java.time.Instant.now()): String {
    val then = java.time.Instant.parse(iso)
    val minutes = java.time.Duration.between(then, now).toMinutes()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 48 * 60 -> "yesterday"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} days ago"
        else -> java.time.LocalDate.ofInstant(then, java.time.ZoneId.systemDefault()).toString()
    }
}

/** "Today", "Yesterday" or "Earlier" for grouping a list by when things happened. [iso] is an ISO-8601 instant. */
fun dayLabel(iso: String, now: java.time.Instant = java.time.Instant.now(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    val day = java.time.LocalDate.ofInstant(java.time.Instant.parse(iso), zone)
    val today = java.time.LocalDate.ofInstant(now, zone)
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> "Earlier"
    }
}

/** What is left of a countdown, for a screen: "1 h 42 min", "35 min", "less than a minute", or "" once it is over. */
fun timeRemaining(iso: String, now: java.time.Instant = java.time.Instant.now()): String {
    val seconds = java.time.Duration.between(now, java.time.Instant.parse(iso)).seconds
    val minutes = seconds / 60
    return when {
        seconds <= 0 -> ""
        minutes < 1 -> "less than a minute"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
