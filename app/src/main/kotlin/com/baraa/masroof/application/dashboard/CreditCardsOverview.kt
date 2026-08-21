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
    val calendarMonthSpendingNet: SignedMoneyAmount,
    val statementSpendingNet: SignedMoneyAmount,
    val salaryPeriodSpendingNet: SignedMoneyAmount,
    val statementPeriodLabel: String?,
    val snapshot: CreditCardBalanceSnapshot?,
)

/**
 * All credit cards with dual spending windows (calendar month + statement cycle).
 */
data class CreditCardsOverview(
    val cards: List<CreditCardDashboardRow>,
    val aggregateDueAmount: Money?,
    val aggregateDueUpdatedAt: Instant?,
    val aggregateDueDate: LocalDate?,
    val aggregatePeriodSpendingNet: SignedMoneyAmount,
    val aggregateStatementSpendingNet: SignedMoneyAmount,
    val aggregateStatementPeriodLabel: String?,
    val calendarMonthLabel: String?,
    val salaryPeriodLabel: String?,
    val currency: Currency,
) {
    val hasContent: Boolean
        get() = cards.isNotEmpty() || aggregateDueAmount != null
}

/**
 * Latest statement due among [cards] — one value for linked cards on the same credit facility.
 * Ignores purchase/refund SMS outstanding balances.
 */
fun resolveLatestStatementDue(cards: List<CreditCardDashboardRow>): StatementDueSnapshot? =
    cards.mapNotNull { row ->
        val snap = row.snapshot ?: return@mapNotNull null
        val amount = snap.dueAmount ?: return@mapNotNull null
        val issuedAt = snap.statementIssuedAt ?: return@mapNotNull null
        StatementDueSnapshot(amount = amount, updatedAt = issuedAt, dueDate = snap.dueDate)
    }.maxByOrNull { it.updatedAt }

data class StatementDueSnapshot(
    val amount: Money,
    val updatedAt: Instant,
    val dueDate: LocalDate?,
)
