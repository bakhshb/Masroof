package com.baraa.masroof.domain.rules

import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParsedEvent

/**
 * Decides whether an SMS is informational only and should never enter review.
 */
object InformationalMessagePolicy {
    fun shouldAutoIgnore(event: ParsedEvent, smsBody: String): Boolean =
        shouldAutoIgnore(
            messageFamily = event.messageFamily,
            hasParsedAmount = event.amount != null,
            smsBody = smsBody,
        )

    fun shouldAutoIgnore(
        messageFamily: MessageFamily?,
        hasParsedAmount: Boolean,
        smsBody: String,
    ): Boolean {
        when (messageFamily) {
            MessageFamily.OTP,
            MessageFamily.NON_FINANCIAL,
            MessageFamily.BALANCE_NOTICE,
            -> return true

            MessageFamily.UNKNOWN ->
                return !hasParsedAmount && !smsBody.containsMoneyIndicators()

            else -> return false
        }
    }

    private fun String.containsMoneyIndicators(): Boolean =
        contains("sar", ignoreCase = true) ||
            contains("ر.س") ||
            contains("ريال") ||
            contains("مبلغ") ||
            contains("بمبلغ") ||
            contains("القيمة:") ||
            contains("القسط")
}
