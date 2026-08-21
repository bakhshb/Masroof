package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency

/**
 * Combines per-account [CurrentAccountSummary] values into one aggregate summary.
 *
 * Used when the UI needs totals across all owned accounts (e.g. accounts summary hero).
 */
object AccountFlowAggregator {
    fun aggregate(summaries: Collection<CurrentAccountSummary>): CurrentAccountSummary {
        if (summaries.isEmpty()) return CurrentAccountSummary.zero(Currency.SAR)
        return CurrentAccountSummary(
            inflow = AccountInflow.sum(summaries.map { it.inflow }),
            outflow = AccountOutflow.sum(summaries.map { it.outflow }),
        )
    }
}

enum class AccountFlowTotalsMode {
    /**
     * Salary + external in/out only — self-transfers between owned accounts excluded.
     * Used for home, accounts summary, and per-account remaining display.
     */
    AGGREGATE_NET,

    /** Includes self-transfers in inflow/outflow totals (cash-position view). */
    PER_ACCOUNT_REMAINING,
}

fun CurrentAccountSummary.flowInflow(mode: AccountFlowTotalsMode): com.baraa.masroof.core.money.Money =
    when (mode) {
        AccountFlowTotalsMode.AGGREGATE_NET -> inflow.coreTotal
        AccountFlowTotalsMode.PER_ACCOUNT_REMAINING -> inflow.total
    }

fun CurrentAccountSummary.flowOutflow(mode: AccountFlowTotalsMode): com.baraa.masroof.core.money.Money =
    when (mode) {
        AccountFlowTotalsMode.AGGREGATE_NET -> outflow.coreTotal
        AccountFlowTotalsMode.PER_ACCOUNT_REMAINING -> outflow.total
    }

fun CurrentAccountSummary.flowRemaining(mode: AccountFlowTotalsMode): SignedMoneyAmount =
    when (mode) {
        AccountFlowTotalsMode.AGGREGATE_NET -> netMovement
        AccountFlowTotalsMode.PER_ACCOUNT_REMAINING ->
            SignedMoneyAmount.difference(inflow.total, outflow.total)
    }
