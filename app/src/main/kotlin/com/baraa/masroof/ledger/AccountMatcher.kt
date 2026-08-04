package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.rules.AccountMatching

/** Conservative, parser/UI-independent matching. No diagnostic contains identifiers. */
object AccountMatcher {
    data class Match(
        val account: FinancialAccount?,
        val source: AccountLinkSource,
        val confidence: Int,
        val needsReview: Boolean,
        val level: AccountLinkConfidence,
        val diagnosticCode: String,
    )

    fun match(transaction: TransactionEntity, accounts: List<FinancialAccount>): Match {
        val eligible = accounts.filter { it.isOwnedByUser && it.isActive && it.systemAccountKey == null }
        val lastFour = transaction.accountOrCardLastFourDigits?.let(AccountMatching::normalizeDigits)
        val aliasMatches = eligible.filter { AccountMatching.matchBySender(transaction.originalSender, listOf(it)) != null }
        if (!lastFour.isNullOrBlank()) {
            val candidates = eligible.filter { it.lastFourDigits == lastFour }
            val compatible = candidates.filter { compatible(it.accountType, transaction) }
            if (compatible.size > 1) return unmatched("ambiguous_last_four")
            if (compatible.size == 1) {
                val account = compatible.single()
                if (account in aliasMatches) return Match(account, AccountLinkSource.LAST_FOUR_MATCH, 100, false, AccountLinkConfidence.CONFIRMED, "sender_last_four_type")
                if (institutionMatches(account, transaction)) return Match(account, AccountLinkSource.LAST_FOUR_MATCH, 90, false, AccountLinkConfidence.HIGH, "last_four_institution_type")
                return Match(account, AccountLinkSource.LAST_FOUR_MATCH, 65, true, AccountLinkConfidence.MEDIUM, "last_four_only")
            }
            if (candidates.isNotEmpty()) return unmatched("incompatible_last_four")
        }
        val compatibleAliases = aliasMatches.filter { compatible(it.accountType, transaction) }
        if (compatibleAliases.size == 1) return Match(compatibleAliases.single(), AccountLinkSource.OWNED_ACCOUNT_RULE, 85, false, AccountLinkConfidence.HIGH, "sender_single_compatible")
        if (compatibleAliases.size > 1) return unmatched("ambiguous_sender")
        val institutions = eligible.filter { institutionMatches(it, transaction) && compatible(it.accountType, transaction) }
        if (institutions.size == 1) return Match(institutions.single(), AccountLinkSource.INSTITUTION_MATCH, 60, true, AccountLinkConfidence.MEDIUM, "institution_type")
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
