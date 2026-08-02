package com.baraa.masroof.rules.rules

import com.baraa.masroof.data.db.Category
import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleInput
import com.baraa.masroof.rules.RulePriority
import com.baraa.masroof.rules.RuleResult
import com.baraa.masroof.rules.TransactionRule
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.FinancialTreatment

/**
 * Maps well-known merchant tokens to a high-confidence category. Used for
 * merchants the parser reliably identifies (e.g. "STARBUCKS", "CARREFOUR",
 * "JAWWY", "STC"). The mapping is configurable via the seed list — the
 * actual rule resolution looks up the category by name.
 *
 * Safety / safety-critical / internal-transfer / merchant-memory rules run
 * before this rule, so any of those still win.
 */
class HighConfidenceMerchantRule(
    private val tokenToCategory: Map<String, String>,
    private val categoryByName: (String) -> Category?,
) : TransactionRule {
    override val name: String = "HighConfidenceMerchantRule"
    override val priority: RulePriority = RulePriority.MERCHANT_RULE

    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        val merchant = input.parsed.merchant?.lowercase() ?: return null
        val category = tokenToCategory.entries.firstOrNull { (token, _) ->
            merchant.contains(token)
        }?.value?.let(categoryByName) ?: return null
        return RuleResult(
            financialTreatment = FinancialTreatment.EXPENSE,
            categoryId = category.id,
            confidence = 80,
            reason = "high-confidence merchant rule: ${category.nameAr}",
            source = CategorySource.RULE,
            excludeFromSpending = false,
        )
    }
}
