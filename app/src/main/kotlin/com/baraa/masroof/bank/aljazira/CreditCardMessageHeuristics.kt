package com.baraa.masroof.bank.aljazira

/**
 * Distinguishes credit-card SMS from debit (mada) and current-account purchase SMS.
 *
 * AlJazira credit-card purchase messages carry available/due balances; debit purchases
 * debit the linked account ("خصمت من حساب") and must not feed credit-card snapshots.
 */
object CreditCardMessageHeuristics {
    private val DEBIT_MARKERS = listOf(
        "بطاقة مدى",
        "بطاقة مدي",
        "mada",
        "debit card",
    )

    private val CREDIT_MARKERS = listOf(
        "بطاقة ائتمان",
        "بطاقة إئتمان",
        "credit card",
    )

    private val DUE_MARKERS = listOf(
        "المبلغ المستحق",
        "due amount",
    )

    fun isCreditCardSms(body: String): Boolean {
        val text = body.replace('\n', ' ')
        if (DEBIT_MARKERS.any { text.contains(it, ignoreCase = true) }) return false
        if (text.contains("خصمت من حساب", ignoreCase = true)) return false
        if (CREDIT_MARKERS.any { text.contains(it, ignoreCase = true) }) return true
        if (DUE_MARKERS.any { text.contains(it, ignoreCase = true) }) return true
        return false
    }
}
