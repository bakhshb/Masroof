package com.baraa.masroof.bank.aljazira.extraction

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.parsing.model.NormalizedSms

/**
 * Fixture-proven card last4 labels only.
 */
class CardExtractor {
    fun extract(sms: NormalizedSms, bank: Bank): CardReference? =
        extractFromText(sms.comparisonBody, bank)

    companion object {
        val PATTERNS = listOf(
            Regex("""بطاقة\s*ائتمانية\s*:\s*(\d{4})"""),
            Regex("""بطاقة\s*إئتمانية\s*:\s*(\d{4})"""),
            Regex("""بطاقة\s*مدى\s*:\s*(\d{4})"""),
            // Internal ATM withdrawal: "بطاقة 8219:مدى"
            Regex("""بطاقة\s*(\d{4})\s*:\s*مدى"""),
            Regex("""رقم\s*:\s*(\d{4})"""),
            Regex("""(?<![\p{L}])number\s*:\s*(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""بطاقة\s*:\s*(\d{4})"""),
            Regex("""credit\s*card\s*:\s*(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""(?<![\p{L}])card\s*:\s*(\d{4})""", RegexOption.IGNORE_CASE),
        )

        fun extractFromText(text: String, bank: Bank): CardReference? {
            for (pattern in PATTERNS) {
                val match = pattern.find(text) ?: continue
                val last4 = match.groupValues[1]
                return CardReference(bank = bank, last4 = last4)
            }
            return null
        }
    }
}
