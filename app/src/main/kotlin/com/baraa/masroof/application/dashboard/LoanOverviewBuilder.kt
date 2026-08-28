package com.baraa.masroof.application.dashboard

import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.loan.LoanTypeResolver
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.LoanRegistryEntry
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object LoanOverviewBuilder {
    fun build(
        salaryPeriod: FinancialPeriod,
        loans: List<LoanRegistryEntry>,
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        zoneId: ZoneId,
        displayLocale: Locale = Locale.forLanguageTag(AppLocale.TAG_AR),
    ): LoansOverview {
        val ownedLoans = loans.filter { it.ownership == OwnershipStatus.OWNED }
        if (ownedLoans.isEmpty()) {
            return LoansOverview(
                loans = emptyList(),
                salaryPeriodLabel = null,
                currency = primaryCurrency,
            )
        }

        val salaryPeriodStart = FinancialPeriodPolicy.toInclusiveStartInstant(salaryPeriod.startDate, zoneId)
        val salaryPeriodEnd = FinancialPeriodPolicy.toExclusiveEndInstant(salaryPeriod.endDateExclusive, zoneId)
        val salaryPeriodLabel = DateTimeFormatter.ofPattern("d MMMM", displayLocale)
            .format(salaryPeriod.startDate)
        val remainingByLoanKey = latestRemainingBalances(parsedRecords, zoneId)

        val overviews = ownedLoans.map { loan ->
            val loanContainerId = FinancialContainerIdFactory.loanId(loan.bank, loan.loanType)
            val loanKey = loanKey(loan.bank.id, loan.loanType)
            var periodPaymentTotal = Money.zero(primaryCurrency)
            transactions.forEach { tx ->
                if (tx.type != FinancialTransactionType.LOAN_REPAYMENT) return@forEach
                if (tx.destinationContainerId != loanContainerId) return@forEach
                if (tx.occurredAt.isBefore(salaryPeriodStart) || !tx.occurredAt.isBefore(salaryPeriodEnd)) {
                    return@forEach
                }
                val amount = TransactionAmountResolver.effectiveAmount(
                    tx = tx,
                    primaryCurrency = primaryCurrency,
                    sarEquivalents = sarEquivalents,
                ) ?: return@forEach
                periodPaymentTotal += amount
            }
            val periodPayment = SignedMoneyAmount.of(periodPaymentTotal)
            val remaining = remainingByLoanKey[loanKey]
            LoanOverview(
                bank = loan.bank,
                loanType = loan.loanType,
                displayLabel = loan.displayName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: RegistryDisplayLabels.loanLabel(loan),
                remainingBalance = remaining?.first,
                remainingBalanceAsOf = remaining?.second,
                salaryPeriodPayment = periodPayment,
                salaryPeriodLabel = salaryPeriodLabel,
            )
        }.sortedBy { it.displayLabel }

        return LoansOverview(
            loans = overviews,
            salaryPeriodLabel = salaryPeriodLabel,
            currency = primaryCurrency,
        )
    }

    private fun loanKey(bankId: String, loanType: com.baraa.masroof.domain.model.LoanType): String =
        "$bankId:${loanType.name}"

    private fun latestRemainingBalances(
        parsedRecords: List<ParsedEventRecord>,
        zoneId: ZoneId,
    ): Map<String, Pair<Money, Instant?>> {
        val latest = mutableMapOf<String, Pair<Money, Instant?>>()
        parsedRecords.forEach { record ->
            val event = record.event
            if (event.messageFamily != MessageFamily.FINANCING_INSTALLMENT) return@forEach
            val loanType = LoanTypeResolver.fromLabel(event.counterparty) ?: return@forEach
            val balance = record.details.outstandingBalance ?: return@forEach
            val occurredAt = event.occurredAt
                ?: record.details.occurredAtLocal?.atZone(zoneId)?.toInstant()
            val key = loanKey(event.bank.id, loanType)
            val existing = latest[key]
            if (shouldReplaceRemainingBalance(existing, occurredAt)) {
                latest[key] = balance to occurredAt
            }
        }
        return latest
    }

    private fun shouldReplaceRemainingBalance(
        existing: Pair<Money, Instant?>?,
        candidateAt: Instant?,
    ): Boolean {
        if (existing == null) return true
        val existingAt = existing.second
        return when {
            candidateAt == null && existingAt == null -> true
            candidateAt == null -> false
            existingAt == null -> true
            else -> candidateAt.isAfter(existingAt)
        }
    }
}
