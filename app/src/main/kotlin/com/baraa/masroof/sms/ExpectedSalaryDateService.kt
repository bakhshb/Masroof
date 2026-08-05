package com.baraa.masroof.sms

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Deterministic salary-date rule.
 *
 * - Normal expected salary date: the 27th of each Gregorian month.
 * - If the 27th falls on **Friday**, expected salary date moves to the 26th.
 * - If the 27th falls on **Saturday**, expected salary date moves to the 28th.
 *
 * The service only computes a date; it does NOT create any income or
 * claim that a salary was received. Future salary-scheduling features will
 * reuse this service.
 */
object ExpectedSalaryDateService {
    /** The configured day-of-month for the normal salary date. */
    const val NORMAL_SALARY_DAY: Int = 27

    /** Compute the expected salary date for the given month. */
    fun expectedForMonth(month: YearMonth): LocalDate {
        val day = NORMAL_SALARY_DAY.coerceAtMost(month.lengthOfMonth())
        val date = month.atDay(day)
        return when (date.dayOfWeek) {
            DayOfWeek.FRIDAY -> date.minusDays(1)
            DayOfWeek.SATURDAY -> date.plusDays(1)
            else -> date
        }
    }

    /** The most recent expected salary date that is not after [today]. */
    fun mostRecentSalaryDate(today: LocalDate): LocalDate {
        var candidate = expectedForMonth(YearMonth.of(today.year, today.month))
        if (candidate.isAfter(today)) candidate = expectedForMonth(YearMonth.from(today.minusMonths(1)))
        var safety = 0
        while (candidate.isAfter(today) && safety < 24) {
            candidate = expectedForMonth(YearMonth.from(candidate.minusMonths(1)))
            safety++
        }
        return candidate
    }
}
