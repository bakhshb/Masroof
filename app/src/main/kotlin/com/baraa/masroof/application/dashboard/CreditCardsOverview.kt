package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import java.time.Instant

data class CreditCardBalanceSnapshot(
    val availableBalance: Money?,
    val dueAmount: Money?,
    val updatedAt: Instant,
)

data class CreditCardDashboardRow(
    val bank: Bank,
    val last4: String,
    val periodSpendingNet: SignedMoneyAmount,
    val snapshot: CreditCardBalanceSnapshot?,
)

/**
 * Credit-card period spending plus latest available/due figures from bank SMS.
 *
 * [aggregateDueAmount] is the most recent due figure from any credit-card message —
 * often identical across cards on the same statement cycle.
 */
data class CreditCardsOverview(
    val cards: List<CreditCardDashboardRow>,
    val aggregateDueAmount: Money?,
    val aggregateDueUpdatedAt: Instant?,
    val currency: Currency,
) {
    val hasContent: Boolean
        get() = cards.isNotEmpty() || aggregateDueAmount != null
}
