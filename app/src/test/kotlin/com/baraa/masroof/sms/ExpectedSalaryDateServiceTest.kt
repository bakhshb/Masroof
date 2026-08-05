package com.baraa.masroof.sms

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExpectedSalaryDateServiceTest {
    @Test fun normalTwentySeventhOfMonth() {
        // May 2025 — 27th is a Tuesday
        val date = ExpectedSalaryDateService.expectedForMonth(YearMonth.of(2025, 5))
        assertEquals(LocalDate.of(2025, 5, 27), date)
    }

    @Test fun fridayTwentySeventhMovesToTwentySixth() {
        // 27 March 2026 is a Friday.
        val date = ExpectedSalaryDateService.expectedForMonth(YearMonth.of(2026, 3))
        assertEquals(LocalDate.of(2026, 3, 26), date)
    }

    @Test fun saturdayTwentySeventhMovesToTwentyEighth() {
        // 27 February 2027 is a Saturday.
        val date = ExpectedSalaryDateService.expectedForMonth(YearMonth.of(2027, 2))
        assertEquals(LocalDate.of(2027, 2, 28), date)
    }

    @Test fun mostRecentSalaryDateAfterFridayHoliday() {
        // Use a month whose 27th is a Wednesday, but the previous month's 27th is a Friday.
        // Jan 2027: 27th is Wednesday → salary date = Jan 27.
        // The previous month Dec 2026: 27th is Sunday → salary date = Dec 27.
        val date = ExpectedSalaryDateService.mostRecentSalaryDate(LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 27), date)
    }

    @Test fun mostRecentSalaryDateBeforeTheFirstMonthBoundary() {
        // 5 May 2025: salary date for May = 27 May 2025 (Tue). 27 > 5, so back to Apr 27 (Sunday normally).
        val date = ExpectedSalaryDateService.mostRecentSalaryDate(LocalDate.of(2025, 5, 1))
        assertEquals(LocalDate.of(2025, 4, 27), date)
    }

    @Test fun shortcutDoesNotCreateSalaryIncome() {
        // The shortcut is defined as a pure date helper that returns a date range.
        // Confirm it does NOT create any income, claim that salary was received,
        // or fabricate a "salary posted" entry.
        val range = SmsImportRange.sinceLastSalary(LocalDate.of(2025, 5, 1))
        assertFalse("Range label must never claim salary receipt", range.label.contains("تم استلام"))
        assertFalse("Range label must never claim 'salary posted' style wording", range.label.contains("إيداع"))
        assertTrue("Range label must use the modifier 'متوقع' to keep it a date hint", range.label.contains("متوقع"))
    }

    @Test fun shortcutReturnsSameDayAtMidnightBoundaries() {
        val range = SmsImportRange.sinceLastSalary(LocalDate.of(2025, 5, 1))
        // The start must be the salary day's start-of-day (00:00).
        assertEquals(0, range.start.hour)
        assertEquals(0, range.start.minute)
        // The end must be the current moment (no future, no past midnight).
        assertTrue(range.endExclusive.isAfter(range.start))
    }
}
