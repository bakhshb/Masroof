package com.baraa.masroof.onboarding

import com.baraa.masroof.sms.MessageTemplateEngine
import com.baraa.masroof.sms.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingImportPlannerTest {
    @Test
    fun countForTemplateSplitsMatchedAndUnmatched() {
        val built = MessageTemplateEngine.buildFromSms(
            """
            شراء
            بطاقة: *7271
            مبلغ: 12.50 SAR
            """.trimIndent(),
        )
        val messages = listOf(
            SmsMessage(
                1,
                "BANK",
                """
                شراء
                بطاقة: *7271
                مبلغ: 12.50 SAR
                """.trimIndent(),
                1L,
            ),
            SmsMessage(
                2,
                "BANK",
                """
                شراء
                بطاقة: *9999
                مبلغ: 99.00 SAR
                """.trimIndent(),
                2L,
            ),
            SmsMessage(3, "BANK", "OTP 123456", 3L),
        )
        val counts = OnboardingImportPlanner.countForTemplate(built.templateText, messages)
        assertEquals(3, counts.total)
        assertTrue(counts.matched >= 1)
        assertEquals(counts.total, counts.matched + counts.unmatched)
    }

    @Test
    fun zeroApprovedPatternsMeansNothingMatched() {
        // Architectural gate: without APPROVED patterns, preview match count is zero.
        val messages = listOf(SmsMessage(1, "BANK", "شراء مبلغ: 10.00 SAR", 1L))
        val counts = OnboardingImportPlanner.countForTemplate("__no_such_template__", messages)
        assertEquals(0, counts.matched)
        assertEquals(1, counts.unmatched)
        assertEquals(1, counts.total)
    }

    @Test
    fun templateFromExactSmsIsDeterministic() {
        val body = """
            شراء عبر البطاقة
            البطاقة: ****7271
            المبلغ: 250.00 ريال
        """.trimIndent()
        val a = MessageTemplateEngine.buildFromSms(body)
        val b = MessageTemplateEngine.buildFromSms(body)
        assertEquals(a.templateText, b.templateText)
        assertTrue(a.templateText.isNotBlank())
        assertTrue(MessageTemplateEngine.matches(a.templateText, body))
    }

    @Test
    fun filterMessagesForSenderUsesNormalizedKey() {
        val messages = listOf(
            SmsMessage(1, "SNB", "a", 1L),
            SmsMessage(2, "Other", "b", 2L),
        )
        val key = requireNotNull(com.baraa.masroof.sms.SenderNormalizer.normalize("SNB"))
        val filtered = OnboardingImportPlanner.filterMessagesForSender(messages, key)
        assertEquals(1, filtered.size)
        assertEquals("SNB", filtered.single().sender)
    }
}
