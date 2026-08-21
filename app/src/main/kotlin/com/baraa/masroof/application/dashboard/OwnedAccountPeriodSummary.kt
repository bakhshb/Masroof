package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.time.Instant

data class OwnedAccountPeriodSummary(
    val bank: Bank,
    val maskedNumber: String,
    val summary: CurrentAccountSummary,
    val remainingBalance: SignedMoneyAmount? = null,
    val remainingBalanceUpdatedAt: Instant? = null,
) {
    val periodNet: SignedMoneyAmount get() = summary.netMovement
    val totalInflow: Money get() = summary.totalInflow
    val totalOutflow: Money get() = summary.totalOutflow
}

object OwnedAccountPeriodSummaryCalculator {
    fun summarize(
        ownedAccounts: List<com.baraa.masroof.domain.model.AccountRegistryEntry>,
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        rawSmsById: Map<String, RawSms>,
        remainingRollForwardTransactions: List<FinancialTransaction> = transactions,
    ): List<OwnedAccountPeriodSummary> {
        val snapshots = CurrentAccountBalanceBuilder.latestSnapshots(
            ownedAccounts = ownedAccounts,
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
        )
        val remainingByAccount = CurrentAccountBalanceBuilder.remainingByAccount(
            snapshots = snapshots,
            transactions = remainingRollForwardTransactions,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
        )

        return ownedAccounts.map { account ->
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
            val snapshot = snapshots[containerId]
            OwnedAccountPeriodSummary(
                bank = account.bank,
                maskedNumber = account.maskedNumber,
                summary = summary,
                remainingBalance = remainingByAccount[containerId],
                remainingBalanceUpdatedAt = snapshot?.updatedAt,
            )
        }
    }
}
