package com.baraa.masroof.sms

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android-runtime instrumentation smoke for the pattern engine.
 *
 * The on-device Jazira sender showed:
 *   Potential financial SMS: 196
 *   Processed: 0
 *   Core failed: 196
 *   Failure breakdown: TEMPLATE_BUILD — 196 NoClassDefFoundError
 *
 * The server JVM unit tests pass, so this is an Android
 * runtime/class-loading/packaging problem, not a parsing problem.
 * This smoke test reproduces the exact production code paths on a
 * real Android runtime so a connected device run fails with the
 * underlying [NoClassDefFoundError] (and its missing-class FQN from
 * `Throwable.message`) when the bug is present.
 *
 * It does NOT change parsing, pattern matching, semantics, Room, or UI.
 */
@RunWith(AndroidJUnit4::class)
class PatternEngineAndroidRuntimeSmokeTest {

    private val posBody = """
        شراء عبر نقاط البيع (Google Pay)
        لدى: MALAYSIA FOODS RESTA
        بمبلغ: 127.00 SAR
        في: 13:24 2026-08-03
        بطاقة مدى رقم: 8219
    """.trimIndent()

    private val transferBody = """
        عملية حوالة مالية صادرة مقبولة
        خصمت من حساب: 3002
        الى: TEST_BENEFICIARY
        مبلغ العملية: 13,258.00 SAR
        المعرف البديل \الايبان : 0593
        [البنك العربي الوطني]
        في: 2026-08-01 12:26
        رقم المعاملة: TEST_REFERENCE_1
    """.trimIndent()

    @Test
    fun messageTemplateEngine_buildFromSms_doesNotThrowNoClassDefFoundError() {
        // If TEMPLATE_BUILD is throwing NoClassDefFoundError on Android,
        // the failure message (the missing class FQN) will surface here.
        val built = MessageTemplateEngine.buildFromSms(posBody)
        assertNotNull(built)
        assertTrue("template must not be blank", built.templateText.isNotBlank())
        assertTrue(built.transactionType != null)
    }

    @Test
    fun messageTemplateEngine_buildFromSms_handlesTransferBody() {
        val built = MessageTemplateEngine.buildFromSms(transferBody)
        assertNotNull(built)
        assertTrue(built.templateText.contains("{AMOUNT}"))
        assertTrue(built.templateText.contains("{IBAN_LAST4}"))
    }

    @Test
    fun patternDiscoveryService_discoverSafely_producesPatternsOnAndroidRuntime() {
        val sms = listOf(
            SmsMessage(id = 1L, sender = "AlJazira", body = posBody, timestamp = 1L),
            SmsMessage(id = 2L, sender = "AlJazira", body = transferBody, timestamp = 2L),
        )
        val result = PatternDiscoveryService.discoverSafely(sms, emptyList())
        // On-device reproduction guard. If TEMPLATE_BUILD throws
        // NoClassDefFoundError, this assertion never runs — the test
        // fails with the NoClassDefFoundError and its missing-class message.
        assertFalse(
            "coreFailedMessages must be 0 on a healthy Android runtime; " +
                "coreFailed=${result.coreFailedMessages} optionalFailures=" +
                "${result.optionalStageFailureCount}",
            result.coreFailedMessages > 0,
        )
        assertTrue("discover must produce candidates", result.patterns.isNotEmpty())
        // Reconciliation invariant must hold on Android too.
        assertTrue(result.isReconciled())
    }
}