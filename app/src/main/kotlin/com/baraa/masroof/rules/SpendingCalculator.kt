package com.baraa.masroof.rules

import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.FinancialTreatment
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Computes a per-treatment breakdown of saved transactions.
 *
 * Rules (matching the spec):
 *  - **Net expenses = confirmed expenses + bank fees − valid refunds.**
 *  - **Credit-card payments** are NOT in expenses.
 *  - **Internal transfers** are NOT in expenses or income.
 *  - **Investments** are tracked separately.
 *  - **Pending, declined, and unresolved transactions** are NOT in
 *    confirmed spending.
 *
 * All amounts are [BigDecimal] with HALF_UP rounding at 2 decimal places.
 * The calculator never uses Float / Double.
 */
object SpendingCalculator {

    data class Breakdown(
        val grossExpenses: BigDecimal,
        val refunds: BigDecimal,
        val netExpenses: BigDecimal,
        val income: BigDecimal,
        val investments: BigDecimal,
        val internalTransfers: BigDecimal,
        val creditCardPayments: BigDecimal,
        val bankFees: BigDecimal,
        val cashWithdrawals: BigDecimal,
        val ignoredAndDeclined: BigDecimal,
        val transactionsRequiringReview: Int,
        val totalTransactions: Int,
    )

    fun calculate(transactions: List<TransactionEntity>): Breakdown {
        var gross = BigDecimal.ZERO
        var refunds = BigDecimal.ZERO
        var income = BigDecimal.ZERO
        var investments = BigDecimal.ZERO
        var internal = BigDecimal.ZERO
        var creditCard = BigDecimal.ZERO
        var fees = BigDecimal.ZERO
        var cashOut = BigDecimal.ZERO
        var ignored = BigDecimal.ZERO
        var pending = 0
        val total = transactions.size

        for (t in transactions) {
            val amt = t.amount
            // A transaction counts toward spending if it was either
            // auto-classified confidently (`!needsReview`) OR the user
            // explicitly confirmed it (`userConfirmed`).
            val counted = !t.needsReview || t.userConfirmed
            // Pending / review counter: any transaction still marked as
            // needing review (whether needsReview is true, or the rule
            // engine flagged it as PENDING_REVIEW) is one less than
            // confirmed. Avoid double-counting when both flags are set.
            when {
                t.needsReview && !t.userConfirmed -> pending += 1
                t.financialTreatment == FinancialTreatment.PENDING_REVIEW -> pending += 1
            }
            when (t.financialTreatment) {
                FinancialTreatment.EXPENSE -> if (counted) gross = gross.add(amt ?: BigDecimal.ZERO)
                FinancialTreatment.REFUND -> refunds = refunds.add(amt ?: BigDecimal.ZERO)
                FinancialTreatment.INCOME -> if (counted) income = income.add(amt ?: BigDecimal.ZERO)
                FinancialTreatment.INVESTMENT -> investments = investments.add(amt ?: BigDecimal.ZERO)
                FinancialTreatment.INTERNAL_TRANSFER -> internal = internal.add(amt ?: BigDecimal.ZERO)
                FinancialTreatment.CREDIT_CARD_PAYMENT -> creditCard = creditCard.add(amt ?: BigDecimal.ZERO)
                FinancialTreatment.BANK_FEE -> if (counted) fees = fees.add(amt ?: BigDecimal.ZERO)
                FinancialTreatment.CASH_WITHDRAWAL -> cashOut = cashOut.add(amt ?: BigDecimal.ZERO)
                FinancialTreatment.IGNORED -> ignored = ignored.add(amt ?: BigDecimal.ZERO)
                FinancialTreatment.PENDING_REVIEW -> { /* counted above */ }
            }
        }

        val netExpenses = gross.add(fees).subtract(refunds).setScale(2, RoundingMode.HALF_UP)
        return Breakdown(
            grossExpenses = gross.setScale(2, RoundingMode.HALF_UP),
            refunds = refunds.setScale(2, RoundingMode.HALF_UP),
            netExpenses = netExpenses,
            income = income.setScale(2, RoundingMode.HALF_UP),
            investments = investments.setScale(2, RoundingMode.HALF_UP),
            internalTransfers = internal.setScale(2, RoundingMode.HALF_UP),
            creditCardPayments = creditCard.setScale(2, RoundingMode.HALF_UP),
            bankFees = fees.setScale(2, RoundingMode.HALF_UP),
            cashWithdrawals = cashOut.setScale(2, RoundingMode.HALF_UP),
            ignoredAndDeclined = ignored.setScale(2, RoundingMode.HALF_UP),
            transactionsRequiringReview = pending,
            totalTransactions = total,
        )
    }
}
