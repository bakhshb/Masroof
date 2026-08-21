package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord

data class OwnedAccountPeriodSummary(
    val bank: com.baraa.masroof.domain.model.Bank,
    val maskedNumber: String,
    val summary: CurrentAccountSummary,
) {
    val periodNet: SignedMoneyAmount get() = summary.netMovement
    val accountRemaining: SignedMoneyAmount get() = summary.accountRemaining
    val totalInflow: Money get() = summary.totalInflow + summary.selfTransfersIn
    val totalOutflow: Money get() = summary.totalOutflow + summary.selfTransfersOut
}

object OwnedAccountPeriodSummaryCalculator {
    fun summarize(
        ownedAccounts: List<com.baraa.masroof.domain.model.AccountRegistryEntry>,
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        rawSmsById: Map<String, RawSms>,
    ): List<OwnedAccountPeriodSummary> =
        ownedAccounts.map { account ->
            val containerId = FinancialContainerIdFactory.accountId(account.bank, account.maskedNumber)
            val last4s = CurrentAccountTransactionScope.ownedAccountLast4sFromMaskedNumbers(
                listOf(account.maskedNumber),
            )
            val summary = CurrentAccountSummaryCalculator.summarize(
                transactions = transactions,
                parsedRecords = parsedRecords,
                primaryCurrency = primaryCurrency,
                sarEquivalents = sarEquivalents,
                ownedAccountContainerIds = setOf(containerId),
                ownedAccountLast4s = last4s,
                rawSmsById = rawSmsById,
            )
            OwnedAccountPeriodSummary(
                bank = account.bank,
                maskedNumber = account.maskedNumber,
                summary = summary,
            )
        }
}
