package com.baraa.masroof.ledger

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
 *  1. Exact typed identifier evidence (SOURCE-role preferred when roles are present)
 *  2. Untyped historical last-four only when no typed evidence exists
 *
 * SenderProfile links narrow exact evidence but never override a type conflict.
 * Institution name alone never selects an account.
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
            // Incoming transfers credit the user's account (DESTINATION). Prefer that
            // over counterparty "خصمت من حساب" SOURCE last-fours from the other bank.
            val primaryEvidence = when {
                transaction.transactionType == com.baraa.masroof.transaction.TransactionType.TRANSFER_IN &&
                    strictEvidence.any { it.role == IdentifierRole.DESTINATION } ->
                    strictEvidence.filter { it.role == IdentifierRole.DESTINATION }
                strictEvidence.any { it.role == IdentifierRole.SOURCE } ->
                    strictEvidence.filter { it.role == IdentifierRole.SOURCE }
                else -> strictEvidence.filter {
                    it.role == IdentifierRole.UNSPECIFIED || it.role == IdentifierRole.SOURCE
                }.ifEmpty { strictEvidence }
            }
            val destinationEvidence = strictEvidence.filter { it.role == IdentifierRole.DESTINATION }

            val primaryExact = resolveTyped(primaryEvidence, eligibleIds, identifierRepository, transaction)
            when {
                primaryExact.size == 1 -> return matched(
                    account = primaryExact.single(),
                    source = AccountLinkSource.LAST_FOUR_MATCH,
                    confidence = 100,
                    review = false,
                    level = AccountLinkConfidence.CONFIRMED,
                    code = "typed_identifier_match",
                    destination = resolveDestination(
                        destinationEvidence,
                        primaryExact.single().id,
                        eligibleIds,
                        identifierRepository,
                        transaction,
                    ),
                )
                primaryExact.size > 1 -> return unmatched("ambiguous_typed_identifier")
            }

            // Typed evidence that does not resolve exactly is a review case.
            // Never reinterpret CREDIT_CARD_LAST4 as ACCOUNT_LAST4 (or vice versa).
            return unmatched("typed_identifier_not_found")
        }

        val fallbackLastFour = transaction.accountOrCardLastFourDigits
            ?.let(AccountMatching::normalizeDigits)
            ?.filter { it.isDigit() }
            ?.takeLast(4)
            ?.takeIf { it.length == 4 }
        if (fallbackLastFour != null) {
            val byAnyType = resolveByLastFours(
                listOf(fallbackLastFour),
                eligibleIds,
                identifierRepository,
                transaction,
            )
            when {
                byAnyType.size == 1 -> return matched(
                    byAnyType.single(),
                    AccountLinkSource.LAST_FOUR_MATCH,
                    100,
                    false,
                    AccountLinkConfidence.CONFIRMED,
                    "last_four_cross_type_match",
                )
                byAnyType.size > 1 -> return unmatched("ambiguous_typed_identifier")
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
        if (senderMatches.size > 1) return unmatched("ambiguous_sender")
        if (senderMatches.size == 1) {
            return matched(
                account = senderMatches.single(),
                source = AccountLinkSource.SENDER_PROFILE,
                confidence = 80,
                review = false,
                level = AccountLinkConfidence.CONFIRMED,
                code = "single_compatible_sender_account",
            )
        }

        return unmatched("missing_account_identifier")
    }

    private suspend fun resolveDestination(
        destinationEvidence: List<ParsedIdentifierEvidence>,
        primaryAccountId: Long,
        eligibleIds: Set<Long>,
        identifierRepository: AccountIdentifierRepository,
        transaction: TransactionEntity,
    ): FinancialAccount? {
        if (destinationEvidence.isEmpty()) return null
        val exact = resolveTyped(destinationEvidence, eligibleIds, identifierRepository, transaction)
            .singleOrNull()
            ?.takeIf { it.id != primaryAccountId }
        if (exact != null) return exact
        return null
    }

    private suspend fun resolveTyped(
        evidence: List<ParsedIdentifierEvidence>,
        eligibleIds: Set<Long>,
        identifierRepository: AccountIdentifierRepository,
        transaction: TransactionEntity,
    ): List<FinancialAccount> {
        val typed = buildList {
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
        if (typed.size <= 1) return typed
        // Prefer accounts associated with this SMS sender (SenderProfile cross-ref).
        // Never pick by insertion order when identifiers conflict across senders.
        val senderLinked = identifierRepository.accountsForSender(transaction.originalSender)
            .map { it.id }
            .toSet()
        if (senderLinked.isEmpty()) return typed
        val narrowed = typed.filter { it.id in senderLinked }
        return narrowed.ifEmpty { typed }
    }

    private suspend fun resolveByLastFours(
        lastFours: List<String>,
        eligibleIds: Set<Long>,
        identifierRepository: AccountIdentifierRepository,
        transaction: TransactionEntity,
    ): List<FinancialAccount> = lastFours
        .flatMap { digits ->
            identifierRepository.findAccountsByLastFourAnyType(digits).filter {
                it.id in eligibleIds &&
                    AccountIdentifierCompatibility.accountCompatibleWithoutIdentifier(
                        it.accountType,
                        transaction.transactionType,
                    )
            }
        }
        .distinctBy { it.id }

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
