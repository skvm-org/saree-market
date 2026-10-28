package com.sareemarket.app.ui

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeRemainingTest {

    private val now = Instant.parse("2026-10-28T10:00:00Z")

    @Test
    fun showsHoursAndMinutes() {
        assertEquals("1 h 42 min", timeRemaining("2026-10-28T11:42:30Z", now))
    }

    @Test
    fun showsOnlyMinutesUnderAnHour() {
        assertEquals("35 min", timeRemaining("2026-10-28T10:35:10Z", now))
    }

    @Test
    fun saysLessThanAMinuteNearTheEnd() {
        assertEquals("less than a minute", timeRemaining("2026-10-28T10:00:40Z", now))
    }

    @Test
    fun isEmptyOnceOver() {
        assertEquals("", timeRemaining("2026-10-28T09:59:00Z", now))
    }
}
