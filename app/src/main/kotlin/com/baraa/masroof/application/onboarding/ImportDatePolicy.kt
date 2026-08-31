package com.baraa.masroof.application.onboarding

import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ImportDatePolicy {
    /** Start of the salary cycle containing [today], including Fri/Sat adjustments. */
    fun last27th(today: LocalDate): LocalDate =
        FinancialPeriodPolicy.periodContaining(today).startDate

    fun toStartOfDayInstant(
        date: LocalDate,
        zoneId: ZoneId,
    ): Instant = date.atStartOfDay(zoneId).toInstant()
}
