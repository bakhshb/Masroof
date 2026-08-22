package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord

data class OwnedAccountPeriodSummary(
    val bank: com.baraa.masroof.domain.model.Bank,
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
    ): List<OwnedAccountPeriodSummary> =
        CurrentAccountScopeFactory.singleAccountScopes(ownedAccounts).map { entry ->
            val summary = CurrentAccountSummaryCalculator.summarize(
                transactions = transactions,
                parsedRecords = parsedRecords,
                primaryCurrency = primaryCurrency,
                sarEquivalents = sarEquivalents,
                ownedAccountContainerIds = setOf(entry.containerId),
                ownedAccountLast4s = entry.scope.ownedAccountLast4s,
                rawSmsById = rawSmsById,
                scopeMode = AccountFlowScopeMode.SingleAccount,
            )
            OwnedAccountPeriodSummary(
                bank = entry.bank,
                maskedNumber = entry.maskedNumber,
                summary = summary,
            )
        }
}
