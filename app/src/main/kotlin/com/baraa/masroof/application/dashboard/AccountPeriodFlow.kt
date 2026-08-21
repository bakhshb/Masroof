package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import java.math.RoundingMode

/**
 * Display totals for a current-account period summary.
 *
 * Two views exist and must not be mixed in the UI:
 * - [CurrentAccountSummary.cashPosition] — per-account remaining; includes self-transfers.
 * - [CurrentAccountSummary.externalMovement] — fleet external flow; excludes self-transfers.
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
    fun cashPosition(): AccountFlowTotals = summary.cashPosition()
}

/**
 * Fleet view across owned accounts for the salary period.
 *
 * [totalRemaining] is the sum of each account's [AccountFlowTotals.remaining] from
 * [OwnedAccountPeriodFlow.cashPosition] (self-transfers included per account).
 */
data class OwnedAccountsFlowSummary(
    val accounts: List<OwnedAccountPeriodFlow>,
) {
    val currency: Currency?
        get() = accounts.firstOrNull()?.summary?.currency

    val totalRemaining: SignedMoneyAmount?
        get() {
            val currency = currency ?: return null
            return accounts
                .map { it.cashPosition().remaining }
                .fold(SignedMoneyAmount.zero(currency), SignedMoneyAmount::plus)
        }

    /** Sum of per-account cash-position inflows (includes self-transfers received). */
    val totalInflow: Money?
        get() {
            val currency = currency ?: return null
            return accounts
                .map { it.cashPosition().inflow }
                .fold(Money.zero(currency), Money::plus)
        }

    /** Sum of per-account cash-position outflows (includes self-transfers sent). */
    val totalOutflow: Money?
        get() {
            val currency = currency ?: return null
            return accounts
                .map { it.cashPosition().outflow }
                .fold(Money.zero(currency), Money::plus)
        }

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
