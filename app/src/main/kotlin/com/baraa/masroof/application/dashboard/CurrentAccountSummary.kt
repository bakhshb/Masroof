package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Cash movement on owned current-account containers for a salary period.
 *
 * Credit-card purchases are excluded — they are tracked as liability on the card section.
 * Self-transfers between owned accounts are neutral for [netMovement] across all accounts,
 * but included in [accountRemaining] for a single account view.
 */
data class CurrentAccountSummary(
    val currency: Currency,
    val salary: Money,
    val otherIncome: Money,
    val externalTransfersIn: Money,
    val selfTransfersIn: Money,
    val selfTransfersOut: Money,
    val creditCardPayments: Money,
    val billPayments: Money,
    val externalTransfersOut: Money,
    val cashWithdrawals: Money,
    val posPurchases: Money,
    val fees: Money,
) {
    val totalInflow: Money
        get() = salary + otherIncome + externalTransfersIn

    val totalOutflow: Money
        get() = creditCardPayments + billPayments + externalTransfersOut + cashWithdrawals + posPurchases + fees

    val netMovement: SignedMoneyAmount
        get() {
            val net = totalInflow.amount
                .subtract(totalOutflow.amount)
                .setScale(Money.SCALE, RoundingMode.HALF_EVEN)
            return SignedMoneyAmount(net, currency)
        }

    /** In − out for one account in the period, including self-transfers. */
    val accountRemaining: SignedMoneyAmount
        get() = SignedMoneyAmount.difference(
            totalInflow + selfTransfersIn,
            totalOutflow + selfTransfersOut,
        )

    val accountSpendingGross: Money
        get() = billPayments + posPurchases + fees

    init {
        listOf(
            salary,
            otherIncome,
            externalTransfersIn,
            selfTransfersIn,
            selfTransfersOut,
            creditCardPayments,
            billPayments,
            externalTransfersOut,
            cashWithdrawals,
            posPurchases,
            fees,
        ).forEach { require(it.currency == currency) }
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
