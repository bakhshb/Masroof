package com.baraa.masroof.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for Task 10: bank service / maintenance / OTP messages must NOT
 * appear in the manual financial picker and must NOT become transaction
 * templates. They are bank messages, but they are not financial transactions.
 *
 * The picker uses [PatternDraftFactory.summarize] (returns null for excluded),
 * and the draft path uses [PatternDraftFactory.fromSms] (returns NonFinancial).
 * Both rely on the single [MessageTypeCueCatalog] source of truth — no second
 * parser/normalizer is introduced.
 */
class ManualPickerNonFinancialFilterTest {

    private val realPosBody = """
        شراء عبر نقاط البيع
        لدى: SAMPLE STORE
        بمبلغ: 60.99 SAR
        في: 12:30 2026-08-06
        بطاقة ائتمانية: 7271
    """.trimIndent()

    private val excludedBodies = listOf(
        "رمز التحقق: 1234 لا تشاركه مع أحد",
        "تم تفعيل خدمة الشراء الآمن عبر الإنترنت. للاستفسار اتصل 8001234567",
        "تم إيقاف خدمة الشراء الآمن عبر الإنترنت",
        "نود إشعارك بأن بعض خدماتنا ستكون غير متاحة يوم الجمعة لأغراض الصيانة الدورية.",
        "نحن بصدد صيانة دورية للنظام. عذراً عن الإزعاج.",
        "تم تغيير الحد اليومي للشراء عبر الانترنت إلى 5000 ريال",
    )

    @Test
    fun summarize_excludesOtpServiceMaintenanceAndLimitChange() {
        for (body in excludedBodies) {
            assertNull(
                "expected null summary for excluded message: $body",
                PatternDraftFactory.summarize(SmsMessage(1, "BANK", body, 1L)),
            )
        }
    }

    @Test
    fun summarize_includesRealFinancialAndUncertainFinancial() {
        val summary = PatternDraftFactory.summarize(SmsMessage(1, "BANK", realPosBody, 1L))
        assertNotNull(summary)
        assertTrue(summary!!.amount != null)
        assertTrue(summary.maskedLast4 != null)
    }

    @Test
    fun fromSms_returnsNonFinancialForOtpServiceMaintenanceAndLimitChange() {
        for (body in excludedBodies) {
            val result = PatternDraftFactory.fromSms(SmsMessage(1, "BANK", body, 1L), 7L)
            assertTrue(
                "expected NonFinancial for: $body — got $result",
                result is PatternDraftResult.NonFinancial,
            )
        }
    }

    @Test
    fun fromSms_returnsReadyForRealPurchase() {
        val result = PatternDraftFactory.fromSms(SmsMessage(1, "BANK", realPosBody, 1L), 7L)
        assertTrue("expected Ready, got $result", result is PatternDraftResult.Ready)
    }

    @Test
    fun fromSms_emptyAndBlankAreNonFinancial() {
        assertTrue(
            PatternDraftFactory.fromSms(SmsMessage(1, "BANK", "", 1L), 7L)
                is PatternDraftResult.NonFinancial,
        )
        assertTrue(
            PatternDraftFactory.fromSms(SmsMessage(1, "BANK", "   ", 1L), 7L)
                is PatternDraftResult.NonFinancial,
        )
    }
    @Test
    fun discovery_skipsServiceAndMaintenanceMessagesAsNonFinancial() {
        val messages = listOf(
            SmsMessage(1, "BANK", realPosBody, 1L),
            SmsMessage(2, "BANK", "تم تفعيل خدمة الشراء الآمن عبر الإنترنت", 2L),
            SmsMessage(3, "BANK", "نود إشعارك بأن بعض خدماتنا ستكون غير متاحة للصيانة.", 3L),
            SmsMessage(4, "BANK", "رمز التحقق: 1234", 4L),
        )
        val result = PatternDiscoveryService.discoverSafely(messages, emptyList())
        // Only the real purchase becomes a pattern.
        assertEquals(1, result.patterns.size)
        val parsedType = result.patterns.single().transactionTypeName?.let {
            com.baraa.masroof.transaction.TransactionTypeTaxonomy.parse(it)
        }
        assertEquals(com.baraa.masroof.transaction.TransactionType.PURCHASE, parsedType)
        assertTrue(result.skippedNonFinancial >= 2)
        assertTrue(result.skippedOtp >= 1)
        assertTrue(result.isReconciled())
    }
}
