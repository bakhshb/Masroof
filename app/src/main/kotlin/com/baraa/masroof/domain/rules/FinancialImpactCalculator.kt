package com.baraa.masroof.domain.rules

import com.baraa.masroof.domain.model.FinancialTransactionType

/**
 * Maps a classified [FinancialTransactionType] to expense / income / net-worth
 * effects using DOMAIN rules (D-001, D-004…D-008, §11–12).
 */
object FinancialImpactCalculator {
    fun forType(type: FinancialTransactionType): FinancialImpact =
        when (type) {
            FinancialTransactionType.SELF_TRANSFER ->
                FinancialImpact(
                    countsAsExpense = false,
                    countsAsIncome = false,
                    netWorthEffect = NetWorthEffect.ZERO,
                )

            FinancialTransactionType.CREDIT_CARD_PAYMENT ->
                FinancialImpact(
                    countsAsExpense = false,
                    countsAsIncome = false,
                    // Cash down, liability down — net worth unchanged (D-007).
                    netWorthEffect = NetWorthEffect.ZERO,
                )

            FinancialTransactionType.EXPENSE ->
                FinancialImpact(
                    countsAsExpense = true,
                    countsAsIncome = false,
                    netWorthEffect = NetWorthEffect.DECREASE,
                )

            FinancialTransactionType.INCOME ->
                FinancialImpact(
                    countsAsExpense = false,
                    countsAsIncome = true,
                    netWorthEffect = NetWorthEffect.INCREASE,
                )

            FinancialTransactionType.EXTERNAL_TRANSFER_IN ->
                // Not automatically income (D-004, §12). Cash direction alone does
                // not determine net worth (loan, gift, reimbursement, etc.).
                FinancialImpact(
                    countsAsExpense = false,
                    countsAsIncome = false,
                    netWorthEffect = NetWorthEffect.UNRESOLVED,
                )

            FinancialTransactionType.EXTERNAL_TRANSFER_OUT ->
                // Not automatically expense (D-005, §11). Cash direction alone does
                // not determine net worth (loan repayment, spending, investment, etc.).
                FinancialImpact(
                    countsAsExpense = false,
                    countsAsIncome = false,
                    netWorthEffect = NetWorthEffect.UNRESOLVED,
                )

            FinancialTransactionType.REFUND ->
                // Offsets a prior purchase; not ordinary income (D-008, §12).
                FinancialImpact(
                    countsAsExpense = false,
                    countsAsIncome = false,
                    netWorthEffect = NetWorthEffect.INCREASE,
                )

            FinancialTransactionType.CASH_WITHDRAWAL ->
                // Bank cash → physical cash; not an expense by default (§11).
                FinancialImpact(
                    countsAsExpense = false,
                    countsAsIncome = false,
                    netWorthEffect = NetWorthEffect.ZERO,
                )

            FinancialTransactionType.BILL_PAYMENT ->
                FinancialImpact(
                    countsAsExpense = true,
                    countsAsIncome = false,
                    netWorthEffect = NetWorthEffect.DECREASE,
                )

            FinancialTransactionType.FEE ->
                // Fee is its own type; economically it reduces net worth like spending.
                FinancialImpact(
                    countsAsExpense = true,
                    countsAsIncome = false,
                    netWorthEffect = NetWorthEffect.DECREASE,
                )

            FinancialTransactionType.ADJUSTMENT,
            FinancialTransactionType.UNKNOWN,
            ->
                FinancialImpact(
                    countsAsExpense = false,
                    countsAsIncome = false,
                    netWorthEffect = NetWorthEffect.UNRESOLVED,
                )
        }

    fun unresolved(): FinancialImpact =
        FinancialImpact(
            countsAsExpense = false,
            countsAsIncome = false,
            netWorthEffect = NetWorthEffect.UNRESOLVED,
        )
}
