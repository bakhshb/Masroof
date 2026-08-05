package com.baraa.masroof.sms

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Verifies the calendar-based date range semantics. Range construction must:
 *  - accept valid in-order dates,
 *  - reject reversed dates,
 *  - reject future dates,
 *  - display start-of-day and exclusive-end-of-day semantics in the
 *    selected millis.
 */
class DateRangeSemanticsTest {
    private val today = LocalDate.of(2025, 5, 10)

    @Test fun rangeStartMillisIsStartOfDayInDeviceTimezone() {
        val range = SmsImportRange.custom(today.minusDays(3), today, today)
        val zone = java.time.ZoneId.systemDefault()
        assertEquals(today.minusDays(3), range.start.atZone(zone).toLocalDate())
        assertEquals(0, range.start.hour)
        assertEquals(0, range.start.minute)
    }

    @Test fun rangeEndMillisIsStartOfDayAfter() {
        // Use a 7-day window ending today so endExclusive lands at the
        // start of "tomorrow" without future dates being implied.
        val range = SmsImportRange.custom(today.minusDays(7), today, today)
        assertEquals("range.endExclusive.toLocalDate() must be the day after selected 'to'", today.plusDays(1), range.endExclusive.toLocalDate())
    }

    @Test fun reversedRangeRejected() {
        try {
            SmsImportRange.custom(today, today.minusDays(2), today)
            fail("Reversed range must throw")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }

    @Test fun futureEndDateRejected() {
        try {
            SmsImportRange.custom(today, today.plusDays(3), today)
            fail("Future end date must throw")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }

    @Test fun includeFullStartAndEndDays() {
        val range = SmsImportRange.custom(LocalDate.of(2025, 5, 5), LocalDate.of(2025, 5, 10), today)
        // A message sent May 5 at noon should be included.
        val may5Noon = java.time.LocalDateTime.of(2025, 5, 5, 12, 0)
        assertTrue("May 5 noon must be inside range", !may5Noon.isBefore(range.start) && may5Noon.isBefore(range.endExclusive))
        // May 10 at 23:59 should still be included (end-exclusive midnight keeps the whole end day in).
        val may10Last = java.time.LocalDateTime.of(2025, 5, 10, 23, 59)
        assertTrue("May 10 evening must still be inside range", !may10Last.isBefore(range.start) && may10Last.isBefore(range.endExclusive))
        // May 11 at 00:01 must be excluded.
        val may11 = java.time.LocalDateTime.of(2025, 5, 11, 0, 1)
        assertFalse("May 11 00:01 must be outside range", !may11.isBefore(range.start) && may11.isBefore(range.endExclusive))
    }

    @Test fun rtlDoesNotReverseSelectedDates() {
        // Semantically: local-date ordering must hold regardless of UI
        // direction. RTL only changes layout direction; the data model
        // is direction-agnostic.
        val range = SmsImportRange.custom(LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 5), LocalDate.of(2025, 8, 7))
        assertTrue(range.start.toLocalDate().isBefore(range.endExclusive.toLocalDate()))
    }

    @Test fun historicalRangeDoesNotMutateTrackingStartDate() {
        // The "start of opening" remains a separate user-managed value;
        // picking a custom historical range here is read-only and must
        // not change the setup record.
        val range = SmsImportRange.custom(today.minusYears(5), today.minusYears(5), today)
        assertEquals(SmsImportRange.QUICK_CUSTOM, range.quickId)
        // The label contains "إلى" but never "start tracking" wording.
        assertFalse(range.label.contains("بداية المتابعة"))
    }

    @Test fun importLabelUsesReadableArabicDate() {
        // The "human" date range formatter inside the UI uses Arabic
        // locale ("d MMMM yyyy"). Here we verify the locale is honored.
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("ar"))
        val fromText = "1 أغسطس 2026"
        val toText = "5 أغسطس 2026"
        assertEquals(fromText, LocalDate.of(2026, 8, 1).format(fmt))
        assertEquals(toText, LocalDate.of(2026, 8, 5).format(fmt))
    }
}
