package com.baraa.masroof.rules.rules

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleInput
import com.baraa.masroof.rules.RulePriority
import com.baraa.masroof.rules.RuleResult
import com.baraa.masroof.rules.TransactionRule
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.FinancialTreatment

/**
 * Detects wallet top-up messages. Requires exactly one owned wallet and
 * exactly one owned credit card that are both evidenced in the message
 * (typed identifiers / names). Never silently picks the first of many.
 */
class WalletTopUpRule : TransactionRule {
    override val name: String = "WalletTopUpRule"
    override val priority: RulePriority = RulePriority.INTERNAL_TRANSFER

    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        if (!looksLikeWalletTopUp(input.body, input.parsed.merchant)) return null

        val owned = context.ownedAccounts.filter { it.isOwnedByUser && it.isActive }
        if (owned.size < 2) return null

        val wallets = owned.filter { it.accountType == AccountType.WALLET || it.accountType == AccountType.DIGITAL_WALLET }
        val cards = owned.filter { it.accountType == AccountType.CREDIT_CARD }
        val wallet = wallets.singleOrNull() ?: return null
        val card = cards.singleOrNull() ?: return null

        val bodyText = (input.body.orEmpty() + " " + input.parsed.merchant.orEmpty()).lowercase()
        // Unique wallet + top-up phrasing already identifies the destination.
        // The funding card must still appear in the message (name, typed id, or evidence).
        val mentionsCard = card.displayName.lowercase() in bodyText ||
            typedMentions(context, card, input) ||
            input.parsed.identifierEvidence.any { it.type == AccountIdentifierType.CREDIT_CARD_LAST4 }

        if (!mentionsCard) return null

        return RuleResult(
            financialTreatment = FinancialTreatment.INTERNAL_TRANSFER,
            categoryId = null,
            confidence = 85,
            reason = "wallet top-up between owned accounts: ${card.displayName} -> ${wallet.displayName}",
            source = CategorySource.RULE,
            excludeFromSpending = true,
        )
    }

    private fun typedMentions(context: RuleContext, account: FinancialAccount, input: RuleInput): Boolean {
        val ids = context.accountIdentifiers.filter { it.accountId == account.id }
        val body = (input.body.orEmpty() + " " + input.parsed.merchant.orEmpty()).lowercase()
        if (ids.any { it.identifierType == AccountIdentifierType.SENDER_ALIAS && it.normalizedValue in body }) {
            return true
        }
        val lastFours = ids.map { it.normalizedValue }.filter { it.length == 4 }
        return lastFours.any { it in body } ||
            input.parsed.identifierEvidence.any { evidence ->
                evidence.lastFour in lastFours && evidence.type == ids.firstOrNull {
                    it.normalizedValue == evidence.lastFour
                }?.identifierType
            }
    }

    private fun looksLikeWalletTopUp(body: String?, merchant: String?): Boolean {
        val text = (body.orEmpty() + " " + merchant.orEmpty()).lowercase()
        if (text.isBlank()) return false
        return WALLET_TOPUP_PATTERNS.any { it.containsMatchIn(text) }
    }

    private companion object {
        val WALLET_TOPUP_PATTERNS: List<Regex> = listOf(
            Regex("""\b(شحن\s*المحفظة|إضافة\s*رصيد\s*للمحفظة|تمويل\s*المحفظة|شحن\s*الحساب\s*بالبطاقة|شحن\s*المحفظة\s*من\s*البطاقة|wallet\s*top[-\s]*up|top[-\s]*up\s*wallet|card\s*funding|add\s*money|cash\s*in|شحن\s*رصيد|تمويل\s*البطاقة\s*للمحفظة)\b"""),
        )
    }
}
