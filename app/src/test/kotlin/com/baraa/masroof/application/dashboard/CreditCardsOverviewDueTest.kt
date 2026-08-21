package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CreditCardsOverviewDueTest {
    @Test
    fun resolveLatestStatementDue_usesLatestStatementAmongCards() {
        val older = Instant.parse("2026-08-10T09:00:00Z")
        val newer = Instant.parse("2026-08-11T09:00:00Z")

        val due = resolveLatestStatementDue(
            listOf(
                cardRow(
                    last4 = "7271",
                    dueAmount = "0.00",
                    statementIssuedAt = older,
                    dueDate = LocalDate.parse("2026-09-07"),
                ),
                cardRow(
                    last4 = "3478",
                    dueAmount = "8755.50",
                    statementIssuedAt = newer,
                    dueDate = LocalDate.parse("2026-09-07"),
                ),
            ),
        )

        assertEquals(Money.of("8755.50", Currency.SAR), due?.amount)
        assertEquals(newer, due?.updatedAt)
        assertEquals(LocalDate.parse("2026-09-07"), due?.dueDate)
    }

    @Test
    fun resolveLatestStatementDue_ignoresPurchaseSnapshotWithoutStatementIssuedAt() {
        val due = resolveLatestStatementDue(
            listOf(
                cardRow(
                    last4 = "7271",
                    dueAmount = "8755.50",
                    statementIssuedAt = null,
                    updatedAt = Instant.parse("2026-08-20T19:10:00Z"),
                ),
            ),
        )

        assertNull(due)
    }

    private fun cardRow(
        last4: String,
        dueAmount: String,
        statementIssuedAt: Instant?,
        dueDate: LocalDate? = null,
        updatedAt: Instant = statementIssuedAt ?: Instant.parse("2026-08-10T09:00:00Z"),
    ) = CreditCardDashboardRow(
        bank = Bank.BANK_ALJAZIRA,
        last4 = last4,
        calendarMonthSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
        statementSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
        salaryPeriodSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
        statementPeriodLabel = null,
        snapshot = CreditCardBalanceSnapshot(
            availableBalance = null,
            dueAmount = Money.of(dueAmount, Currency.SAR),
            dueDate = dueDate,
            statementIssuedAt = statementIssuedAt,
            updatedAt = updatedAt,
        ),
    )
}
