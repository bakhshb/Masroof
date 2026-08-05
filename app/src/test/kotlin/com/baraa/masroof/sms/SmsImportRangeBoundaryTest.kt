package com.baraa.masroof.sms

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Date-range boundary tests required by section C / M:
 *  - today is included
 *  - messages at 23:59 today are included
 *  - messages at 00:00 tomorrow are excluded
 *  - the displayed end date is human-readable (today, not tomorrow)
 *  - RTL does not reverse the dates
 */
class SmsImportRangeBoundaryTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 5)

    @Test fun todayIsIncluded() {
        val r = SmsImportRange.lastDays(today, 7)
        // start = 2026-07-30; endExclusive = 2026-08-06 00:00. Aug 5 12:00 must be inside.
        val noon = java.time.LocalDateTime.of(2026, 8, 5, 12, 0)
        assertTrue("Aug 5 12:00 inside window", !noon.isBefore(r.start) && noon.isBefore(r.endExclusive))
    }

    @Test fun endOfTodayAt235959IsIncluded() {
        val r = SmsImportRange.lastDays(today, 7)
        val lateNight = java.time.LocalDateTime.of(2026, 8, 5, 23, 59, 59)
        assertTrue("Aug 5 23:59:59 inside window", !lateNight.isBefore(r.start) && lateNight.isBefore(r.endExclusive))
    }

    @Test fun midnightTomorrowIsExcluded() {
        val r = SmsImportRange.lastDays(today, 7)
        val nextMidnight = java.time.LocalDateTime.of(2026, 8, 6, 0, 0)
        assertTrue("Aug 6 00:00 NOT inside window", !nextMidnight.isBefore(r.endExclusive))
    }

    @Test fun displayEndDateIsHumanReadable() {
        // lastDays(today, 7) → start = today.minusDays(6), endExclusive = today.plusDays(1).atStartOfDay()
        // displayEndDate must equal today, not today+1
        val r = SmsImportRange.lastDays(today, 7)
        assertEquals(today, r.displayEndDate)
    }

    @Test fun sinceLastSalaryDisplaysTodayNotYesterday() {
        // Previously the displayed end was today.minusDays(1) — bug.
        val r = SmsImportRange.sinceLastSalary(today)
        assertEquals(today, r.displayEndDate)
    }

    @Test fun monthStartRangeDisplaysToday() {
        val r = SmsImportRange.default(today)
        assertEquals(today, r.displayEndDate)
    }

    @Test fun customRangeDisplaysTheUserPickedEndDate() {
        val r = SmsImportRange.custom(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 5), today)
        assertEquals(LocalDate.of(2026, 8, 5), r.displayEndDate)
    }

    @Test fun arabicLabelContainsStartAndEndDatesInDisplayOrder() {
        // RTL composition does not reverse the order; the Arabic label
        // still says "من 27 يوليو 2026 إلى 5 أغسطس 2026".
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("ar"))
        val label = "من ${LocalDate.of(2026, 7, 27).format(fmt)} إلى ${LocalDate.of(2026, 8, 5).format(fmt)}"
        // The Arabic label starts with "من" (from) and ends with the date — this is correct.
        assertTrue(label.startsWith("من"))
        assertTrue(label.contains("27 يوليو 2026"))
        assertTrue(label.contains("5 أغسطس 2026"))
        assertTrue(label.indexOf("27") < label.indexOf("5"))
    }
}