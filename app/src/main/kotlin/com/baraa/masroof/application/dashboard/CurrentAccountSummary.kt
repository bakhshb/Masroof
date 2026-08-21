package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import java.math.RoundingMode

/**
 * Cash movement on owned current-account containers for a salary period.
 *
 * Inflow and outflow categories are defined by [AccountInflow] and [AccountOutflow].
 * Credit-card purchases on the card itself are excluded — they are tracked on the card section.
 */
data class CurrentAccountSummary(
    val inflow: AccountInflow,
    val outflow: AccountOutflow,
) {
    val currency: Currency get() = inflow.currency

    val salary: Money get() = inflow.salary
    val otherIncome: Money get() = inflow.otherIncome
    val externalTransfersIn: Money get() = inflow.externalTransfersIn
    val selfTransfersIn: Money get() = inflow.selfTransfersIn

    val creditCardPayments: Money get() = outflow.creditCardPayments
    val billPayments: Money get() = outflow.billPayments
    val externalTransfersOut: Money get() = outflow.externalTransfersOut
    val cashWithdrawals: Money get() = outflow.cashWithdrawals
    val posPurchases: Money get() = outflow.posPurchases
    val fees: Money get() = outflow.fees
    val selfTransfersOut: Money get() = outflow.selfTransfersOut

    val totalInflow: Money get() = inflow.coreTotal
    val totalOutflow: Money get() = outflow.coreTotal
    val accountInflow: Money get() = inflow.total
    val accountOutflow: Money get() = outflow.total

    val netMovement: SignedMoneyAmount
        get() {
            val net = totalInflow.amount
                .subtract(totalOutflow.amount)
                .setScale(Money.SCALE, RoundingMode.HALF_EVEN)
            return SignedMoneyAmount(net, currency)
        }

    val accountRemaining: SignedMoneyAmount
        get() = SignedMoneyAmount.difference(accountInflow, accountOutflow)

    val accountSpendingGross: Money
        get() = billPayments + posPurchases + fees

    init {
        require(inflow.currency == outflow.currency)
    }

    companion object {
        fun of(
            currency: Currency,
            salary: Money,
            otherIncome: Money,
            externalTransfersIn: Money,
            selfTransfersIn: Money,
            creditCardPayments: Money,
            billPayments: Money,
            externalTransfersOut: Money,
            cashWithdrawals: Money,
            posPurchases: Money,
            fees: Money,
            selfTransfersOut: Money,
        ): CurrentAccountSummary =
            CurrentAccountSummary(
                inflow = AccountInflow(
                    currency = currency,
                    salary = salary,
                    otherIncome = otherIncome,
                    externalTransfersIn = externalTransfersIn,
                    selfTransfersIn = selfTransfersIn,
                ),
                outflow = AccountOutflow(
                    currency = currency,
                    externalTransfersOut = externalTransfersOut,
                    creditCardPayments = creditCardPayments,
                    cashWithdrawals = cashWithdrawals,
                    billPayments = billPayments,
                    posPurchases = posPurchases,
                    fees = fees,
                    selfTransfersOut = selfTransfersOut,
                ),
            )

        fun zero(currency: Currency): CurrentAccountSummary =
            CurrentAccountSummary(
                inflow = AccountInflow.zero(currency),
                outflow = AccountOutflow.zero(currency),
            )
    }
}

/**
 * Period spending: everything that left the current account in the period.
 */
data class SpendingSplitSummary(
    val currency: Currency,
    val totalSpending: Money,
    val creditCardPurchases: SignedMoneyAmount,
) {
    init {
        require(totalSpending.currency == currency)
        require(creditCardPurchases.currency == currency)
    }
}
