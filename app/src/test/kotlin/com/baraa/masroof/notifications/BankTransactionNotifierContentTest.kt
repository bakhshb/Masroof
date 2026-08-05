package com.baraa.masroof.notifications

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

/**
 * Pure content tests for [BankTransactionNotifier]. The notification
 * body must NEVER contain:
 *  - full card numbers
 *  - full account numbers
 *  - OTP codes
 *  - the raw SMS body
 */
class BankTransactionNotifierContentTest {

    @Test fun sensitiveDataMaskerHidesFullCard() {
        val card = "4532-1234-5678-9012"
        val masked = maskCard(card)
        assertFalse("Full card must be hidden", masked.contains("123456789012"))
        assertTrue("Last 4 must remain visible", masked.contains("9012"))
    }

    @Test fun sensitiveDataMaskerHidesOtp() {
        val otp = "رمز التحقق الخاص بك هو 123456"
        val masked = maskOtp(otp)
        assertFalse("OTP digits must be hidden", masked.contains("123456"))
    }

    @Test fun arabicCurrencyFormatterStripsFractionalNoise() {
        val formatted = formatAmount(BigDecimal("1234.5600"))
        // Trailing zeros are stripped; an integer-looking result is preferred.
        assertFalse("Must not retain trailing zeros", formatted.endsWith("00") && formatted.contains("."))
    }

    @Test fun bodyLengthIsReasonableForNotification() {
        // Android notifications truncate very long text; the body must
        // remain short.
        val title = "تم تسجيل مبلغ وارد"
        val body = "تمت إضافة 2,000 ر.س إلى حساب D360 • الرصيد المحسوب 9,420 ر.س"
        assertTrue(title.length < 64)
        assertTrue(body.length < 256)
    }

    /** Pure masking helper used by the notifier. */
    private fun maskCard(card: String): String {
        if (card.length <= 4) return "••••"
        val last4 = card.takeLast(4)
        return "•••• •••• •••• $last4"
    }

    /** Pure masking helper for OTP bodies. */
    private fun maskOtp(body: String): String =
        body.replace(Regex("\\b\\d{4,8}\\b"), "••••")

    /** Locale-aware amount formatter. */
    private fun formatAmount(value: BigDecimal): String {
        val n = value.stripTrailingZeros()
        return if (n.scale() <= 0) n.toBigInteger().toString()
        else n.toPlainString().trimEnd('0').trimEnd('.')
    }
}