package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.LoanType
import java.time.Instant

data class LoanOverview(
    val bank: Bank,
    val loanType: LoanType,
    val displayLabel: String,
    val remainingBalance: Money?,
    val remainingBalanceAsOf: Instant?,
    val salaryPeriodPayment: SignedMoneyAmount,
    val salaryPeriodLabel: String?,
)

data class LoansOverview(
    val loans: List<LoanOverview>,
    val salaryPeriodLabel: String?,
    val currency: Currency,
) {
    val hasContent: Boolean get() = loans.isNotEmpty()
}

fun LoansOverview.aggregateRemainingBalance(): Money? {
    val balances = loans.mapNotNull { it.remainingBalance }
    if (balances.isEmpty()) return null
    return balances.reduce { acc, balance -> acc + balance }
}

fun LoansOverview.aggregateSalaryPeriodPayment(): SignedMoneyAmount =
    SpendingAmounts.sum(loans.map { it.salaryPeriodPayment })
