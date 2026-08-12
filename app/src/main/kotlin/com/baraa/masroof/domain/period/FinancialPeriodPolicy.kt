package com.baraa.masroof.domain.period

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure financial-month policy.
 *
 * Default Masroof salary cycle uses [DEFAULT_START_DAY] (27), adjusted when that day
 * falls on Friday (start 26) or Saturday (start 28). Shorter months clamp to the last
 * valid day before the weekend adjustment.
 */
object FinancialPeriodPolicy {
    const val DEFAULT_START_DAY: Int = 27

    fun periodContaining(
        anchor: LocalDate,
        startDay: Int = DEFAULT_START_DAY,
    ): FinancialPeriod {
        require(startDay in 1..31) { "startDay must be 1..31, was $startDay" }
        val start = cycleStartOnOrBefore(anchor, startDay)
        val endExclusive = nextCycleStart(start, startDay)
        return FinancialPeriod(startDate = start, endDateExclusive = endExclusive)
    }

    fun previous(period: FinancialPeriod, startDay: Int = DEFAULT_START_DAY): FinancialPeriod {
        require(startDay in 1..31) { "startDay must be 1..31, was $startDay" }
        val previousStart = previousCycleStart(period.startDate, startDay)
        return FinancialPeriod(startDate = previousStart, endDateExclusive = period.startDate)
    }

    fun next(period: FinancialPeriod, startDay: Int = DEFAULT_START_DAY): FinancialPeriod {
        require(startDay in 1..31) { "startDay must be 1..31, was $startDay" }
        val nextStart = period.endDateExclusive
        val nextEnd = nextCycleStart(nextStart, startDay)
        return FinancialPeriod(startDate = nextStart, endDateExclusive = nextEnd)
    }

    fun salaryCycleStartAdjustment(
        startDate: LocalDate,
        startDay: Int = DEFAULT_START_DAY,
    ): SalaryCycleStartAdjustment? {
        if (startDay != DEFAULT_START_DAY) return null
        val nominal = clampDay(startDate.year, startDate.monthValue, startDay)
        if (startDate == nominal) return null
        return when (nominal.dayOfWeek) {
            DayOfWeek.FRIDAY -> SalaryCycleStartAdjustment.EARLY_FOR_FRIDAY
            DayOfWeek.SATURDAY -> SalaryCycleStartAdjustment.LATE_FOR_SATURDAY
            else -> null
        }
    }

    fun toInclusiveStartInstant(date: LocalDate, zoneId: ZoneId): Instant =
        date.atStartOfDay(zoneId).toInstant()

    fun toExclusiveEndInstant(date: LocalDate, zoneId: ZoneId): Instant =
        date.atStartOfDay(zoneId).toInstant()

    private fun cycleStartOnOrBefore(anchor: LocalDate, startDay: Int): LocalDate {
        val candidate = cycleStartForMonth(anchor.year, anchor.monthValue, startDay)
        return if (!anchor.isBefore(candidate)) {
            candidate
        } else {
            previousCycleStart(candidate, startDay)
        }
    }

    private fun nextCycleStart(start: LocalDate, startDay: Int): LocalDate {
        val nextMonth = start.plus(1, ChronoUnit.MONTHS)
        return cycleStartForMonth(nextMonth.year, nextMonth.monthValue, startDay)
    }

    private fun previousCycleStart(start: LocalDate, startDay: Int): LocalDate {
        val previousMonth = start.minus(1, ChronoUnit.MONTHS)
        return cycleStartForMonth(previousMonth.year, previousMonth.monthValue, startDay)
    }

    private fun cycleStartForMonth(year: Int, month: Int, startDay: Int): LocalDate {
        val nominal = clampDay(year, month, startDay)
        return if (startDay == DEFAULT_START_DAY) {
            adjustSalaryStartForWeekend(nominal)
        } else {
            nominal
        }
    }

    private fun adjustSalaryStartForWeekend(nominal: LocalDate): LocalDate =
        when (nominal.dayOfWeek) {
            DayOfWeek.FRIDAY -> nominal.minusDays(1)
            DayOfWeek.SATURDAY -> nominal.plusDays(1)
            else -> nominal
        }

    private fun clampDay(year: Int, month: Int, day: Int): LocalDate {
        val length = LocalDate.of(year, month, 1).lengthOfMonth()
        return LocalDate.of(year, month, minOf(day, length))
    }
}
