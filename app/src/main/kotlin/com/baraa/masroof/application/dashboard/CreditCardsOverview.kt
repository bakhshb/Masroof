package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import java.time.Instant
import java.time.LocalDate

data class CreditCardBalanceSnapshot(
    val availableBalance: Money?,
    val dueAmount: Money?,
    val dueDate: LocalDate?,
    val statementIssuedAt: Instant?,
    val updatedAt: Instant,
)

data class CreditCardDashboardRow(
    val bank: Bank,
    val last4: String,
    val statementSpendingNet: SignedMoneyAmount,
    val salaryPeriodSpendingNet: SignedMoneyAmount,
    val statementPeriodLabel: String?,
    val snapshot: CreditCardBalanceSnapshot?,
)

/**
 * All credit cards with dual spending windows (statement cycle + salary period).
 */
data class CreditCardsOverview(
    val cards: List<CreditCardDashboardRow>,
    val aggregateDueAmount: Money?,
    val aggregateDueUpdatedAt: Instant?,
    val aggregateDueDate: LocalDate?,
    val salaryPeriodLabel: String?,
    val currency: Currency,
) {
    val hasContent: Boolean
        get() = cards.isNotEmpty() || aggregateDueAmount != null
}
