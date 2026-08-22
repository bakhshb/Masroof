package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord

data class OwnedAccountPeriodSummary(
    val bank: Bank,
    val maskedNumber: String,
    val summary: CurrentAccountSummary,
)

object OwnedAccountPeriodSummaryCalculator {
    fun summarize(
        ownedAccounts: List<com.baraa.masroof.domain.model.AccountRegistryEntry>,
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        rawSmsById: Map<String, RawSms>,
    ): List<OwnedAccountPeriodSummary> {
        val scopesByContainer = CurrentAccountScopeFactory.singleAccountScopes(ownedAccounts)
        return ownedAccounts.mapNotNull { account ->
            val containerId = FinancialContainerIdFactory.accountId(account.bank, account.maskedNumber)
                ?: return@mapNotNull null
            val scope = scopesByContainer[containerId] ?: return@mapNotNull null
            val last4s = scope.ownedAccountLast4s
            val summary = CurrentAccountSummaryCalculator.summarize(
                transactions = transactions,
                parsedRecords = parsedRecords,
                primaryCurrency = primaryCurrency,
                sarEquivalents = sarEquivalents,
                ownedAccountContainerIds = setOf(containerId),
                ownedAccountLast4s = last4s,
                rawSmsById = rawSmsById,
                scopeMode = AccountFlowScopeMode.SingleAccount,
            )
            OwnedAccountPeriodSummary(
                bank = account.bank,
                maskedNumber = account.maskedNumber,
                summary = summary,
            )
        }
    }
}
