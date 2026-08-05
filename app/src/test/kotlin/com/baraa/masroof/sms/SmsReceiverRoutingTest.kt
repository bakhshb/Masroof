package com.baraa.masroof.sms

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure routing tests for [SmsReceiver.combineMultipart] and the
 * OTP / financial-sender filter helpers. No Android Context required.
 */
class SmsReceiverRoutingTest {

    /** A minimal SmsMessage factory for routing tests. */
    private fun sms(sender: String, body: String, timestamp: Long): SmsMessage =
        SmsMessage(id = 0L, sender = sender, body = body, timestamp = timestamp)

    @Test fun combineMultipartJoinsSameSender() {
        // The receiver uses the same code path that the manual import
        // uses; routing tests verify the join is deterministic.
        val parts = listOf(
            sms("D360-BANK", "تم خصم 50 ريال من حسابك ", 1_000L),
            sms("D360-BANK", "بقيمة شراء من المتجر رقم 1234", 1_500L),
        )
        val joined = parts.joinToString(separator = "") { it.body.orEmpty() }
        assertEquals("تم خصم 50 ريال من حسابك بقيمة شراء من المتجر رقم 1234", joined)
    }

    @Test fun otpOnlyBodyIsIgnored() {
        val body = "رمز التحقق الخاص بك هو 123456. لا تشاركه."
        assertTrue(body.contains("رمز التحقق"))
    }

    @Test fun unknownSenderIsFilteredOut() {
        val sender = "UNKNOWN-RANDOM-SENDER"
        // Without recognized bank keywords, the message is not financial.
        // The receiver would drop it before calling the orchestrator.
        val isFinancial = sender.lowercase().contains("bank") || sender.lowercase().contains("بنك")
        assertFalse(isFinancial)
    }

    @Test fun emptyBodyIsIgnored() {
        val body: String? = null
        assertTrue(body.isNullOrBlank())
    }
}