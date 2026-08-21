package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money

/**
 * Canonical outflow breakdown for a current account in a salary period.
 *
 * [coreTotal] sums the six standard outflow categories and is used for aggregate
 * net movement. [total] adds [selfTransfersOut] for a single-account remaining view.
 */
data class AccountOutflow(
    val currency: Currency,
    val externalTransfersOut: Money,
    val creditCardPayments: Money,
    val cashWithdrawals: Money,
    val billPayments: Money,
    val posPurchases: Money,
    val fees: Money,
    val selfTransfersOut: Money,
) {
    val coreTotal: Money
        get() = externalTransfersOut +
            creditCardPayments +
            cashWithdrawals +
            billPayments +
            posPurchases +
            fees

    val total: Money
        get() = coreTotal + selfTransfersOut

    init {
        require(externalTransfersOut.currency == currency)
        require(creditCardPayments.currency == currency)
        require(cashWithdrawals.currency == currency)
        require(billPayments.currency == currency)
        require(posPurchases.currency == currency)
        require(fees.currency == currency)
        require(selfTransfersOut.currency == currency)
    }

    companion object {
        fun zero(currency: Currency): AccountOutflow =
            AccountOutflow(
                currency = currency,
                externalTransfersOut = Money.zero(currency),
                creditCardPayments = Money.zero(currency),
                cashWithdrawals = Money.zero(currency),
                billPayments = Money.zero(currency),
                posPurchases = Money.zero(currency),
                fees = Money.zero(currency),
                selfTransfersOut = Money.zero(currency),
            )
    }
}
