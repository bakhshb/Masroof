package com.baraa.masroof.domain.rules

import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParsedEvent
import java.math.BigDecimal

/**
 * Decides whether an SMS is informational only and should never enter review.
 */
object InformationalMessagePolicy {
    private val INFORMATIONAL_BODY_MARKERS = listOf(
        "رمز التفعيل",
        "لإضافة المستفيد",
        "رمز التحقق",
        "تم تسجيل الدخول",
        "مكافآتي",
        "رصيد نقاطك",
        "برنامج مكاف",
        "اسم المستفيد",
        "الاسم المختصر",
        "حالة: غير نشط",
        "حالة : غير نشط",
        "تم إضافة المستفيد",
        "إضافة مستفيد",
        "تنبيه أمني",
        "تنويه",
        "إصدار كشف حساب",
        "كشف حساب",
        "تاريخ الاستحقاق",
        "المبلغ المستحق",
    )

    fun shouldAutoIgnore(event: ParsedEvent, smsBody: String): Boolean =
        shouldAutoIgnore(
            messageFamily = event.messageFamily,
            parsedAmount = event.amount,
            smsBody = smsBody,
        )

    fun shouldAutoIgnore(
        messageFamily: MessageFamily?,
        parsedAmount: Money?,
        smsBody: String,
    ): Boolean {
        when (messageFamily) {
            MessageFamily.OTP,
            MessageFamily.NON_FINANCIAL,
            MessageFamily.BALANCE_NOTICE,
            -> return true

            MessageFamily.UNKNOWN -> return isInformationalUnknown(parsedAmount, smsBody)

            else -> return false
        }
    }

    private fun isInformationalUnknown(parsedAmount: Money?, smsBody: String): Boolean {
        if (looksLikeInformationalBody(smsBody)) {
            return true
        }
        if (!parsedAmount.isSignificantTransactionAmount()) {
            return !smsBody.containsNonZeroMoneyWording()
        }
        return false
    }

    private fun looksLikeInformationalBody(smsBody: String): Boolean =
        INFORMATIONAL_BODY_MARKERS.any { smsBody.contains(it) }

    private fun Money?.isSignificantTransactionAmount(): Boolean =
        this != null && amount.compareTo(BigDecimal.ZERO) > 0

    /**
     * Detects money wording with a non-zero numeric value (transaction-like SMS).
     */
    private fun String.containsNonZeroMoneyWording(): Boolean {
        val amountPatterns = listOf(
            Regex("""(\d[\d,]*(?:\.\d+)?)\s*(?:SAR|ر\.س|ريال)""", RegexOption.IGNORE_CASE),
            Regex("""(?:SAR|ر\.س|ريال)\s*(\d[\d,]*(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:بمبلغ|مبلغ|القيمة|القسط)\s*:?\s*(\d[\d,]*(?:\.\d+)?)"""),
        )
        return amountPatterns.any { pattern ->
            pattern.findAll(this).any { match ->
                val raw = match.groupValues.getOrNull(1)?.replace(",", "") ?: return@any false
                raw.toBigDecimalOrNull()?.let { it.compareTo(BigDecimal.ZERO) > 0 } == true
            }
        }
    }
}
