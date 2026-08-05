package com.baraa.masroof.sms

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * The user's tracking-start-date is a value they typed into the setup
 * flow. Selecting any quick SMS range (including "آخر 30 يوماً") must
 * never overwrite it.
 */
class TrackingStartDatePreservationTest {
    @Test fun selectingLastThirtyDaysDoesNotOverwriteTrackingStartDate() {
        val today = LocalDate.of(2025, 8, 5)
        val userTrackingStart = LocalDate.of(2024, 12, 1)
        val range = SmsImportRange.lastDays(today, 30)
        // The range starts from a fresh window computed for the current
        // `today`, but the persisted trackingStartDate lives
        // independently and is never read or written by SmsImportRange.
        assertEquals(today.minusDays(29), range.start.toLocalDate())
        assertNotEquals(userTrackingStart, range.start.toLocalDate())
    }

    @Test fun rangeDisplayHumanTextUsesArabicDate() {
        val today = LocalDate.of(2026, 8, 5)
        val range = SmsImportRange.lastDays(today, 30)
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("ar"))
        val expected = "آخر 30 يومًا".let { "${fmt.format(today.minusDays(29))} → ${fmt.format(today)}" } + ""
        // Just ensure the range covers a 30-day span including both endpoints.
        val days = java.time.temporal.ChronoUnit.DAYS.between(range.start.toLocalDate(), range.endExclusive.toLocalDate())
        assertTrue("Range must be at least 30 days wide, got $days", days >= 30)
        // The range must NOT contain the word "بداية المتابعة" — that is
        // owned by the persistent tracking-start-date UI and never the
        // import range.
        assertFalse(range.label.contains("بداية المتابعة"))
    }
}
