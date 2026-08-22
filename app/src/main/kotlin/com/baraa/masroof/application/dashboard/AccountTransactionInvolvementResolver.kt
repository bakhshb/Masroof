package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord

/**
 * Maps each transaction to owned account container IDs it touches, using the same
 * SMS/container resolution as [CurrentAccountSummaryCalculator] (SingleAccount scope).
 */
object AccountTransactionInvolvementResolver {
    fun buildIndex(
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
        ownedAccounts: List<AccountRegistryEntry>,
    ): Map<String, Set<String>> {
        if (transactions.isEmpty() || ownedAccounts.isEmpty()) return emptyMap()

        val parsedRecordsById = parsedRecords.associateBy { it.event.id }
        val scopesByContainer = CurrentAccountScopeFactory.singleAccountScopes(ownedAccounts)
        if (scopesByContainer.isEmpty()) return emptyMap()

        val involvement = mutableMapOf<String, MutableSet<String>>()
        for ((containerId, scope) in scopesByContainer) {
            for (tx in transactions) {
                if (scope.involvesOwnedAccount(tx, parsedRecordsById, rawSmsById)) {
                    involvement.getOrPut(tx.id) { linkedSetOf() }.add(containerId)
                }
            }
        }
        return transactions.associate { tx ->
            tx.id to involvement[tx.id].orEmpty()
        }
    }
}
