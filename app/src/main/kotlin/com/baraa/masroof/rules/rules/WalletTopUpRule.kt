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

/**
 * Detects "wallet top-up" messages — funding a digital wallet from a
 * credit card. Classifies as INTERNAL_TRANSFER only when BOTH the wallet
 * and the source card correspond to owned accounts. Otherwise the rule
 * returns null and the engine falls through to PENDING_REVIEW.
 *
 * This rule does NOT create a second expense for the funding side — the
 * paired credit-card transaction is the actual spend (handled by
 * CardPaymentRule) and the wallet credit is a movement between the user's
 * own accounts.
 */
class WalletTopUpRule : TransactionRule {
    override val name: String = "WalletTopUpRule"
    override val priority: RulePriority = RulePriority.INTERNAL_TRANSFER

    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        if (!looksLikeWalletTopUp(input.body, input.parsed.merchant)) return null

        val owned = context.ownedAccounts.filter { it.isOwnedByUser && it.isActive }
        if (owned.size < 2) return null

        // Identify the wallet (the destination of the top-up).
        val wallet = owned.firstOrNull { it.accountType == AccountType.WALLET }
            ?: return null
        // Identify the source — must be a CREDIT_CARD owned by the user.
        val card = owned.firstOrNull {
            it.id != wallet.id && it.accountType == AccountType.CREDIT_CARD &&
                (it.displayName.isNotBlank() || it.institutionName != null)
        } ?: return null

        // The body should mention BOTH the wallet name (or a wallet alias)
        // and the card (sender alias or last four).
        val mentionsWallet = wallet.displayName.lowercase() in input.body.orEmpty().lowercase() ||
            wallet.institutionName?.lowercase()?.let { it in input.body.orEmpty().lowercase() } == true
        val mentionsCard = AccountMatching.matchByLastFour(input.body, null, listOf(card)) != null ||
            card.senderAliases.any { it.lowercase() in input.body.orEmpty().lowercase() }

        if (!mentionsWallet || !mentionsCard) return null

        return RuleResult(
            financialTreatment = FinancialTreatment.INTERNAL_TRANSFER,
            categoryId = null,
            confidence = 85,
            reason = "wallet top-up between owned accounts: ${card.displayName} -> ${wallet.displayName}",
            source = CategorySource.RULE,
            excludeFromSpending = true,
        )
    }

    private fun looksLikeWalletTopUp(body: String?, merchant: String?): Boolean {
        val text = (body.orEmpty() + " " + merchant.orEmpty()).lowercase()
        if (text.isBlank()) return false
        return WALLET_TOPUP_PATTERNS.any { it.containsMatchIn(text) }
    }

    private companion object {
        // Arabic + English phrases that indicate a wallet top-up / funding.
        val WALLET_TOPUP_PATTERNS: List<Regex> = listOf(
            Regex("""\b(شحن\s*المحفظة|إضافة\s*رصيد\s*للمحفظة|تمويل\s*المحفظة|شحن\s*الحساب\s*بالبطاقة|شحن\s*المحفظة\s*من\s*البطاقة|wallet\s*top[-\s]*up|top[-\s]*up\s*wallet|card\s*funding|add\s*money|cash\s*in|شحن\s*رصيد|تمويل\s*البطاقة\s*للمحفظة)\b"""),
        )
    }
}
