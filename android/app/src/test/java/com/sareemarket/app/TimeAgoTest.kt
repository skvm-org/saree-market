package com.sareemarket.app

import com.sareemarket.app.ui.timeAgo
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeAgoTest {

    private val now = Instant.parse("2026-10-25T12:00:00Z")

    private fun ago(iso: String) = timeAgo(iso, now)

    @Test
    fun recentTimesAreShortPhrases() {
        assertEquals("just now", ago("2026-10-25T11:59:30Z"))
        assertEquals("1 min ago", ago("2026-10-25T11:59:00Z"))
        assertEquals("59 min ago", ago("2026-10-25T11:01:00Z"))
    }

    @Test
    fun hoursThenYesterdayThenDays() {
        assertEquals("1 h ago", ago("2026-10-25T11:00:00Z"))
        assertEquals("23 h ago", ago("2026-10-24T13:00:00Z"))
        assertEquals("yesterday", ago("2026-10-24T11:00:00Z"))
        assertEquals("3 days ago", ago("2026-10-22T11:00:00Z"))
    }

    @Test
    fun aWeekOrMoreShowsTheDate() {
        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}").matches(ago("2026-10-10T12:00:00Z")))
    }

    @Test
    fun aTimeInTheFutureIsJustNow() {
        assertEquals("just now", ago("2026-10-25T12:05:00Z"))
    }
}
