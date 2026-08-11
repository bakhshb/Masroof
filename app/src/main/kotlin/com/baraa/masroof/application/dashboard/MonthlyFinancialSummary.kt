package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.period.FinancialPeriod
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Currency-scoped monthly dashboard projection derived from FinancialTransaction rows.
 */
data class MonthlyFinancialSummary(
    val period: FinancialPeriod,
    val currency: Currency,
    val spendingGross: Money,
    val refunds: Money,
    val spendingNet: SignedMoneyAmount,
    val income: Money,
    val externalTransfersIn: Money,
    val externalTransfersOut: Money,
    val creditCardPayments: Money,
    val cashWithdrawals: Money,
    val selfTransfers: Money,
    val transactionCount: Int,
    val reviewRequiredCount: Int,
    val excludedOtherCurrencyCount: Int = 0,
) {
    /** Salary/reward income plus external inbound transfers (separate buckets in UI). */
    val combinedIncoming: Money
        get() = income + externalTransfersIn

    /**
     * Period net cash change from external flows and spending (excludes self-transfers,
     * card payments, and cash withdrawals which are neutral internal movements).
     */
    val netCashFlow: SignedMoneyAmount
        get() {
            val net = income.amount
                .add(externalTransfersIn.amount)
                .subtract(externalTransfersOut.amount)
                .subtract(spendingNet.amount)
                .setScale(Money.SCALE, RoundingMode.HALF_EVEN)
            return SignedMoneyAmount(net, currency)
        }

    init {
        require(spendingGross.currency == currency)
        require(refunds.currency == currency)
        require(spendingNet.currency == currency)
        require(income.currency == currency)
        require(externalTransfersIn.currency == currency)
        require(externalTransfersOut.currency == currency)
        require(creditCardPayments.currency == currency)
        require(cashWithdrawals.currency == currency)
        require(selfTransfers.currency == currency)
        require(transactionCount >= 0)
        require(reviewRequiredCount >= 0)
        require(excludedOtherCurrencyCount >= 0)
    }

    companion object {
        fun empty(
            period: FinancialPeriod,
            currency: Currency,
            reviewRequiredCount: Int = 0,
            excludedOtherCurrencyCount: Int = 0,
        ): MonthlyFinancialSummary =
            MonthlyFinancialSummary(
                period = period,
                currency = currency,
                spendingGross = Money.zero(currency),
                refunds = Money.zero(currency),
                spendingNet = SignedMoneyAmount.zero(currency),
                income = Money.zero(currency),
                externalTransfersIn = Money.zero(currency),
                externalTransfersOut = Money.zero(currency),
                creditCardPayments = Money.zero(currency),
                cashWithdrawals = Money.zero(currency),
                selfTransfers = Money.zero(currency),
                transactionCount = 0,
                reviewRequiredCount = reviewRequiredCount,
                excludedOtherCurrencyCount = excludedOtherCurrencyCount,
            )
    }
}
