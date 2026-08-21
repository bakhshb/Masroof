package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.model.Bank

/** Display totals for UI layers — alias of [AccountFlowSummary]. */
typealias AccountFlowTotals = AccountFlowSummary

/** Per-owned-account period flow used in accounts summary and account cards. */
data class OwnedAccountPeriodFlow(
    val bank: Bank,
    val maskedNumber: String,
    val summary: CurrentAccountSummary,
) {
    val flow: AccountFlow get() = summary.accountFlow()

    fun externalSummary(): AccountFlowSummary = flow.externalSummary()

    fun accountSummary(): AccountFlowSummary = flow.accountSummary()
}

/**
 * Fleet view across owned accounts for the salary period.
 *
 * Hero totals use [accountSummary] (all in − all out).
 * Per-account cards use [OwnedAccountPeriodFlow.externalSummary].
 */
data class OwnedAccountsFlowSummary(
    val accounts: List<OwnedAccountPeriodFlow>,
) {
    private val fleet: FleetAccountFlow
        get() = FleetAccountFlow(accounts.map { it.flow })

    val currency get() = fleet.currency

    /** Sum of every account's total in − total out. */
    val totalRemaining get() = fleet.accountSummary()?.remaining

    val totalInflow get() = fleet.accountSummary()?.inflow

    val totalOutflow get() = fleet.accountSummary()?.outflow

    fun accountSummary(): AccountFlowSummary? = fleet.accountSummary()

    fun externalSummary(): AccountFlowSummary? = fleet.externalSummary()

    companion object {
        fun fromPeriodSummaries(summaries: List<OwnedAccountPeriodSummary>): OwnedAccountsFlowSummary =
            OwnedAccountsFlowSummary(
                accounts = summaries.map { period ->
                    OwnedAccountPeriodFlow(
                        bank = period.bank,
                        maskedNumber = period.maskedNumber,
                        summary = period.summary,
                    )
                },
            )

        fun fromSummaries(
            accounts: List<Pair<Bank, String>>,
            summaries: List<CurrentAccountSummary>,
        ): OwnedAccountsFlowSummary {
            require(accounts.size == summaries.size)
            return OwnedAccountsFlowSummary(
                accounts = accounts.zip(summaries) { (bank, masked), summary ->
                    OwnedAccountPeriodFlow(bank = bank, maskedNumber = masked, summary = summary)
                },
            )
        }
    }
}

fun CurrentAccountSummary.externalMovement(): AccountFlowTotals =
    accountFlow().externalSummary().toTotals()

fun CurrentAccountSummary.cashPosition(): AccountFlowTotals =
    accountFlow().accountSummary().toTotals()
