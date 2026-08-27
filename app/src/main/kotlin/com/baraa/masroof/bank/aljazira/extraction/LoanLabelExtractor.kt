package com.baraa.masroof.bank.aljazira.extraction

import com.baraa.masroof.parsing.model.NormalizedSms

/**
 * Extracts the financing product label from installment SMS (لـ: تمويل شخصي).
 */
class LoanLabelExtractor {
    fun extract(sms: NormalizedSms): String? {
        val match = LOAN_LABEL.find(sms.comparisonBody) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotEmpty() }
    }

    companion object {
        private val LOAN_LABEL =
            Regex("""(?:^|\n)\s*لـ\s*:\s*(.+?)(?:\n|$)""", RegexOption.MULTILINE)
    }
}
