package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.period.FinancialPeriod

/**
 * Pure FinancialTransaction → monthly dashboard projection.
 *
 * No Android / Room / Compose dependencies.
 */
object MonthlyFinancialSummaryCalculator {
    fun summarize(
        period: FinancialPeriod,
        transactions: List<FinancialTransaction>,
        reviewRequiredCount: Int,
        primaryCurrency: Currency = Currency.SAR,
    ): MonthlyFinancialSummary {
        val inPrimary = transactions.filter { it.amount.currency == primaryCurrency }
        // Total period count answers "how many transactions exist"; SAR totals stay currency-scoped.
        // Domain Currency currently includes SAR only, so excludedOtherCurrencyCount stays 0 until
        // another currency is added to the canonical enum (no FX conversion in P11).
        val excludedOtherCurrencyCount = transactions.size - inPrimary.size

        var spendingGross = Money.zero(primaryCurrency)
        var refunds = Money.zero(primaryCurrency)
        var income = Money.zero(primaryCurrency)
        var externalTransfersIn = Money.zero(primaryCurrency)
        var externalTransfersOut = Money.zero(primaryCurrency)
        var creditCardPayments = Money.zero(primaryCurrency)
        var cashWithdrawals = Money.zero(primaryCurrency)
        var selfTransfers = Money.zero(primaryCurrency)

        for (tx in inPrimary) {
            when (tx.type) {
                FinancialTransactionType.EXPENSE,
                FinancialTransactionType.FEE,
                -> spendingGross = spendingGross + tx.amount

                FinancialTransactionType.REFUND ->
                    refunds = refunds + tx.amount

                FinancialTransactionType.INCOME ->
                    income = income + tx.amount

                FinancialTransactionType.EXTERNAL_TRANSFER_IN ->
                    externalTransfersIn = externalTransfersIn + tx.amount

                FinancialTransactionType.EXTERNAL_TRANSFER_OUT ->
                    externalTransfersOut = externalTransfersOut + tx.amount

                FinancialTransactionType.CREDIT_CARD_PAYMENT ->
                    creditCardPayments = creditCardPayments + tx.amount

                FinancialTransactionType.CASH_WITHDRAWAL ->
                    cashWithdrawals = cashWithdrawals + tx.amount

                FinancialTransactionType.SELF_TRANSFER ->
                    selfTransfers = selfTransfers + tx.amount

                FinancialTransactionType.ADJUSTMENT,
                FinancialTransactionType.UNKNOWN,
                -> Unit
            }
        }

        return MonthlyFinancialSummary(
            period = period,
            currency = primaryCurrency,
            spendingGross = spendingGross,
            refunds = refunds,
            spendingNet = SignedMoneyAmount.difference(spendingGross, refunds),
            income = income,
            externalTransfersIn = externalTransfersIn,
            externalTransfersOut = externalTransfersOut,
            creditCardPayments = creditCardPayments,
            cashWithdrawals = cashWithdrawals,
            selfTransfers = selfTransfers,
            transactionCount = transactions.size,
            reviewRequiredCount = reviewRequiredCount,
            excludedOtherCurrencyCount = excludedOtherCurrencyCount,
        )
    }
}
