package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.rules.AccountMatching
import com.baraa.masroof.transaction.IdentifierRole
import com.baraa.masroof.transaction.ParsedIdentifierEvidence

/**
 * Deterministic account matching.
 *
 * Priority:
 *  1. Typed identifier evidence (SOURCE-role preferred when roles are present)
 *  2. Untyped TX last-four mapped by transaction type
 *  3. Unambiguous active [SENDER_ALIAS] (needs review; never auto-confirmed)
 *
 * Institution name alone never selects an account. Matching uses typed
 * [AccountIdentifierEntity] rows only.
 */
object AccountMatcher {
    data class Match(
        val account: FinancialAccount?,
        val source: AccountLinkSource,
        val confidence: Int,
        val needsReview: Boolean,
        val level: AccountLinkConfidence,
        val diagnosticCode: String,
        val destinationAccountCandidate: FinancialAccount? = null,
    )

    suspend fun match(
        transaction: TransactionEntity,
        accounts: List<FinancialAccount>,
        identifierRepository: AccountIdentifierRepository,
        identifierEvidence: List<ParsedIdentifierEvidence> = emptyList(),
    ): Match {
        val eligibleIds = accounts
            .filter { it.isOwnedByUser && it.isActive && it.systemAccountKey == null }
            .map { it.id }
            .toSet()
        val strictEvidence = identifierEvidence.filter {
            it.lastFour.length == 4 &&
                AccountIdentifierCompatibility.identifierTypeFitsTransaction(it.type, transaction.transactionType)
        }

        if (strictEvidence.isNotEmpty()) {
            val primaryEvidence = when {
                strictEvidence.any { it.role == IdentifierRole.SOURCE } ->
                    strictEvidence.filter { it.role == IdentifierRole.SOURCE }
                else -> strictEvidence.filter {
                    it.role == IdentifierRole.UNSPECIFIED || it.role == IdentifierRole.SOURCE
                }.ifEmpty { strictEvidence }
            }
            val primary = resolveTyped(primaryEvidence, eligibleIds, identifierRepository, transaction)
            val destinationEvidence = strictEvidence.filter { it.role == IdentifierRole.DESTINATION }
            val destination = if (destinationEvidence.isEmpty()) {
                null
            } else {
                resolveTyped(destinationEvidence, eligibleIds, identifierRepository, transaction)
                    .singleOrNull()
                    ?.takeIf { it.id != primary.singleOrNull()?.id }
            }
            return when {
                primary.size == 1 -> matched(
                    account = primary.single(),
                    source = AccountLinkSource.LAST_FOUR_MATCH,
                    confidence = 100,
                    review = false,
                    level = AccountLinkConfidence.CONFIRMED,
                    code = "typed_identifier_match",
                    destination = destination,
                )
                primary.size > 1 -> unmatched("ambiguous_typed_identifier")
                else -> unmatched("missing_account_identifier")
            }
        }

        val fallbackLastFour = transaction.accountOrCardLastFourDigits
            ?.let(AccountMatching::normalizeDigits)
            ?.filter { it.isDigit() }
            ?.takeLast(4)
            ?.takeIf { it.length == 4 }
        if (fallbackLastFour != null) {
            val inferred = AccountIdentifierCompatibility.identifierTypesFor(transaction.transactionType)
                .map {
                    ParsedIdentifierEvidence(
                        type = it,
                        lastFour = fallbackLastFour,
                        role = IdentifierRole.UNSPECIFIED,
                        confidence = 70,
                        extractionRule = "transaction.accountOrCardLastFourDigits",
                    )
                }
            val typed = resolveTyped(inferred, eligibleIds, identifierRepository, transaction)
            when {
                typed.size == 1 -> return matched(
                    typed.single(),
                    AccountLinkSource.LAST_FOUR_MATCH,
                    100,
                    false,
                    AccountLinkConfidence.CONFIRMED,
                    "typed_identifier_match",
                )
                typed.size > 1 -> return unmatched("ambiguous_typed_identifier")
                else -> return unmatched("missing_account_identifier")
            }
        }

        val senderMatches = identifierRepository.accountsForSender(transaction.originalSender)
            .filter {
                it.id in eligibleIds &&
                    AccountIdentifierCompatibility.accountCompatibleWithoutIdentifier(
                        it.accountType,
                        transaction.transactionType,
                    )
            }
            .distinctBy { it.id }
        if (senderMatches.size == 1) {
            return matched(
                senderMatches.single(),
                AccountLinkSource.OWNED_ACCOUNT_RULE,
                75,
                true,
                AccountLinkConfidence.MEDIUM,
                "sender_only_compatible",
            )
        }
        if (senderMatches.size > 1) return unmatched("ambiguous_sender")

        return unmatched("missing_account_identifier")
    }

    private suspend fun resolveTyped(
        evidence: List<ParsedIdentifierEvidence>,
        eligibleIds: Set<Long>,
        identifierRepository: AccountIdentifierRepository,
        transaction: TransactionEntity,
    ): List<FinancialAccount> = buildList {
        for (item in evidence) {
            addAll(
                identifierRepository.findAccountsByIdentifier(item.type, item.lastFour).filter {
                    it.id in eligibleIds &&
                        AccountIdentifierCompatibility.isCompatibleTyped(it.accountType, item.type) &&
                        AccountIdentifierCompatibility.accountCompatibleWithoutIdentifier(
                            it.accountType,
                            transaction.transactionType,
                        )
                },
            )
        }
    }.distinctBy { it.id }

    private fun matched(
        account: FinancialAccount,
        source: AccountLinkSource,
        confidence: Int,
        review: Boolean,
        level: AccountLinkConfidence,
        code: String,
        destination: FinancialAccount? = null,
    ) = Match(account, source, confidence, review, level, code, destination)

    private fun unmatched(code: String) =
        Match(null, AccountLinkSource.UNLINKED, 0, true, AccountLinkConfidence.UNMATCHED, code)
}
