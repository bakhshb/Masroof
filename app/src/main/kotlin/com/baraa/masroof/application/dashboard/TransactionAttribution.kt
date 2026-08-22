package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord

/**
 * Shared attribution for summaries, lists, and detail screens.
 */
object TransactionAttribution {
    fun matchesOwnedAccount(
        tx: FinancialTransaction,
        bank: Bank,
        maskedNumber: String,
        parsedRecords: List<ParsedEventRecord> = emptyList(),
        rawSmsById: Map<String, RawSms> = emptyMap(),
    ): Boolean {
        val containerId = com.baraa.masroof.domain.ids.FinancialContainerIdFactory
            .accountId(bank, maskedNumber)
            ?: return false
        val last4s = CurrentAccountTransactionScope.ownedAccountLast4sFromMaskedNumbers(
            listOf(maskedNumber),
        )
        val scope = CurrentAccountTransactionScope(
            ownedContainerIds = setOf(containerId),
            ownedAccountLast4s = last4s,
            mode = AccountFlowScopeMode.SingleAccount,
        )
        val parsedById = parsedRecords.associateBy { it.event.id }
        return scope.involvesOwnedSource(tx, parsedById, rawSmsById) ||
            scope.involvesOwnedDestination(tx, parsedById, rawSmsById) ||
            matchesAccountContainer(tx.sourceContainerId, containerId, last4s) ||
            matchesAccountContainer(tx.destinationContainerId, containerId, last4s)
    }

    fun matchesCardLast4(tx: FinancialTransaction, last4: String): Boolean =
        matchesCardContainer(tx.sourceContainerId, last4) ||
            matchesCardContainer(tx.destinationContainerId, last4) ||
            com.baraa.masroof.domain.ids.FinancialContainerIdParser
                .cardLast4FromContainers(tx.sourceContainerId, tx.destinationContainerId) == last4

    private fun matchesCardContainer(containerId: String?, last4: String): Boolean =
        containerId?.endsWith(":$last4") == true

    private fun matchesAccountContainer(
        containerId: String?,
        ownedContainerId: String,
        ownedLast4s: Set<String>,
    ): Boolean {
        if (containerId == null) return false
        if (containerId == ownedContainerId) return true
        if (!containerId.startsWith("account:")) return false
        return containerId.substringAfterLast(':') in ownedLast4s
    }
}
