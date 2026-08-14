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
            salaryPeriodLabel = null,
            currency = Currency.SAR,
        )

        val filtered = overview.followedOnly(setOf("7271", "5123"))

        assertEquals(listOf("7271", "5123"), filtered.cards.map { it.last4 })
        assertFalse(filtered.hasContent && filtered.cards.any { it.last4 == "9999" })
    }

    @Test
    fun followedSalarySpendingTotal_sumsFollowedCards() {
        val overview = CreditCardsOverview(
            cards = listOf(
                row("3478", "100.00"),
                row("7271", "50.25"),
                row("9999", "900.00"),
            ),
            aggregateDueAmount = null,
            aggregateDueUpdatedAt = null,
            aggregateDueDate = null,
            salaryPeriodLabel = null,
            currency = Currency.SAR,
        )

        val total = overview.followedSalarySpendingTotal(setOf("3478", "7271"))

        assertEquals(SignedMoneyAmount.of(Money.of("150.25", Currency.SAR)), total)
    }

    private fun row(last4: String, salaryAmount: String = "0.00"): CreditCardDashboardRow =
        CreditCardDashboardRow(
            bank = Bank.BANK_ALJAZIRA,
            last4 = last4,
            statementSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
            salaryPeriodSpendingNet = SignedMoneyAmount.of(Money.of(salaryAmount, Currency.SAR)),
            statementPeriodLabel = null,
            snapshot = null,
        )
}
