package com.baraa.masroof.bank.aljazira.extraction

import com.baraa.masroof.parsing.model.AmountCandidate
import com.baraa.masroof.parsing.model.AmountSourceKind
import com.baraa.masroof.parsing.model.NormalizedSms

/**
 * Extracts labeled transaction amounts only (never first/largest number).
 *
 * Fixture-proven labels: بمبلغ, مبلغ العملية, مبلغ, القسط, Amount, of
 */
class AmountExtractor {
    fun extract(sms: NormalizedSms): List<AmountCandidate> {
        val text = sms.comparisonBody
        val candidates = mutableListOf<AmountCandidate>()

        for ((label, pattern) in LABEL_PATTERNS) {
            pattern.findAll(text).forEach { match ->
                val money = MoneyTokens.parseMoneyFromMatch(match) ?: return@forEach
                candidates += AmountCandidate(
                    value = money,
                    evidenceLabel = label,
                    sourceKind = AmountSourceKind.TRANSACTION_AMOUNT,
                    confidence = 1.0,
                )
            }
        }
        return candidates.distinctBy { it.value.amount to it.evidenceLabel }
    }

    companion object {
        private val LABEL_PATTERNS: List<Pair<String, Regex>> = listOf(
            "بمبلغ" to Regex("""بمبلغ""" + MoneyTokens.moneyAfterLabel.pattern, RegexOption.IGNORE_CASE),
            "مبلغ العملية" to Regex("""مبلغ\s*العملية""" + MoneyTokens.moneyAfterLabel.pattern, RegexOption.IGNORE_CASE),
            "القسط" to Regex("""القسط""" + MoneyTokens.moneyAfterLabel.pattern, RegexOption.IGNORE_CASE),
            // "مبلغ" after more-specific labels; avoid matching داخل "مبلغ العملية" / balances via word boundary-ish
            "مبلغ" to Regex("""(?<![\p{L}])مبلغ(?!\s*العملية)(?!\s*المتبقي)""" + MoneyTokens.moneyAfterLabel.pattern, RegexOption.IGNORE_CASE),
            // Line-anchored "Amount:" only — never "Due Amount" / "Available Balance".
            "amount" to Regex(
                """(?:^|\n)\s*amount""" + MoneyTokens.moneyAfterLabel.pattern,
                setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
            ),
            "of" to Regex("""(?<![\p{L}])of""" + MoneyTokens.moneyAfterLabel.pattern, RegexOption.IGNORE_CASE),
        )
    }
}
