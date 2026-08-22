package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank

/**
 * Fleet view across [OwnedAccount] instances for the salary period.
 *
 * Hero totals use [totalRemaining] (all in − all out per account, summed).
 * Per-account cards use [OwnedAccount.remaining] (external movement only).
 */
data class AccountsSummary(
    val accounts: List<OwnedAccount>,
) {
    private val fleet: FleetAccountFlow
        get() = FleetAccountFlow(accounts.map { it.flow })

    val currency: Currency?
        get() = fleet.currency

    /** Sum of every account's total in − total out. */
    val totalRemaining: SignedMoneyAmount?
        get() = fleet.accountSummary()?.remaining

    val totalInflow: Money?
        get() = fleet.accountSummary()?.inflow

    val totalOutflow: Money?
        get() = fleet.accountSummary()?.outflow

    /** Fleet external movement (internal transfers excluded from totals). */
    val externalRemaining: SignedMoneyAmount?
        get() = fleet.externalSummary()?.remaining

    fun accountSummary(): AccountFlowSummary? = fleet.accountSummary()

    fun externalSummary(): AccountFlowSummary? = fleet.externalSummary()

    fun totalCreditCardPayments(): Money =
        sumBucket { it.creditCardPayment }

    fun totalCashWithdrawals(): Money =
        sumBucket { it.cashWithdrawal }

    fun totalBillPayments(): Money =
        sumBucket { it.billPayment }

    fun totalPosPurchases(): Money =
        sumBucket { it.posPurchase }

    fun totalFees(): Money =
        sumBucket { it.fees }

    fun totalExternalTransfersOut(): Money =
        sumBucket { it.externalTransfersOut }

    private fun sumBucket(selector: (OwnedAccount) -> Money): Money {
        val c = currency ?: return Money.zero(Currency.SAR)
        return accounts.fold(Money.zero(c)) { acc, account -> acc + selector(account) }
    }

    companion object {
        fun from(periodSummaries: List<OwnedAccountPeriodSummary>): AccountsSummary =
            AccountsSummary(periodSummaries.mapNotNull(OwnedAccount::from))

        fun fromSummaries(
            accounts: List<Pair<Bank, String>>,
            summaries: List<CurrentAccountSummary>,
        ): AccountsSummary {
            require(accounts.size == summaries.size)
            return AccountsSummary(
                accounts = accounts.zip(summaries) { (bank, masked), summary ->
                    OwnedAccount.from(bank, masked, summary)
                }.filterNotNull(),
            )
        }
    }
}

/** @see AccountsSummary */
typealias OwnedAccountsFlowSummary = AccountsSummary

/** @see OwnedAccount */
typealias OwnedAccountPeriodFlow = OwnedAccount
