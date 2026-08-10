package com.baraa.masroof.bank.aljazira.extraction

import com.baraa.masroof.parsing.model.NormalizedSms

/**
 * Commercial purchase merchant only — not counterparty or biller.
 */
class MerchantExtractor {
    fun extract(sms: NormalizedSms): String? {
        val normalized = sms.normalizedBody
        val comparison = sms.comparisonBody
        for ((pattern, group) in PATTERNS) {
            val match = pattern.find(comparison) ?: continue
            val start = match.groups[group]?.range?.first ?: continue
            val end = match.groups[group]?.range?.last ?: continue
            return normalized.substring(start, end + 1).trim().trimStart(':').trim()
                .takeIf { it.isNotBlank() && !it.all { ch -> ch.isDigit() } }
        }
        return null
    }

    companion object {
        private val VALUE = """([^\n]+?)(?=\s*(?:\n|$|بمبلغ|مبلغ|amount|of\s*:|on\s*:|date\s*:|available|due|الرصيد|إجمالي|في\s*:|خصمت))"""

        private val PATTERNS: List<Pair<Regex, Int>> = listOf(
            Regex("""لدى\s*:\s*$VALUE""", RegexOption.IGNORE_CASE) to 1,
            Regex("""(?<![\p{L}])at\s*:\s*$VALUE""", RegexOption.IGNORE_CASE) to 1,
            Regex("""(?<![\p{L}])at\s+$VALUE""", RegexOption.IGNORE_CASE) to 1,
        )
    }
}
