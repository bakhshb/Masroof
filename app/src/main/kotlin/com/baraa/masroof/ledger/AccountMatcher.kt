package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.rules.AccountMatching
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionType

/** Uses the AccountIdentifier system as the source of truth for typed identifier linking. */
object AccountMatcher {
    data class Match(
        val account: FinancialAccount?,
        val source: AccountLinkSource,
        val confidence: Int,
        val needsReview: Boolean,
        val level: AccountLinkConfidence,
        val diagnosticCode: String,
        /**
         * When the matched transaction is an internal transfer between two
         * of the user's owned accounts, this is the destination side. The
         * journal generator + the atomic importer use it to record the
         * transfer without invalidating account-link priority rules.
         */
        val destinationAccountCandidate: FinancialAccount? = null,
    )

    suspend fun match(
        transaction: TransactionEntity,
        accounts: List<FinancialAccount>,
        identifierRepository: AccountIdentifierRepository,
    ): Match {
        val eligible = accounts.filter { it.isOwnedByUser && it.isActive && it.systemAccountKey == null }
        val lastFour = transaction.accountOrCardLastFourDigits?.let(AccountMatching::normalizeDigits)
        val candidatesByIdentifier = if (!lastFour.isNullOrBlank()) {
            identifierRepository.findAccountsByIdentifier(
                AccountIdentifierType.CREDIT_CARD_LAST4,
                lastFour,
            ).filter { compatible(it.accountType, transaction) } +
                identifierRepository.findAccountsByIdentifier(
                    AccountIdentifierType.ACCOUNT_LAST4,
                    lastFour,
                ).filter { compatible(it.accountType, transaction) } +
                identifierRepository.findAccountsByIdentifier(
                    AccountIdentifierType.DEBIT_CARD_LAST4,
                    lastFour,
                ).filter { compatible(it.accountType, transaction) }
        } else emptyList()
        val unique = candidatesByIdentifier.distinctBy { it.id }
        if (unique.size > 1) return unmatched("ambiguous_typed_identifier")
        if (unique.size == 1) {
            val account = unique.single()
            return Match(
                account = account,
                source = AccountLinkSource.LAST_FOUR_MATCH,
                confidence = 100,
                needsReview = false,
                level = AccountLinkConfidence.CONFIRMED,
                diagnosticCode = "typed_identifier_match",
            )
        }
        // Sender-only fallback: institution match but never cross between bank account and credit card.
        val senderMatches = identifierRepository.accountsForSender(transaction.originalSender)
        val compatibleSenderMatches = senderMatches.filter { compatible(it.accountType, transaction) }
        if (compatibleSenderMatches.size == 1) return Match(
            account = compatibleSenderMatches.single(),
            source = AccountLinkSource.OWNED_ACCOUNT_RULE,
            confidence = 75,
            needsReview = true,
            level = AccountLinkConfidence.MEDIUM,
            diagnosticCode = "sender_only_compatible",
        )
        if (compatibleSenderMatches.size > 1) return unmatched("ambiguous_sender")
        val institutions = eligible.filter { institutionMatches(it, transaction) && compatible(it.accountType, transaction) }
        if (institutions.size == 1) return Match(
            account = institutions.single(),
            source = AccountLinkSource.INSTITUTION_MATCH,
            confidence = 55,
            needsReview = true,
            level = AccountLinkConfidence.MEDIUM,
            diagnosticCode = "institution_only",
        )
        return unmatched(if (institutions.size > 1) "ambiguous_institution" else "unmatched")
    }

    private fun unmatched(code: String) = Match(null, AccountLinkSource.UNLINKED, 0, true, AccountLinkConfidence.UNMATCHED, code)

    private fun compatible(type: AccountType, transaction: TransactionEntity): Boolean = when (transaction.financialTreatment) {
        FinancialTreatment.CREDIT_CARD_PAYMENT -> type == AccountType.CREDIT_CARD || type in setOf(AccountType.BANK_ACCOUNT, AccountType.DIGITAL_WALLET, AccountType.WALLET)
        FinancialTreatment.INVESTMENT -> type == AccountType.INVESTMENT_ACCOUNT || type == AccountType.SUKUK_ACCOUNT || type == AccountType.BANK_ACCOUNT
        FinancialTreatment.CASH_WITHDRAWAL -> type == AccountType.BANK_ACCOUNT || type == AccountType.CASH
        else -> transaction.transactionType != TransactionType.CARD_PAYMENT || type == AccountType.CREDIT_CARD
    }

    private fun institutionMatches(account: FinancialAccount, transaction: TransactionEntity): Boolean {
        val haystack = listOfNotNull(transaction.originalSender, transaction.merchantOrBeneficiary)
            .joinToString(" ").lowercase()
        val institution = account.institutionName?.trim()?.lowercase().orEmpty()
        return institution.length >= 3 && haystack.contains(institution)
    }
}
