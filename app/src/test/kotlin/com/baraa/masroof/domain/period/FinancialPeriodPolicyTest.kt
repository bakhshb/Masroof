package com.baraa.masroof.domain.period

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FinancialPeriodPolicyTest {
    @Test
    fun periodContaining_august11_isJuly27ToAugust27Exclusive() {
        val period = FinancialPeriodPolicy.periodContaining(LocalDate.parse("2026-08-11"))
        assertEquals(LocalDate.parse("2026-07-27"), period.startDate)
        assertEquals(LocalDate.parse("2026-08-27"), period.endDateExclusive)
        assertEquals(LocalDate.parse("2026-08-26"), period.displayEndDateInclusive)
    }

    @Test
    fun periodContaining_onStartDay_startsSameDay() {
        val period = FinancialPeriodPolicy.periodContaining(LocalDate.parse("2026-08-27"))
        assertEquals(LocalDate.parse("2026-08-27"), period.startDate)
        assertEquals(LocalDate.parse("2026-09-27"), period.endDateExclusive)
    }

    @Test
    fun periodContaining_august29_sameAsAfter27() {
        val period = FinancialPeriodPolicy.periodContaining(LocalDate.parse("2026-08-29"))
        assertEquals(LocalDate.parse("2026-08-27"), period.startDate)
        assertEquals(LocalDate.parse("2026-09-27"), period.endDateExclusive)
    }

    @Test
    fun previousAndNext_areAdjacentWithoutGapOrOverlap() {
        val current = FinancialPeriodPolicy.periodContaining(LocalDate.parse("2026-08-11"))
        val previous = FinancialPeriodPolicy.previous(current)
        val next = FinancialPeriodPolicy.next(current)

        assertEquals(current.startDate, previous.endDateExclusive)
        assertEquals(current.endDateExclusive, next.startDate)
        assertEquals(LocalDate.parse("2026-06-28"), previous.startDate)
        assertEquals(LocalDate.parse("2026-09-27"), next.endDateExclusive)
    }

    @Test
    fun salaryStart_whenNominal27IsFriday_startsOn26() {
        val period = FinancialPeriodPolicy.periodContaining(LocalDate.parse("2026-03-15"))
        assertEquals(LocalDate.parse("2026-02-26"), period.startDate)
        assertEquals(
            SalaryCycleStartAdjustment.EARLY_FOR_FRIDAY,
            FinancialPeriodPolicy.salaryCycleStartAdjustment(period.startDate),
        )
    }

    @Test
    fun salaryStart_whenNominal27IsSaturday_startsOn28() {
        val period = FinancialPeriodPolicy.periodContaining(LocalDate.parse("2026-07-15"))
        assertEquals(LocalDate.parse("2026-06-28"), period.startDate)
        assertEquals(
            SalaryCycleStartAdjustment.LATE_FOR_SATURDAY,
            FinancialPeriodPolicy.salaryCycleStartAdjustment(period.startDate),
        )
    }

    @Test
    fun startDay31_clampsToFebruaryLength() {
        val period = FinancialPeriodPolicy.periodContaining(
            anchor = LocalDate.parse("2026-03-01"),
            startDay = 31,
        )
        assertEquals(LocalDate.parse("2026-02-28"), period.startDate)
        assertEquals(LocalDate.parse("2026-03-31"), period.endDateExclusive)
    }

    @Test
    fun instantBoundaries_useLocalStartOfDay_notUtcMidnight() {
        val zone = ZoneId.of("Asia/Riyadh")
        val start = FinancialPeriodPolicy.toInclusiveStartInstant(LocalDate.parse("2026-07-27"), zone)
        val end = FinancialPeriodPolicy.toExclusiveEndInstant(LocalDate.parse("2026-08-27"), zone)
        assertEquals(
            LocalDate.parse("2026-07-27").atStartOfDay(zone).toInstant(),
            start,
        )
        assertEquals(
            LocalDate.parse("2026-08-27").atStartOfDay(zone).toInstant(),
            end,
        )
        assertTrue(start.toEpochMilli() < end.toEpochMilli())
    }
}
