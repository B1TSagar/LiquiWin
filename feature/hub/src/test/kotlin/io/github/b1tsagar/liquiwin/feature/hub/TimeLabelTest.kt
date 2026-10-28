package io.github.b1tsagar.liquiwin.feature.hub

import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeLabelTest {
    private fun label(time: String, now: String, zone: String = "UTC") = timeLabel(
        Instant.parse(time), Instant.parse(now),
        SimpleDateFormat("H:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone(zone) },
    )

    @Test fun todaysMessagesShowTheirTime() {
        assertEquals("9:41", label("2026-10-28T09:41:00Z", now = "2026-10-28T12:00:00Z"))
    }

    @Test fun earlierOnesSayYesterday() {
        assertEquals("yesterday 23:50", label("2026-10-27T23:50:00Z", now = "2026-10-28T00:10:00Z"))
    }

    @Test fun theDayIsTheOneOnThePhone() {
        // In India both are on the morning of the 28th.
        assertEquals("5:20", label("2026-10-27T23:50:00Z", now = "2026-10-28T00:10:00Z", zone = "Asia/Kolkata"))
    }
}
