package com.baraa.masroof.diagnostics

import com.baraa.masroof.data.repository.ApprovedTemplateCoverage
import com.baraa.masroof.data.repository.ScanFilterFunnel
import com.baraa.masroof.data.repository.ScanPreview
import com.baraa.masroof.data.repository.SenderTemplateCoverage
import com.baraa.masroof.data.repository.TemplateAnchorDiagnostic
import com.baraa.masroof.data.repository.UnmatchedTemplateGroupDiagnostic
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedTemplateDiagnosticReportTest {
    @Test
    fun sanitizerRemovesIdentifiersNamesAmountsAndReferences() {
        val original = """
            خصمت من حساب: 3001
            إلى: ولاء عاشور
            مبلغ العملية: 1789.00 SAR
            المعرف البديل الايبان: 6810
            في: 27-07-2026 07:43
            رقم المعاملة: 2BTMS10743701555
        """.trimIndent()

        val safe = ApprovedTemplateDiagnosticSanitizer.sanitizeMessage(original)

        listOf("3001", "ولاء", "عاشور", "1789", "6810", "2026", "07:43", "2BTMS10743701555")
            .forEach { secret -> assertFalse("$secret leaked in $safe", safe.contains(secret)) }
        assertTrue(safe.contains("{"))
        assertFalse(ApprovedTemplateDiagnosticSanitizer.containsSensitiveValue(safe))
    }

    @Test
    fun numericSenderIsNeverExported() {
        assertTrue(
            ApprovedTemplateDiagnosticSanitizer.sanitizeSender("+966500000000") ==
                "{NUMERIC_SENDER}",
        )
    }

    @Test
    fun customTemplateDisplayNameIsReplacedByCanonicalTaxonomyLabel() {
        val safe = ApprovedTemplateDiagnosticSanitizer.sanitizeDisplayName(
            "تحويل إلى ولاء",
            "TRANSFER_OUT",
        )
        assertFalse(safe.contains("ولاء"))
        assertTrue(safe.contains("CUSTOM_NAME_REDACTED"))
    }

    @Test
    fun jsonIncludesCoverageFailureAnchorsAndExplicitRoundTripAvailability() {
        val preview = ScanPreview(
            scannedMessages = 2,
            unmatchedTemplateMessages = 1,
            filterFunnel = ScanFilterFunnel(
                rawSms = 2,
                afterOtpFilter = 2,
                afterSenderFilter = 2,
                templateInput = 2,
                templateMatched = 1,
                unmatchedTemplate = 1,
            ),
            senderTemplateCoverage = listOf(
                SenderTemplateCoverage("BANK", 2, 1, 2, 1, 1, 0),
            ),
            approvedTemplateCoverage = listOf(
                ApprovedTemplateCoverage(
                    templateId = 12,
                    displayName = "شراء",
                    transactionType = "PURCHASE",
                    canonicalSignature = "purchase|amount",
                    active = true,
                    approved = true,
                    requiredPlaceholders = listOf("AMOUNT"),
                    optionalPlaceholders = listOf("AVAILABLE_BALANCE"),
                    historicalMessageCount = 59,
                    currentCandidateMessages = 2,
                    successfulMatches = 1,
                    failureCounts = mapOf("REQUIRED_FIELD_MISSING" to 1),
                ),
            ),
            unmatchedTemplateGroups = listOf(
                UnmatchedTemplateGroupDiagnostic(
                    count = 1,
                    normalizedSender = "BANK",
                    senderProfileId = 2,
                    closestTemplateId = 12,
                    closestTemplateName = "شراء",
                    closestTemplateTransactionType = "PURCHASE",
                    failureReason = "REQUIRED_FIELD_MISSING",
                    normalizedStructuralRepresentation = "شراء|{AMOUNT}",
                    redactedRepresentativeMessage = "مبلغ العملية: {AMOUNT}",
                    matchedAnchors = listOf(TemplateAnchorDiagnostic("شراء", "شراء")),
                    failedAnchors = listOf(
                        TemplateAnchorDiagnostic("الرصيد: {AVAILABLE_BALANCE}", null),
                    ),
                ),
            ),
        )

        val json = ApprovedTemplateDiagnosticReport.renderJson(
            ApprovedTemplateDiagnosticExportInput(
                preview = preview,
                scanTimestampMillis = 1_700_000_000_000,
                selectedDateStart = LocalDate.of(2026, 7, 1),
                selectedDateEnd = LocalDate.of(2026, 7, 31),
            ),
        )

        assertTrue(json.contains("\"approvedTemplateUnmatchedCount\":1"))
        assertTrue(json.contains("\"failureReason\":\"REQUIRED_FIELD_MISSING\""))
        assertTrue(json.contains("\"matchedAnchors\""))
        assertTrue(json.contains("\"failedAnchors\""))
        assertTrue(json.contains("NOT_AVAILABLE_NO_PERSISTED_SMS_ASSOCIATIONS"))
    }
}
