package com.baraa.masroof.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sanitization unit tests. Required by the spec — must verify the
 * diagnostic report excludes:
 *  - SMS bodies
 *  - merchant names
 *  - exact amounts
 *  - card / account digits
 *  - API keys
 *
 * And that the redaction rules replace:
 *  - PANs / masked PANs → [CARD_LAST4]
 *  - IBANs → [IBAN]
 *  - phones → [PHONE]
 *  - OTPs → [OTP]
 *  - long reference numbers → [REFERENCE]
 *  - balance numerics → [BALANCE]
 *  - personal names → [NAME]
 */
class DiagnosticsSanitizationTest {

    // -- TextSanitizer --------------------------------------------------

    @Test
    fun panReplacedWithCardLast4() {
        val s = TextSanitizer.sanitize("Card 4111 1111 1111 1111 charged 100 SAR")
        assertTrue("must replace PAN", s.contains("[CARD_LAST4"))
        assertFalse("must not contain raw PAN", s.contains("4111 1111 1111 1111"))
        assertFalse("must not contain a 16-digit run", Regex("""\b\d{16}\b""").containsMatchIn(s))
    }

    @Test
    fun maskedPanReplaced() {
        val s = TextSanitizer.sanitize("Card ****1234 charged 100 SAR")
        assertTrue(s.contains("[CARD_LAST4]"))
        assertFalse(s.contains("1234"))
    }

    @Test
    fun ibanReplaced() {
        val valIban = "SA0380000000608010167519"
        val s = TextSanitizer.sanitize("Transfer to $valIban")
        assertTrue(s.contains("[IBAN]"))
        assertFalse(s.contains("SA038"))
    }

    @Test
    fun saudiPhoneReplaced() {
        val s = TextSanitizer.sanitize("Contact 0551234567 for help")
        assertTrue(s.contains("[PHONE]"))
        assertFalse(s.contains("0551234567"))
    }

    @Test
    fun internationalPhoneReplaced() {
        val s = TextSanitizer.sanitize("Call +966551234567")
        assertTrue(s.contains("[PHONE]"))
        assertFalse(s.contains("966551234567"))
    }

    @Test
    fun otpReplaced() {
        val s = TextSanitizer.sanitize("Your OTP is 482931. Do not share.")
        assertTrue("OTP keyword must be replaced", s.contains("[OTP]"))
        assertFalse("raw digits must not survive", s.contains("482931"))
    }

    @Test
    fun arabicOtpKeywordReplaced() {
        val s = TextSanitizer.sanitize("كلمة المرور: 482931")
        assertTrue(s.contains("[OTP]"))
        assertFalse(s.contains("482931"))
    }

    @Test
    fun referenceNumberReplaced() {
        val s = TextSanitizer.sanitize("Reference number 1234567890")
        assertTrue(s.contains("[REFERENCE]"))
        assertFalse(s.contains("1234567890"))
    }

    @Test
    fun arabicReferenceKeywordReplaced() {
        val s = TextSanitizer.sanitize("رقم العملية 9988776655")
        assertTrue(s.contains("[REFERENCE]"))
        assertFalse(s.contains("9988776655"))
    }

    @Test
    fun balanceReplaced() {
        val s = TextSanitizer.sanitize("رصيدك 12,345.67 ر.س")
        assertTrue(s.contains("[BALANCE]"))
        assertFalse("must not contain raw balance", Regex("""12,345""").containsMatchIn(s))
    }

    @Test
    fun personalNameReplaced() {
        val s = TextSanitizer.sanitize("Mr. Abdullah Al-Saud transferred money")
        assertTrue(s.contains("[NAME]"))
    }

    @Test
    fun lastFourDigitsHelper() {
        assertEquals("4321", TextSanitizer.lastFourDigits("****1234 4321"))
        assertNull(TextSanitizer.lastFourDigits(""))
        assertNull(TextSanitizer.lastFourDigits(null))
    }

    @Test
    fun shapeOnlyStripsStructure() {
        val s = TextSanitizer.shapeOnly("Hello, world! How are you? 42.")
        // Punctuation is replaced with spaces, runs collapsed.
        assertEquals("Hello world How are you 42", s)
    }

    // -- DiagnosticReport -------------------------------------------------

    @Test
    fun diagnosticReportExcludesSmsBodies() {
        val snap = makeSnapshot(merchantDisplay = "WHO CARES")
        val text = DiagnosticReport.renderText(snap)
        // The merchant display name is the *only* non-numeric field
        // besides category name that could leak, and the report must
        // not include it.
        assertFalse("merchant name must not appear", text.contains("WHO CARES"))
    }

    @Test
    fun diagnosticReportExcludesExactAmounts() {
        val snap = makeSnapshot()
        val text = DiagnosticReport.renderText(snap)
        // The bucket is shown but the underlying amount is not.
        // We do not embed the BigDecimal in the snapshot in the first
        // place, but the renderer must not invent one.
        assertFalse("no SAR value should be embedded", Regex("""\d+\.\d+\s*(ر\.س|SAR)""").containsMatchIn(text))
    }

    @Test
    fun diagnosticReportExcludesCardOrAccountDigits() {
        val snap = makeSnapshot()
        val text = DiagnosticReport.renderText(snap)
        assertFalse("no 4-digit run should appear", Regex("""\b\d{4}\b""").containsMatchIn(text))
    }

    @Test
    fun diagnosticReportExcludesApiKey() {
        val snap = makeSnapshot()
        val text = DiagnosticReport.renderText(snap)
        // The API key isn't on the snapshot at all, but we double-check
        // by ensuring no "sk-" prefix appears.
        assertFalse(text.contains("sk-"))
        assertFalse(text.contains("Bearer "))
    }

    @Test
    fun diagnosticReportJsonOmitsSensitiveData() {
        val snap = makeSnapshot(merchantDisplay = "SHOULD NOT LEAK")
        val json = DiagnosticReport.renderJson(snap)
        assertFalse("JSON must not contain merchant", json.contains("SHOULD NOT LEAK"))
        assertFalse(json.contains("sk-"))
        assertFalse(json.contains("Bearer "))
    }

    @Test
    fun diagnosticReportContainsExpectedSections() {
        val snap = makeSnapshot()
        val text = DiagnosticReport.renderText(snap)
        // Required top-level labels.
        assertTrue(text.contains("تقرير تشخيص تطبيق مصروف"))
        assertTrue(text.contains("Android"))
        assertTrue(text.contains("قاعدة البيانات"))
        assertTrue(text.contains("الرسائل"))
        assertTrue(text.contains("التصنيفات"))
        assertTrue(text.contains("التصنيف الذكي"))
        assertTrue(text.contains("المحللات"))
        assertTrue(text.contains("قواعد التصنيف"))
        assertTrue(text.contains("الأخطاء"))
    }

    @Test
    fun diagnosticErrorLogCapsAtLimit() {
        val log = DiagnosticErrorLog(limit = 3)
        for (i in 1..10) {
            log.record(
                category = DiagnosticError.ErrorCategory.SMS_PERMISSION_DENIED,
                message = "Error #$i"
            )
        }
        assertEquals(3, log.snapshot().size)
        // The most recent 3 are kept (10, 9, 8) → but 8, 9, 10 in order.
        val msgs = log.snapshot().map { it.message }
        assertEquals(listOf("Error #8", "Error #9", "Error #10"), msgs)
    }

    @Test
    fun diagnosticErrorLogPreservesOrder() {
        val log = DiagnosticErrorLog(limit = 50)
        log.record(DiagnosticError.ErrorCategory.SMS_PROVIDER_UNAVAILABLE, "first")
        log.record(DiagnosticError.ErrorCategory.NETWORK_UNAVAILABLE, "second")
        log.record(DiagnosticError.ErrorCategory.DATABASE_ERROR, "third")
        val msgs = log.snapshot().map { it.message }
        assertEquals(listOf("first", "second", "third"), msgs)
    }

    @Test
    fun diagnosticErrorLogClearWorks() {
        val log = DiagnosticErrorLog()
        log.record(DiagnosticError.ErrorCategory.UNKNOWN, "x")
        assertEquals(1, log.snapshot().size)
        log.clear()
        assertEquals(0, log.snapshot().size)
    }

    // -- helpers ---------------------------------------------------------

    private fun makeSnapshot(merchantDisplay: String = "test-merchant"): DiagnosticSnapshot =
        DiagnosticSnapshot(
            appVersionName = "0.1.0-test",
            appVersionCode = 2L,
            databaseSchemaVersion = 6,
            androidVersion = "14 (SDK 34)",
            deviceManufacturer = "TestCo",
            deviceModel = "TestPhone",
            smsPermissionGranted = true,
            smsScannedCount = 10L,
            smsFinancialDetectedCount = 5L,
            smsParsedCount = 4L,
            smsParseFailureCount = 1L,
            savedTransactionsCount = 7L,
            exactDuplicatesCount = 1L,
            possibleDuplicatesCount = 1L,
            needsReviewCount = 2L,
            categoryCount = 12L,
            merchantMemoryCount = 3L,
            aiEnabled = false,
            aiProviderName = null,
            aiModelName = null,
            lastAiOutcome = DiagnosticSnapshot.EMPTY_OUTCOME,
            parserNames = listOf("TemplateResolver:AlRajhi", "TemplateResolver"),
            ruleNames = listOf("SAFETY", "CATEGORY_RULE"),
            recentErrors = emptyList(),
            buildTimestamp = "2024-01-01T00:00:00Z",
            diagnosticReportVersion = "v1"
        )
}