package com.baraa.masroof.domain.rules

/**
 * Detects OTP / verification SMS that must never become financial transactions.
 */
object OtpMessageHeuristics {
    fun isOtpMessage(comparisonBody: String): Boolean {
        val text = comparisonBody
        if (
            text.contains("رمز التحقق") ||
            text.contains("otp") ||
            text.contains("one time password") ||
            text.contains("one-time password") ||
            text.contains("كلمة مرور") ||
            text.contains("verification code") ||
            text.contains("do not share") ||
            text.contains("لا تشاركه") ||
            text.contains("رمز التفعيل") ||
            text.contains("لإضافة المستفيد")
        ) {
            return true
        }
        return text.contains("code:") && text.contains("password")
    }
}
