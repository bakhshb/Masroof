package com.baraa.masroof.rules.rules

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.rules.AccountMatching
import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleInput
import com.baraa.masroof.rules.RulePriority
import com.baraa.masroof.rules.RuleResult
import com.baraa.masroof.rules.TransactionRule
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionType

/**
 * Classifies a transfer as INTERNAL_TRANSFER when the SMS can be
 * confidently matched to two of the user's owned accounts (one as source,
 * one as destination). Uses [AccountMatching] for sender alias, last-four
 * and name matching. If only one side is confidently matched — or the
 * match is ambiguous — the rule returns null and the engine falls through
 * to PENDING_REVIEW.
 */
class InternalTransferRule : TransactionRule {
    override val name: String = "InternalTransferRule"
    override val priority: RulePriority = RulePriority.INTERNAL_TRANSFER

    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        if (input.type !in TRANSFER_TYPES) return null

        val owned = context.ownedAccounts.filter { it.isOwnedByUser && it.isActive }
        if (owned.size < 2) return null

        val source = AccountMatching.match(input.sender, input.body, input.parsed.merchant, owned)
        if (source == null) return null

        val destination = matchDestination(owned, source, input)
        if (destination == null) return null

        return RuleResult(
            financialTreatment = FinancialTreatment.INTERNAL_TRANSFER,
            categoryId = null,
            confidence = 90,
            reason = "transfer between owned accounts: ${source.displayName} -> ${destination.displayName}",
            source = CategorySource.RULE,
            excludeFromSpending = true,
        )
    }

    private fun matchDestination(
        owned: List<FinancialAccount>,
        source: FinancialAccount,
        input: RuleInput,
    ): FinancialAccount? {
        val remaining = owned.filter { it.id != source.id }
        // Typed identifiers (when provided by the parser) are the only
        // evidence used for selecting the destination account.
        val evidence = input.parsed.identifierEvidence.firstOrNull { it.role == com.baraa.masroof.transaction.IdentifierRole.DESTINATION || it.role == com.baraa.masroof.transaction.IdentifierRole.UNSPECIFIED }
        if (evidence != null) {
            val typed = remaining.firstOrNull { it.institutionName != null && it.id != source.id && compatible(it.accountType, evidence.type) }
            if (typed != null) return typed
        }
        // Fall back to name / institution match.
        val byName = AccountMatching.matchByName(input.body, input.parsed.merchant, remaining)
        if (byName != null) return byName
        return null
    }

    private fun compatible(type: com.baraa.masroof.transaction.AccountType, idType: com.baraa.masroof.data.db.AccountIdentifierType): Boolean = when (idType) {
        com.baraa.masroof.data.db.AccountIdentifierType.ACCOUNT_LAST4, com.baraa.masroof.data.db.AccountIdentifierType.IBAN_LAST4 -> type in setOf(com.baraa.masroof.transaction.AccountType.BANK_ACCOUNT, com.baraa.masroof.transaction.AccountType.INVESTMENT_ACCOUNT, com.baraa.masroof.transaction.AccountType.SUKUK_ACCOUNT)
        com.baraa.masroof.data.db.AccountIdentifierType.CREDIT_CARD_LAST4 -> type == com.baraa.masroof.transaction.AccountType.CREDIT_CARD
        com.baraa.masroof.data.db.AccountIdentifierType.DEBIT_CARD_LAST4 -> type == com.baraa.masroof.transaction.AccountType.BANK_ACCOUNT
        com.baraa.masroof.data.db.AccountIdentifierType.WALLET_LAST4 -> type in setOf(com.baraa.masroof.transaction.AccountType.DIGITAL_WALLET, com.baraa.masroof.transaction.AccountType.WALLET)
        com.baraa.masroof.data.db.AccountIdentifierType.SENDER_ALIAS -> false
    }

    private companion object {
        val TRANSFER_TYPES = setOf(
            TransactionType.TRANSFER_OUT,
            TransactionType.TRANSFER_IN,
            TransactionType.INTERNAL_TRANSFER,
        )
    }
}
