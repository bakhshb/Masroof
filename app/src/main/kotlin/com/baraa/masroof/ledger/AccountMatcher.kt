package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.rules.AccountMatching
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.transaction.ParsedIdentifierEvidence

/** Deterministic account matching. Institution identifies a bank, never an account. */
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
        val eligible = accounts.filter { it.isOwnedByUser && it.isActive && it.systemAccountKey == null }
        val strictEvidence = identifierEvidence.filter { it.lastFour.length == 4 && identifierCompatibleType(it.type, transaction) }
        val candidates = if (strictEvidence.isNotEmpty()) strictEvidence.map { it.type to it.lastFour }
        else transaction.accountOrCardLastFourDigits?.let(AccountMatching::normalizeDigits)
            ?.takeIf { it.isNotBlank() }?.let { value -> identifierTypesFor(transaction).map { it to value } }.orEmpty()
        if (candidates.isNotEmpty()) {
            val typed = buildList {
                for ((type, value) in candidates) {
                    addAll(identifierRepository.findAccountsByIdentifier(type, value).filter {
                        identifierCompatible(it.accountType, type, transaction)
                    })
                }
            }.distinctBy { it.id }
            if (typed.size == 1) return matched(typed.single(), AccountLinkSource.LAST_FOUR_MATCH, 100, false, AccountLinkConfidence.CONFIRMED, "typed_identifier_match")
            if (typed.size > 1) return unmatched("ambiguous_typed_identifier")
            // A message supplied a real identifier but it belongs to no owned
            // account. Sender/institution must not override that conflict.
            return unmatched("missing_account_identifier")
        }

        // Existing account sender aliases are legacy persisted data; use the
        // same canonical sender key as the typed identifier repository.
        val senderKey = FinancialInstitutionResolver.senderKey(transaction.originalSender)
        val legacyAliases = eligible.filter { account ->
            account.senderAliases.any { FinancialInstitutionResolver.senderKey(it) == senderKey } && compatibleWithoutIdentifier(account.accountType, transaction)
        }
        val typedAliases = identifierRepository.accountsForSender(transaction.originalSender)
            .filter { compatibleWithoutIdentifier(it.accountType, transaction) }
        val senderMatches = (legacyAliases + typedAliases).distinctBy { it.id }
        if (senderMatches.size == 1) return matched(senderMatches.single(), AccountLinkSource.OWNED_ACCOUNT_RULE, 75, true, AccountLinkConfidence.MEDIUM, "sender_only_compatible")
        if (senderMatches.size > 1) return unmatched("ambiguous_sender")

        val institutions = eligible.filter { institutionMatches(it, transaction) && compatibleWithoutIdentifier(it.accountType, transaction) }
        return when (institutions.size) {
            1 -> matched(institutions.single(), AccountLinkSource.INSTITUTION_MATCH, 55, true, AccountLinkConfidence.MEDIUM, "institution_only")
            0 -> unmatched("missing_account_identifier")
            else -> unmatched("ambiguous_institution")
        }
    }

    private fun identifierTypesFor(transaction: TransactionEntity): List<AccountIdentifierType> = when (transaction.transactionType) {
        TransactionType.CARD_PAYMENT -> listOf(AccountIdentifierType.CREDIT_CARD_LAST4, AccountIdentifierType.DEBIT_CARD_LAST4)
        TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN, TransactionType.INTERNAL_TRANSFER -> listOf(AccountIdentifierType.ACCOUNT_LAST4, AccountIdentifierType.IBAN_LAST4)
        else -> listOf(AccountIdentifierType.ACCOUNT_LAST4, AccountIdentifierType.DEBIT_CARD_LAST4, AccountIdentifierType.CREDIT_CARD_LAST4, AccountIdentifierType.WALLET_LAST4)
    }

    private fun identifierCompatibleType(identifier: AccountIdentifierType, transaction: TransactionEntity): Boolean =
        identifier in identifierTypesFor(transaction) || transaction.transactionType == TransactionType.UNKNOWN

    private fun identifierCompatible(type: AccountType, identifier: AccountIdentifierType, transaction: TransactionEntity): Boolean = when (identifier) {
        AccountIdentifierType.CREDIT_CARD_LAST4 -> type == AccountType.CREDIT_CARD
        AccountIdentifierType.DEBIT_CARD_LAST4 -> type == AccountType.BANK_ACCOUNT
        AccountIdentifierType.ACCOUNT_LAST4, AccountIdentifierType.IBAN_LAST4 -> type in setOf(AccountType.BANK_ACCOUNT, AccountType.INVESTMENT_ACCOUNT, AccountType.SUKUK_ACCOUNT)
        AccountIdentifierType.WALLET_LAST4 -> type in setOf(AccountType.DIGITAL_WALLET, AccountType.WALLET)
        AccountIdentifierType.SENDER_ALIAS -> compatibleWithoutIdentifier(type, transaction)
    }

    private fun compatibleWithoutIdentifier(type: AccountType, transaction: TransactionEntity): Boolean = when (transaction.transactionType) {
        TransactionType.CARD_PAYMENT -> type in setOf(AccountType.CREDIT_CARD, AccountType.BANK_ACCOUNT)
        else -> type !in setOf(AccountType.CASH, AccountType.OTHER_ASSET, AccountType.OTHER_LIABILITY)
    }

    private fun institutionMatches(account: FinancialAccount, transaction: TransactionEntity): Boolean {
        val institution = account.institutionName?.trim()?.lowercase().orEmpty()
        return institution.length >= 3 && listOfNotNull(transaction.originalSender, transaction.merchantOrBeneficiary)
            .joinToString(" ").lowercase().contains(institution)
    }

    private fun matched(account: FinancialAccount, source: AccountLinkSource, confidence: Int, review: Boolean, level: AccountLinkConfidence, code: String) =
        Match(account, source, confidence, review, level, code)
    private fun unmatched(code: String) = Match(null, AccountLinkSource.UNLINKED, 0, true, AccountLinkConfidence.UNMATCHED, code)
}
