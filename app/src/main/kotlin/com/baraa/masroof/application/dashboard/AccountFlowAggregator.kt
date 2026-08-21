package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency

/**
 * Combines per-account [CurrentAccountSummary] values into one aggregate summary.
 *
 * Used when the UI needs raw category buckets across all owned accounts.
 * For display totals prefer [CurrentAccountSummary.externalMovement] or
 * [OwnedAccountsFlowSummary.totalRemaining].
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
