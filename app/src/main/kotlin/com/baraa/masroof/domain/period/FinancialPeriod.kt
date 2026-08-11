package com.baraa.masroof.domain.period

import java.time.LocalDate

/**
 * Half-open financial month range: [startDate, endDateExclusive).
 */
data class FinancialPeriod(
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
) {
    init {
        require(endDateExclusive.isAfter(startDate)) {
            "FinancialPeriod end must be after start: $startDate .. $endDateExclusive"
        }
    }

    /** Inclusive last calendar day shown in UI labels. */
    val displayEndDateInclusive: LocalDate
        get() = endDateExclusive.minusDays(1)
}
