package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money

/**
 * Canonical inflow breakdown for a current account in a salary period.
 *
 * [coreTotal] is used for aggregate net movement (excludes self-transfers).
 * [total] includes [selfTransfersIn] for a single-account remaining view.
 */
data class AccountInflow(
    val currency: Currency,
    val salary: Money,
    val otherIncome: Money,
    val externalTransfersIn: Money,
    val selfTransfersIn: Money,
) {
    val coreTotal: Money
        get() = salary + otherIncome + externalTransfersIn

    val total: Money
        get() = coreTotal + selfTransfersIn

    init {
        require(salary.currency == currency)
        require(otherIncome.currency == currency)
        require(externalTransfersIn.currency == currency)
        require(selfTransfersIn.currency == currency)
    }

    companion object {
        fun zero(currency: Currency): AccountInflow =
            AccountInflow(
                currency = currency,
                salary = Money.zero(currency),
                otherIncome = Money.zero(currency),
                externalTransfersIn = Money.zero(currency),
                selfTransfersIn = Money.zero(currency),
            )
    }
}
