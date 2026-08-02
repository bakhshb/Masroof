package com.baraa.masroof.rules.rules

import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleInput
import com.baraa.masroof.rules.RulePriority
import com.baraa.masroof.rules.RuleResult
import com.baraa.masroof.rules.TransactionRule
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.MerchantNormalizer

/**
 * Looks up the parsed merchant in the user's merchant-memory table. If a
 * matching row exists with `confirmationCount > 0`, return the stored
 * category and treatment.
 *
 * Safety-critical rules (declined, refund, card payment, etc.) run BEFORE
 * this rule, so a declined transaction is still IGNORED even if the same
 * merchant was previously categorized.
 */
class MerchantMemoryRule : TransactionRule {
    override val name: String = "MerchantMemoryRule"
    override val priority: RulePriority = RulePriority.MERCHANT_MEMORY

    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        val key = input.normalizedMerchantKey
            ?: MerchantNormalizer.normalize(input.parsed.merchant)
        if (key.isBlank()) return null

        val memory = context.merchantMemories.firstOrNull { it.normalizedKey == key }
            ?: return null
        if (memory.confirmationCount <= 0) return null

        val treatment = memory.preferredFinancialTreatment
            ?: com.baraa.masroof.transaction.FinancialTreatment.EXPENSE
        return RuleResult(
            financialTreatment = treatment,
            categoryId = memory.preferredCategoryId,
            confidence = 100,
            reason = "user previously confirmed this merchant",
            source = CategorySource.MERCHANT_MEMORY,
            excludeFromSpending = treatment in setOf(
                com.baraa.masroof.transaction.FinancialTreatment.IGNORED,
                com.baraa.masroof.transaction.FinancialTreatment.INTERNAL_TRANSFER,
                com.baraa.masroof.transaction.FinancialTreatment.REFUND,
                com.baraa.masroof.transaction.FinancialTreatment.CREDIT_CARD_PAYMENT,
                com.baraa.masroof.transaction.FinancialTreatment.INCOME,
                com.baraa.masroof.transaction.FinancialTreatment.INVESTMENT,
                com.baraa.masroof.transaction.FinancialTreatment.PENDING_REVIEW,
            ),
        )
    }
}
