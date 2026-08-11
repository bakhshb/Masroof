package com.baraa.masroof.presentation.onboarding

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ImportDatePolicy {
    fun last27th(today: LocalDate): LocalDate =
        if (today.dayOfMonth >= 27) {
            today.withDayOfMonth(27)
        } else {
            today.minusMonths(1).withDayOfMonth(27)
        }

    fun toStartOfDayInstant(
        date: LocalDate,
        zoneId: ZoneId,
    ): Instant = date.atStartOfDay(zoneId).toInstant()
}
