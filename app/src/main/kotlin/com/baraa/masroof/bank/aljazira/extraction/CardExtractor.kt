package com.baraa.masroof.bank.aljazira.extraction

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.parsing.model.NormalizedSms

/**
 * Fixture-proven card last4 labels only.
 */
class CardExtractor {
    fun extract(sms: NormalizedSms, bank: Bank): CardReference? {
        val text = sms.comparisonBody
        for (pattern in PATTERNS) {
            val match = pattern.find(text) ?: continue
            val last4 = match.groupValues[1]
            return CardReference(bank = bank, last4 = last4)
        }
        return null
    }

    companion object {
        private val PATTERNS = listOf(
            Regex("""بطاقة\s*ائتمانية\s*:\s*(\d{4})"""),
            Regex("""بطاقة\s*إئتمانية\s*:\s*(\d{4})"""),
            Regex("""بطاقة\s*مدى\s*:\s*(\d{4})"""),
            Regex("""رقم\s*:\s*(\d{4})"""),
            Regex("""(?<![\p{L}])number\s*:\s*(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""بطاقة\s*:\s*(\d{4})"""),
            Regex("""credit\s*card\s*:\s*(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""(?<![\p{L}])card\s*:\s*(\d{4})""", RegexOption.IGNORE_CASE),
        )
    }
}
