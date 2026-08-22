package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord

/**
 * Period cash-flow view for one owned current account (e.g. last4 `3001`).
 *
 * Buckets live on [flow]; [remaining] and [cashPosition] are always derived.
 * Credit-card **purchases** (card container) are excluded — only [creditCardPayment]
 * (سداد بطاقة from this account) is tracked here.
 */
data class OwnedAccount(
    val id: String,
    val bank: Bank,
    val maskedNumber: String,
    val containerId: String,
    val summary: CurrentAccountSummary,
) {
    val flow: AccountFlow get() = summary.accountFlow()

    val currency: Currency get() = flow.currency

    // —— Inflows ——
    val salary: Money get() = flow.salary
    val otherIncome: Money get() = flow.otherIncome
    val externalTransfersIn: Money get() = flow.externalTransfersIn
    val internalTransfersIn: Money get() = flow.internalTransfersIn
    val externalIn: Money get() = flow.externalIn
    val totalIn: Money get() = flow.totalIn

    // —— Outflows ——
    val externalTransfersOut: Money get() = flow.externalTransfersOut
    /** سداد بطاقة ائتمانية — settlement from this account to a credit card. */
    val creditCardPayment: Money get() = flow.creditCardPayments
    val cashWithdrawal: Money get() = flow.cashWithdrawals
    val billPayment: Money get() = flow.billPayments
    /** POS / مدى debited from this account. */
    val posPurchase: Money get() = flow.posPurchases
    val fees: Money get() = flow.fees
    val loan: Money get() = flow.loan
    val internalTransfersOut: Money get() = flow.internalTransfersOut
    val externalOut: Money get() = flow.externalOut
    val totalOut: Money get() = flow.totalOut

    /** وارد − منصرف خارجي (self-transfers excluded). */
    val remaining: SignedMoneyAmount get() = flow.externalSummary().remaining

    /** كل وارد − كل منصرف (includes internal transfers). */
    val cashPosition: SignedMoneyAmount get() = flow.accountSummary().remaining

    fun externalSummary(): AccountFlowSummary = flow.externalSummary()

    fun accountSummary(): AccountFlowSummary = flow.accountSummary()

    companion object {
        fun from(period: OwnedAccountPeriodSummary): OwnedAccount? {
            val containerId = FinancialContainerIdFactory.accountId(period.bank, period.maskedNumber)
                ?: return null
            return OwnedAccount(
                id = accountIdFromMasked(period.maskedNumber),
                bank = period.bank,
                maskedNumber = period.maskedNumber,
                containerId = containerId,
                summary = period.summary,
            )
        }

        fun from(
            bank: Bank,
            maskedNumber: String,
            summary: CurrentAccountSummary,
        ): OwnedAccount? {
            val containerId = FinancialContainerIdFactory.accountId(bank, maskedNumber) ?: return null
            return OwnedAccount(
                id = accountIdFromMasked(maskedNumber),
                bank = bank,
                maskedNumber = maskedNumber,
                containerId = containerId,
                summary = summary,
            )
        }

        fun buildAll(
            ownedAccounts: List<com.baraa.masroof.domain.model.AccountRegistryEntry>,
            transactions: List<FinancialTransaction>,
            parsedRecords: List<ParsedEventRecord>,
            primaryCurrency: Currency,
            sarEquivalents: Map<String, Money>,
            rawSmsById: Map<String, RawSms>,
        ): List<OwnedAccount> =
            OwnedAccountPeriodSummaryCalculator.summarize(
                ownedAccounts = ownedAccounts,
                transactions = transactions,
                parsedRecords = parsedRecords,
                primaryCurrency = primaryCurrency,
                sarEquivalents = sarEquivalents,
                rawSmsById = rawSmsById,
            ).mapNotNull(OwnedAccount::from)

        private fun accountIdFromMasked(maskedNumber: String): String {
            val trimmed = maskedNumber.trim()
            return if (trimmed.length <= 4) trimmed else trimmed.takeLast(4)
        }
    }
}
