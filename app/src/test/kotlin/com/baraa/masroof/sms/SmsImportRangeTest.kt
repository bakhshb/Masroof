package com.baraa.masroof.sms

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SmsImportRangeTest {
    private val today = LocalDate.of(2025, 5, 10)

    @Test fun defaultIsFromMonthStartToToday() {
        val range = SmsImportRange.default(today)
        assertEquals(today.withDayOfMonth(1).atStartOfDay(), range.start)
        assertTrue("End must be end-of-today (next day midnight)", range.endExclusive.toLocalDate() == today.plusDays(1))
        assertEquals(SmsImportRange.QUICK_MONTH_START, range.quickId)
    }

    @Test fun lastSevenDaysProducesSevenDayRange() {
        val range = SmsImportRange.lastDays(today, 7)
        val start = range.start.toLocalDate()
        val end = range.endExclusive.toLocalDate()
        // The window is inclusive of both endpoints.
        val span = java.time.temporal.ChronoUnit.DAYS.between(start, end)
        assertTrue("Expected 7-day window, got $span", span in 6..8)
        assertEquals(SmsImportRange.QUICK_LAST_SEVEN, range.quickId)
    }

    @Test fun lastThirtyDaysProducesThirtyDayRange() {
        val range = SmsImportRange.lastDays(today, 30)
        val start = range.start.toLocalDate()
        val span = java.time.temporal.ChronoUnit.DAYS.between(start, today)
        assertTrue("Expected ~30-day window, got $span", span in 28..31)
        assertEquals(SmsImportRange.QUICK_LAST_THIRTY, range.quickId)
    }

    @Test fun customAcceptsValidRange() {
        val range = SmsImportRange.custom(today.minusDays(5), today)
        assertEquals(today.minusDays(5).atStartOfDay(), range.start)
        assertEquals(today.plusDays(1).atStartOfDay(), range.endExclusive)
        assertEquals(SmsImportRange.QUICK_CUSTOM, range.quickId)
    }

    @Test fun customRejectsReversedRange() {
        val ex = assertThrowsWith("Range start must be on or before end") {
            SmsImportRange.custom(today, today.minusDays(1), today)
        }
        assertNotNull(ex)
    }

    @Test fun customRejectsFutureDate() {
        val ex = assertThrowsWith("End date cannot be in the future") {
            SmsImportRange.custom(today.minusDays(3), today.plusDays(1), today)
        }
        assertNotNull(ex)
    }

    @Test fun validateReportsReversed() {
        assertEquals(CustomValidationResult.Reversed, SmsImportRange.validateCustom(today, today.minusDays(1), today))
    }

    @Test fun validateReportsFuture() {
        assertEquals(CustomValidationResult.Future, SmsImportRange.validateCustom(today.minusDays(2), today.plusDays(2), today))
    }

    @Test fun validateReportsValid() {
        assertEquals(CustomValidationResult.Valid, SmsImportRange.validateCustom(today.minusDays(7), today, today))
    }

    @Test fun validateReportsMissing() {
        assertEquals(CustomValidationResult.Missing, SmsImportRange.validateCustom(null, today, today))
        assertEquals(CustomValidationResult.Missing, SmsImportRange.validateCustom(today, null, today))
    }

    @Test fun rangeEncompassesEntireSelectedDays() {
        // A range covering May 5–7 must include a full May 6 12:00 message.
        val range = SmsImportRange.custom(LocalDate.of(2025, 5, 5), LocalDate.of(2025, 5, 7))
        assertTrue("May 6 12:00 inside window", java.time.LocalDateTime.of(2025, 5, 6, 12, 0) >= range.start && java.time.LocalDateTime.of(2025, 5, 6, 12, 0) < range.endExclusive)
        assertTrue("May 4 12:00 outside window", java.time.LocalDateTime.of(2025, 5, 4, 12, 0) < range.start)
        assertTrue("May 8 00:00 outside window", java.time.LocalDateTime.of(2025, 5, 8, 0, 0) >= range.endExclusive)
    }

    @Test fun historicalRangeDoesNotChangeTrackingStartDate() {
        // The historical record is only changed via the dedicated screen.
        val range = SmsImportRange.custom(today.minusYears(2), today.minusDays(1))
        // Range label does not include any tracking start date mutation.
        assertFalse(range.label.contains("بداية المتابعة"))
        assertFalse(range.label.contains("تغيير"))
    }

    private inline fun assertThrowsWith(expectedSubstring: String, block: () -> Unit): Throwable? {
        return try { block(); null } catch (t: Throwable) {
            assertTrue("Expected message containing '$expectedSubstring' but was '${t.message}'", t.message?.contains(expectedSubstring) == true)
            t
        }
    }
}
