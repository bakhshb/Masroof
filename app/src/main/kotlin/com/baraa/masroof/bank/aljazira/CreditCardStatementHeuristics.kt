package com.baraa.masroof.bank.aljazira

/**
 * Detects credit-card statement notices (typically issued around the 10th).
 */
object CreditCardStatementHeuristics {
    fun isStatementSms(body: String): Boolean {
        val text = body.replace('\n', ' ')
        if (text.contains("إصدار كشف حساب", ignoreCase = true)) return true
        return text.contains("كشف حساب", ignoreCase = true) &&
            text.contains("المبلغ المستحق", ignoreCase = true)
    }
}
