package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Cash movement on owned current-account containers for a salary period.
 *
 * Credit-card purchases are excluded — they are tracked as liability on the card section.
 */
data class CurrentAccountSummary(
    val currency: Currency,
    val income: Money,
    val externalTransfersIn: Money,
    val creditCardPayments: Money,
    val billPayments: Money,
    val externalTransfersOut: Money,
    val cashWithdrawals: Money,
    val posPurchases: Money,
    val fees: Money,
) {
    val totalInflow: Money
        get() = income + externalTransfersIn

    val totalOutflow: Money
        get() = creditCardPayments + billPayments + externalTransfersOut + cashWithdrawals + posPurchases + fees

    val netMovement: SignedMoneyAmount
        get() {
            val net = totalInflow.amount
                .subtract(totalOutflow.amount)
                .setScale(Money.SCALE, RoundingMode.HALF_EVEN)
            return SignedMoneyAmount(net, currency)
        }

    val accountSpendingGross: Money
        get() = billPayments + posPurchases + fees

    init {
        listOf(
            income,
            externalTransfersIn,
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
 * Period spending split: cash left the account vs liability on credit cards.
 */
data class SpendingSplitSummary(
    val currency: Currency,
    val fromCurrentAccount: Money,
    val onCreditCard: SignedMoneyAmount,
) {
    /** Purchases and bills (mada + bills), excluding transfers, cash, and card settlement. */
    val totalNet: SignedMoneyAmount
        get() {
            val net = fromCurrentAccount.amount
                .add(onCreditCard.amount)
                .setScale(Money.SCALE, RoundingMode.HALF_EVEN)
            return SignedMoneyAmount(net, currency)
        }

    init {
        require(fromCurrentAccount.currency == currency)
        require(onCreditCard.currency == currency)
    }
}
