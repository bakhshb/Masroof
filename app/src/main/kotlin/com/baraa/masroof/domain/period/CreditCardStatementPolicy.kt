package com.baraa.masroof.domain.period

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Credit-card billing statement cycle (statement day 10 by default).
 */
object CreditCardStatementPolicy {
    const val DEFAULT_STATEMENT_DAY: Int = 10
    const val PRIMARY_CARD_LAST4: String = "7271"

    fun statementCycleStartOnOrBefore(
        anchor: LocalDate,
        statementDay: Int = DEFAULT_STATEMENT_DAY,
    ): LocalDate {
        require(statementDay in 1..31) { "statementDay must be 1..31, was $statementDay" }
        val candidate = clampDay(anchor.year, anchor.monthValue, statementDay)
        return if (!anchor.isBefore(candidate)) {
            candidate
        } else {
            previousStatementStart(candidate, statementDay)
        }
    }

    private fun previousStatementStart(start: LocalDate, statementDay: Int): LocalDate {
        val previousMonth = start.minus(1, ChronoUnit.MONTHS)
        return clampDay(previousMonth.year, previousMonth.monthValue, statementDay)
    }

    private fun clampDay(year: Int, month: Int, day: Int): LocalDate {
        val length = LocalDate.of(year, month, 1).lengthOfMonth()
        return LocalDate.of(year, month, minOf(day, length))
    }
}
