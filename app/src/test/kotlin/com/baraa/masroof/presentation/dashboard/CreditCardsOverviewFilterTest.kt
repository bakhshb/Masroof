package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.CreditCardsOverview
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CreditCardsOverviewFilterTest {
    @Test
    fun followedOnly_keepsOwnedLast4Rows() {
        val overview = CreditCardsOverview(
            cards = listOf(
                row("7271"),
                row("5123"),
                row("9999"),
            ),
            aggregateDueAmount = null,
            aggregateDueUpdatedAt = null,
            aggregateDueDate = null,
            aggregatePeriodSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
            aggregateStatementSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
            aggregateStatementPeriodLabel = null,
            calendarMonthLabel = null,
            salaryPeriodLabel = null,
            currency = Currency.SAR,
        )

        val filtered = overview.followedOnly(setOf("7271", "5123"))

        assertEquals(listOf("7271", "5123"), filtered.cards.map { it.last4 })
        assertFalse(filtered.hasContent && filtered.cards.any { it.last4 == "9999" })
    }

    @Test
    fun followedOnly_recalculatesAggregateTotals() {
        val overview = CreditCardsOverview(
            cards = listOf(
                row("3478", periodAmount = "100.00", statementAmount = "80.00"),
                row("7271", periodAmount = "50.25", statementAmount = "40.00"),
                row("9999", periodAmount = "900.00", statementAmount = "10.00"),
            ),
            aggregateDueAmount = null,
            aggregateDueUpdatedAt = null,
            aggregateDueDate = null,
            aggregatePeriodSpendingNet = SignedMoneyAmount.of(Money.of("1050.25", Currency.SAR)),
            aggregateStatementSpendingNet = SignedMoneyAmount.of(Money.of("130.00", Currency.SAR)),
            aggregateStatementPeriodLabel = null,
            calendarMonthLabel = null,
            salaryPeriodLabel = null,
            currency = Currency.SAR,
        )

        val filtered = overview.followedOnly(setOf("3478", "7271"))

        assertEquals(
            SignedMoneyAmount.of(Money.of("150.25", Currency.SAR)),
            filtered.aggregatePeriodSpendingNet,
        )
        assertEquals(
            SignedMoneyAmount.of(Money.of("120.00", Currency.SAR)),
            filtered.aggregateStatementSpendingNet,
        )
    }

    @Test
    fun followedSalaryPeriodSpendingTotal_sumsFollowedCards() {
        val overview = CreditCardsOverview(
            cards = listOf(
                row("3478", periodAmount = "100.00"),
                row("7271", periodAmount = "50.25"),
                row("9999", periodAmount = "900.00"),
            ),
            aggregateDueAmount = null,
            aggregateDueUpdatedAt = null,
            aggregateDueDate = null,
            aggregatePeriodSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
            aggregateStatementSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
            aggregateStatementPeriodLabel = null,
            calendarMonthLabel = null,
            salaryPeriodLabel = null,
            currency = Currency.SAR,
        )

        val total = overview.followedSalaryPeriodSpendingTotal(setOf("3478", "7271"))

        assertEquals(SignedMoneyAmount.of(Money.of("150.25", Currency.SAR)), total)
    }

    private fun row(
        last4: String,
        monthAmount: String = "0.00",
        periodAmount: String = "0.00",
        statementAmount: String = "0.00",
    ): CreditCardDashboardRow =
        CreditCardDashboardRow(
            bank = Bank.BANK_ALJAZIRA,
            last4 = last4,
            calendarMonthSpendingNet = SignedMoneyAmount.of(Money.of(monthAmount, Currency.SAR)),
            statementSpendingNet = SignedMoneyAmount.of(Money.of(statementAmount, Currency.SAR)),
            salaryPeriodSpendingNet = SignedMoneyAmount.of(Money.of(periodAmount, Currency.SAR)),
            statementPeriodLabel = null,
            snapshot = null,
        )
}
