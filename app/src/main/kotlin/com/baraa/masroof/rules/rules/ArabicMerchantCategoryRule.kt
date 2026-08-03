package com.baraa.masroof.rules.rules

import com.baraa.masroof.data.db.Category
import com.baraa.masroof.rules.ArabicMerchantRules
import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleInput
import com.baraa.masroof.rules.RulePriority
import com.baraa.masroof.rules.RuleResult
import com.baraa.masroof.rules.TransactionRule
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.FinancialTreatment

/**
 * Classifies Arabic-language bank-SMS messages using the data-driven
 * [ArabicMerchantRules] table. Returns a [RuleResult] with the matched
 * [ArabicMerchantRules.Pattern.ruleName] and the [ArabicMerchantRules.Pattern.confidence]
 * so the UI / review screen can surface why a category was chosen.
 *
 * Vague-only text (e.g. "شركة" or "مؤسسة") does NOT trigger a category.
 * The engine falls through to FALLBACK → PENDING_REVIEW.
 */
class ArabicMerchantCategoryRule(
    private val categoryByName: (String) -> Category?,
) : TransactionRule {

    override val name: String = "ArabicMerchantCategoryRule"
    override val priority: RulePriority = RulePriority.CATEGORY_RULE

    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        val merchant = input.parsed.merchant.orEmpty()
        val body = input.body.orEmpty()
        if (merchant.isBlank() && body.isBlank()) return null

        // Compose the searchable text: merchant first (higher signal),
        // then body. Lowercased for case-insensitive matching.
        val text = (merchant + " " + body).lowercase()

        // Vague-only text: if every non-blank token is in the blacklist,
        // refuse to classify. We use a simple "all words are vague" rule
        // (split on whitespace and punctuation, filter blanks).
        val words = text.split(Regex("""[\s\p{Punct}]+""")).filter { it.isNotBlank() }
        if (words.isNotEmpty() && words.all { it in ArabicMerchantRules.VAGUE_BLACKLIST }) {
            return null
        }

        for (pattern in ArabicMerchantRules.ALL) {
            if (pattern.regex.containsMatchIn(text)) {
                val category = categoryByName(pattern.categoryName) ?: continue
                return RuleResult(
                    financialTreatment = FinancialTreatment.EXPENSE,
                    categoryId = category.id,
                    confidence = pattern.confidence,
                    reason = "${pattern.ruleName}: ${category.nameAr}",
                    source = CategorySource.RULE,
                    excludeFromSpending = false,
                )
            }
        }
        return null
    }
}
