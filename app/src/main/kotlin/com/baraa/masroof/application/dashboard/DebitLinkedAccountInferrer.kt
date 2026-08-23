package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord

/**
 * Infers a debit (Mada) card's linked account from parsed SMS when registry link is missing.
 */
object DebitLinkedAccountInferrer {
    private val SOURCE_ACCOUNT_PATTERNS = listOf(
        Regex("""خصمت\s*من\s*حساب\s*:\s*(\d{4})"""),
        Regex("""من\s*حساب\s*:\s*(\d{4})"""),
        Regex("""حساب\s*رقم\s*:\s*(\d{4})"""),
        Regex("""رقم\s*حساب\s*المرسل\s*:\s*(\d{4})"""),
        Regex("""(?<![\p{L}])حساب\s*:\s*(\d{4})"""),
    )

    fun inferAccountLast4(
        bank: Bank,
        cardLast4: String,
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
    ): String? {
        for (record in parsedRecords) {
            val event = record.event
            if (event.bank != bank) continue
            if (event.cardRef?.last4 != cardLast4) continue
            val body = rawSmsById[event.rawSmsId]?.body ?: continue
            val accountLast4 = SOURCE_ACCOUNT_PATTERNS.firstNotNullOfOrNull { pattern ->
                pattern.find(body)?.groupValues?.getOrNull(1)
            } ?: continue
            return accountLast4
        }
        return null
    }
}
