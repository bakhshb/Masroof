package com.baraa.masroof.bank.aljazira.extraction

import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.parsing.model.NormalizedSms

data class AccountExtraction(
    val source: AccountReference? = null,
    val destination: AccountReference? = null,
)

/**
 * Keeps source and destination distinct. Does not resolve ownership.
 */
class AccountExtractor {
    fun extract(sms: NormalizedSms, bank: Bank): AccountExtraction {
        val text = sms.comparisonBody
        var source: AccountReference? = null
        var destination: AccountReference? = null

        for (pattern in SOURCE_PATTERNS) {
            val match = pattern.find(text) ?: continue
            source = AccountReference(bank, match.groupValues[1])
            break
        }

        for (pattern in DESTINATION_PATTERNS) {
            val match = pattern.find(text) ?: continue
            destination = AccountReference(bank, match.groupValues[1])
            break
        }

        return AccountExtraction(source = source, destination = destination)
    }

    companion object {
        private val SOURCE_PATTERNS = listOf(
            Regex("""خصمت\s*من\s*حساب\s*:\s*(\d{4})"""),
            Regex("""من\s*حساب\s*:\s*(\d{4})"""),
            Regex("""رقم\s*حساب\s*المرسل\s*:\s*(\d{4})"""),
            Regex("""(?<![\p{L}])حساب\s*:\s*(\d{4})"""),
            // "من: 3001" but not "خصمت من حساب" already matched; avoid "من حساب"
            Regex("""(?:^|\n)\s*من\s*:\s*(\d{4})"""),
        )

        private val DESTINATION_PATTERNS = listOf(
            Regex("""المعرف\s*البديل\s*\\?\s*الايبان\s*:\s*(\d{4})"""),
            Regex("""الى\s*حساب(?:ك)?(?:\s*الجاري)?\s*:\s*(\d{4})"""),
            Regex("""إلى\s*:\s*(\d{4})"""),
            Regex("""الى\s*:\s*(\d{4})"""),
            Regex("""إلى\s*حساب\s*:\s*(\d{4})"""),
        )
    }
}
