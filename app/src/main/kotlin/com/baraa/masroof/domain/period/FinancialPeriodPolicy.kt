package com.baraa.masroof.domain.period

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure financial-month policy.
 *
 * Default Masroof cycle uses [DEFAULT_START_DAY] (27): day 27 through the next
 * month's day 27 exclusive. Shorter months clamp to the last valid day.
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

    fun toInclusiveStartInstant(date: LocalDate, zoneId: ZoneId): Instant =
        date.atStartOfDay(zoneId).toInstant()

    fun toExclusiveEndInstant(date: LocalDate, zoneId: ZoneId): Instant =
        date.atStartOfDay(zoneId).toInstant()

    private fun cycleStartOnOrBefore(anchor: LocalDate, startDay: Int): LocalDate {
        val candidate = clampDay(anchor.year, anchor.monthValue, startDay)
        return if (!anchor.isBefore(candidate)) {
            candidate
        } else {
            previousCycleStart(candidate, startDay)
        }
    }

    private fun nextCycleStart(start: LocalDate, startDay: Int): LocalDate {
        val nextMonth = start.plus(1, ChronoUnit.MONTHS)
        return clampDay(nextMonth.year, nextMonth.monthValue, startDay)
    }

    private fun previousCycleStart(start: LocalDate, startDay: Int): LocalDate {
        val previousMonth = start.minus(1, ChronoUnit.MONTHS)
        return clampDay(previousMonth.year, previousMonth.monthValue, startDay)
    }

    private fun clampDay(year: Int, month: Int, day: Int): LocalDate {
        val length = LocalDate.of(year, month, 1).lengthOfMonth()
        return LocalDate.of(year, month, minOf(day, length))
    }
}
