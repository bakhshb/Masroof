package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.CreditCardBalanceSnapshot
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.CreditCardsOverview
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

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
    fun followedOnly_recalculatesAggregateDueFromFollowedStatementSnapshots() {
        val olderStatementAt = Instant.parse("2026-08-10T09:00:00Z")
        val newerStatementAt = Instant.parse("2026-08-11T09:00:00Z")
        val unfollowedStatementAt = Instant.parse("2026-08-12T09:00:00Z")
        val dueDate = LocalDate.parse("2026-09-07")
        val overview = CreditCardsOverview(
            cards = listOf(
                rowWithStatementDue("7271", "0.00", olderStatementAt, dueDate),
                rowWithStatementDue("3478", "8755.50", newerStatementAt, dueDate),
                rowWithStatementDue("9999", "999.00", unfollowedStatementAt, dueDate),
            ),
            aggregateDueAmount = Money.of("999.00", Currency.SAR),
            aggregateDueUpdatedAt = unfollowedStatementAt,
            aggregateDueDate = dueDate,
            aggregatePeriodSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
            aggregateStatementSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
            aggregateStatementPeriodLabel = null,
            calendarMonthLabel = null,
            salaryPeriodLabel = null,
            currency = Currency.SAR,
        )

        val filtered = overview.followedOnly(setOf("7271", "3478"))

        assertEquals(Money.of("8755.50", Currency.SAR), filtered.aggregateDueAmount)
        assertEquals(newerStatementAt, filtered.aggregateDueUpdatedAt)
        assertEquals(dueDate, filtered.aggregateDueDate)
    }

    @Test
    fun followedOnly_aggregateDueNullWhenFollowedCardsHaveNoStatement() {
        val overview = CreditCardsOverview(
            cards = listOf(
                row("7271"),
                rowWithPurchaseOutstandingOnly("3478", "8755.50"),
            ),
            aggregateDueAmount = Money.of("8755.50", Currency.SAR),
            aggregateDueUpdatedAt = Instant.parse("2026-08-20T19:10:00Z"),
            aggregateDueDate = null,
            aggregatePeriodSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
            aggregateStatementSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
            aggregateStatementPeriodLabel = null,
            calendarMonthLabel = null,
            salaryPeriodLabel = null,
            currency = Currency.SAR,
        )

        val filtered = overview.followedOnly(setOf("7271", "3478"))

        assertNull(filtered.aggregateDueAmount)
        assertNull(filtered.aggregateDueUpdatedAt)
        assertNull(filtered.aggregateDueDate)
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

    private fun rowWithStatementDue(
        last4: String,
        dueAmount: String,
        statementIssuedAt: Instant,
        dueDate: LocalDate,
    ): CreditCardDashboardRow = row(last4).copy(
        snapshot = CreditCardBalanceSnapshot(
            availableBalance = null,
            dueAmount = Money.of(dueAmount, Currency.SAR),
            dueDate = dueDate,
            statementIssuedAt = statementIssuedAt,
            updatedAt = statementIssuedAt,
        ),
    )

    private fun rowWithPurchaseOutstandingOnly(
        last4: String,
        dueAmount: String,
    ): CreditCardDashboardRow = row(last4).copy(
        snapshot = CreditCardBalanceSnapshot(
            availableBalance = Money.of("1000.00", Currency.SAR),
            dueAmount = Money.of(dueAmount, Currency.SAR),
            dueDate = null,
            statementIssuedAt = null,
            updatedAt = Instant.parse("2026-08-20T19:10:00Z"),
        ),
    )
}
