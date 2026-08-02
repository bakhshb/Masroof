package com.baraa.masroof.rules.rules

import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleInput
import com.baraa.masroof.rules.RulePriority
import com.baraa.masroof.rules.RuleResult
import com.baraa.masroof.rules.TransactionRule
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.FinancialTreatment

/**
 * Catches generic patterns (supermarkets, fuel stations, food delivery,
 * pharmacies, telecom) and assigns the corresponding low-confidence
 * category. Runs after the higher-priority rules.
 */
class GenericCategoryRule(
    private val patternToCategoryName: List<Pair<Regex, String>>,
    private val categoryByName: (String) -> com.baraa.masroof.data.db.Category?,
) : TransactionRule {
    override val name: String = "GenericCategoryRule"
    override val priority: RulePriority = RulePriority.CATEGORY_RULE

    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        val text = (input.parsed.merchant.orEmpty() + " " + input.body.orEmpty()).lowercase()
        if (text.isBlank()) return null
        for ((pattern, name) in patternToCategoryName) {
            if (pattern.containsMatchIn(text)) {
                val category = categoryByName(name) ?: continue
                return RuleResult(
                    financialTreatment = FinancialTreatment.EXPENSE,
                    categoryId = category.id,
                    confidence = 50,
                    reason = "generic category rule: ${category.nameAr}",
                    source = CategorySource.RULE,
                    excludeFromSpending = false,
                )
            }
        }
        return null
    }
}
