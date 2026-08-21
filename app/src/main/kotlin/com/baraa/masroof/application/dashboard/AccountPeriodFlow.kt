package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import java.math.RoundingMode

/**
 * Display totals for a current-account period summary.
 *
 * UI should use [CurrentAccountSummary.externalMovement] everywhere (v0.2.19 behaviour):
 * core inflow/outflow only; self-transfers shown separately and excluded from الباقي.
 *
 * [CurrentAccountSummary.cashPosition] includes self-transfers and is kept for tests only.
 */
data class AccountFlowTotals(
    val inflow: Money,
    val outflow: Money,
    val remaining: SignedMoneyAmount,
    val selfTransfersIn: Money,
    val selfTransfersOut: Money,
    val includesSelfTransfersInTotals: Boolean,
)

/** Per-owned-account period flow used in accounts summary and account cards. */
data class OwnedAccountPeriodFlow(
    val bank: Bank,
    val maskedNumber: String,
    val summary: CurrentAccountSummary,
) {
    fun externalMovement(): AccountFlowTotals = summary.externalMovement()
}

/**
 * Fleet view across owned accounts for the salary period.
 *
 * Totals use [externalMovement] — self-transfers between owned accounts are neutral.
 */
data class OwnedAccountsFlowSummary(
    val accounts: List<OwnedAccountPeriodFlow>,
) {
    val currency: Currency?
        get() = accounts.firstOrNull()?.summary?.currency

    val totalRemaining: SignedMoneyAmount?
        get() = externalMovement()?.remaining

    val totalInflow: Money?
        get() = externalMovement()?.inflow

    val totalOutflow: Money?
        get() = externalMovement()?.outflow

    /** Combined external movement across all owned accounts (self-transfers excluded). */
    fun externalMovement(): AccountFlowTotals? {
        if (accounts.isEmpty()) return null
        val combined = CurrentAccountSummary.aggregate(accounts.map { it.summary })
        return combined.externalMovement()
    }

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

/** Per-account card/detail: inflow and outflow include transfers between owned accounts. */
fun CurrentAccountSummary.cashPosition(): AccountFlowTotals =
    AccountFlowTotals(
        inflow = inflow.total,
        outflow = outflow.total,
        remaining = SignedMoneyAmount.difference(inflow.total, outflow.total),
        selfTransfersIn = inflow.selfTransfersIn,
        selfTransfersOut = outflow.selfTransfersOut,
        includesSelfTransfersInTotals = true,
    )

/**
 * Home current-account section and flow-detail totals: external categories only;
 * self-transfers are shown separately and excluded from the main formula.
 */
fun CurrentAccountSummary.externalMovement(): AccountFlowTotals =
    AccountFlowTotals(
        inflow = inflow.coreTotal,
        outflow = outflow.coreTotal,
        remaining = netMovement,
        selfTransfersIn = inflow.selfTransfersIn,
        selfTransfersOut = outflow.selfTransfersOut,
        includesSelfTransfersInTotals = false,
    )

fun SignedMoneyAmount.plus(other: SignedMoneyAmount): SignedMoneyAmount {
    require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
    return SignedMoneyAmount(
        amount.add(other.amount).setScale(Money.SCALE, RoundingMode.HALF_EVEN),
        currency,
    )
}
