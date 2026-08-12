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
    val isPrimary: Boolean,
    val statementSpendingNet: SignedMoneyAmount,
    val snapshot: CreditCardBalanceSnapshot?,
)

/**
 * Primary credit-card snapshot plus statement-cycle spending.
 *
 * [aggregateDueAmount] comes from the latest statement SMS for the primary card.
 */
data class CreditCardsOverview(
    val cards: List<CreditCardDashboardRow>,
    val aggregateDueAmount: Money?,
    val aggregateDueUpdatedAt: Instant?,
    val aggregateDueDate: LocalDate?,
    val statementPeriodLabel: String?,
    val supplementaryCardCount: Int,
    val currency: Currency,
) {
    val hasContent: Boolean
        get() = cards.isNotEmpty() || aggregateDueAmount != null
}
