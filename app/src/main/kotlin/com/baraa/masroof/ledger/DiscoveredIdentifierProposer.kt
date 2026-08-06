package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.ParsedIdentifierEvidence

/**
 * Builds an optional [IdentifierCandidate] the user may explicitly save
 * after manually linking a transaction. Never persists on its own.
 */
object DiscoveredIdentifierProposer {

    fun propose(
        transaction: TransactionEntity,
        selectedAccount: FinancialAccount,
        evidence: List<ParsedIdentifierEvidence> = emptyList(),
    ): IdentifierCandidate? {
        val typed = evidence.firstOrNull {
            it.lastFour.length == 4 &&
                AccountIdentifierCompatibility.isCompatibleTyped(selectedAccount.accountType, it.type)
        }
        if (typed != null) {
            return IdentifierCandidate(
                identifierType = typed.type,
                normalizedLastFour = typed.lastFour,
                transactionRole = when (typed.role) {
                    com.baraa.masroof.transaction.IdentifierRole.SOURCE -> IdentifierTransactionRole.SOURCE
                    com.baraa.masroof.transaction.IdentifierRole.DESTINATION -> IdentifierTransactionRole.DESTINATION
                    com.baraa.masroof.transaction.IdentifierRole.UNSPECIFIED -> IdentifierTransactionRole.UNKNOWN
                },
                sourceField = typed.extractionRule,
                confidence = typed.confidence,
            )
        }
        val lastFour = transaction.accountOrCardLastFourDigits
            ?.filter { it.isDigit() }
            ?.takeLast(4)
            ?.takeIf { it.length == 4 }
            ?: return null
        val type = AccountIdentifierCompatibility.defaultIdentifierTypeFor(selectedAccount.accountType)
            ?: return null
        return IdentifierCandidate(
            identifierType = type,
            normalizedLastFour = lastFour,
            transactionRole = IdentifierTransactionRole.UNKNOWN,
            sourceField = "transaction.accountOrCardLastFourDigits",
            confidence = 70,
        )
    }
}
