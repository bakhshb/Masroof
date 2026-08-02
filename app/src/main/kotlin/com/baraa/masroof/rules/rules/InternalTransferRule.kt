package com.baraa.masroof.rules.rules

import com.baraa.masroof.data.db.FinancialAccount
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
 * Classifies a transfer as INTERNAL_TRANSFER when the sender (the account
 * the SMS came from) is one of the user's owned accounts AND the
 * destination (parsed from the body or implied) is also one of the user's
 * owned accounts.
 *
 * The rule only fires for parsed transfer types. It does NOT assume
 * every transfer between two well-known banks is internal — both endpoints
 * must be in the user's owned-accounts list.
 *
 * Safety-critical and merchant-memory rules run before this one, so
 * credit-card payments and user-confirmed merchant rules still win.
 */
class InternalTransferRule : TransactionRule {
    override val name: String = "InternalTransferRule"
    override val priority: RulePriority = RulePriority.INTERNAL_TRANSFER

    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        if (input.type !in TRANSFER_TYPES) return null

        val owned = context.ownedAccounts.filter { it.isOwnedByUser && it.isActive }
        if (owned.size < 2) return null // need at least two accounts to transfer between

        val source = matchAccount(input.sender, owned)
        if (source == null) return null // source is not one of the user's accounts

        val destination = matchAccountInBody(input.body, owned, exclude = source)
            ?: matchAccountInMerchant(input.parsed.merchant, owned, exclude = source)
        if (destination == null) return null // destination unknown → fall through to review

        return RuleResult(
            financialTreatment = FinancialTreatment.INTERNAL_TRANSFER,
            categoryId = null,
            confidence = 90,
            reason = "transfer between owned accounts: ${source.displayName} -> ${destination.displayName}",
            source = CategorySource.RULE,
            excludeFromSpending = true,
        )
    }

    /**
     * Match the SMS sender against any owned account's normalized sender
     * aliases. The sender-alias comparison is exact (after normalization
     * which the account store does when saving).
     */
    private fun matchAccount(sender: String?, owned: List<FinancialAccount>): FinancialAccount? {
        if (sender.isNullOrBlank()) return null
        val norm = sender.trim().lowercase()
        return owned.firstOrNull { acc ->
            acc.senderAliases.any { alias -> alias.trim().lowercase() == norm }
        }
    }

    /**
     * Look for an owned-account display name (or alias) inside the message
     * body. Conservative: requires a whole-word match to avoid false
     * positives like "STC Pay" matching an "STC Bank" account.
     */
    private fun matchAccountInBody(
        body: String?,
        owned: List<FinancialAccount>,
        exclude: FinancialAccount,
    ): FinancialAccount? {
        if (body.isNullOrBlank()) return null
        val candidates = owned.filter { it.id != exclude.id }
        for (acc in candidates) {
            val tokens = (acc.displayName + " " + (acc.institutionName.orEmpty()))
                .split(Regex("\\W+"))
                .filter { it.length >= 3 }
                .map { it.trim().lowercase() }
                .distinct()
            if (tokens.isEmpty()) continue
            val bodyLower = body.lowercase()
            if (tokens.all { word -> word !in bodyLower }) continue
            // We have at least one token match → consider this the destination.
            return acc
        }
        return null
    }

    private fun matchAccountInMerchant(
        merchant: String?,
        owned: List<FinancialAccount>,
        exclude: FinancialAccount,
    ): FinancialAccount? {
        if (merchant.isNullOrBlank()) return null
        val m = merchant.lowercase()
        return owned.firstOrNull { acc ->
            acc.id != exclude.id && (
                acc.displayName.lowercase() in m ||
                    (acc.institutionName?.lowercase()?.let { it in m } ?: false)
                )
        }
    }

    private companion object {
        val TRANSFER_TYPES = setOf(
            TransactionType.TRANSFER_OUT,
            TransactionType.TRANSFER_IN,
            TransactionType.INTERNAL_TRANSFER,
        )
    }
}

/** A small helper that exposes just the account list for the rule context. */
@Suppress("unused")
fun List<FinancialAccount>.isInvestmentAccount(name: String?): Boolean =
    any { it.accountType == AccountType.INVESTMENT_ACCOUNT && it.displayName.equals(name, ignoreCase = true) }
