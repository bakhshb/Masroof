package com.baraa.masroof.diagnostics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.baraa.masroof.data.repository.ScanPreview
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import java.util.Locale

data class ApprovedTemplateDiagnosticExportInput(
    val preview: ScanPreview,
    val scanTimestampMillis: Long,
    val selectedDateStart: LocalDate?,
    val selectedDateEnd: LocalDate?,
)

/** Pure JSON renderer; no raw SMS body is accepted by this API. */
object ApprovedTemplateDiagnosticReport {
    const val FORMAT_VERSION = 1

    fun renderJson(input: ApprovedTemplateDiagnosticExportInput): String {
        val preview = input.preview
        val funnel = preview.filterFunnel
        return buildString {
            append("{\n")
            field("formatVersion", FORMAT_VERSION, comma = true)
            field("privacy", "Values are aggressively redacted; no raw SMS bodies are exported.", comma = true)
            field(
                "scanTimestamp",
                Instant.ofEpochMilli(input.scanTimestampMillis).toString(),
                comma = true,
            )
            field("selectedDateStart", input.selectedDateStart?.toString(), comma = true)
            field("selectedDateEnd", input.selectedDateEnd?.toString(), comma = true)
            append("\"summary\":")
            obj {
                field("rawSmsCount", preview.scannedMessages, true)
                field("otpCount", preview.otpOrAuthMessages, true)
                field("unregisteredSenderCount", preview.unregisteredSenderMessages, true)
                field("nonFinancialCount", preview.nonFinancialMessages, true)
                field(
                    "templateMatchingInputCount",
                    preview.senderTemplateCoverage.sumOf { it.messagesEnteringMatcher },
                    true,
                )
                field(
                    "approvedTemplateMatchedCount",
                    preview.senderTemplateCoverage.sumOf { it.matched },
                    true,
                )
                field(
                    "approvedTemplateUnmatchedCount",
                    preview.senderTemplateCoverage.sumOf { it.unmatched },
                    true,
                )
                field("ambiguousCount", funnel?.ambiguousTemplate ?: 0, true)
                field("extractionFailedCount", preview.extractionFailedMessages, true)
                field("readyToImportCount", preview.readyToImport, true)
                field("needsReviewCount", preview.messageReviewCount, false)
            }
            append(",\n\"failureReasonCounts\":")
            map(preview.templateFailureCounts)
            append(",\n\"senders\":[")
            preview.senderTemplateCoverage.forEachIndexed { index, sender ->
                if (index > 0) append(',')
                append('\n')
                obj {
                    field(
                        "sender",
                        ApprovedTemplateDiagnosticSanitizer.sanitizeSender(sender.normalizedSender),
                        true,
                    )
                    field("senderId", sender.senderProfileId, true)
                    field("approvedTemplates", sender.approvedTemplatesLoaded, true)
                    field("messagesEnteringMatcher", sender.messagesEnteringMatcher, true)
                    field("matched", sender.matched, true)
                    field("unmatched", sender.unmatched, true)
                    field("ambiguous", sender.ambiguous, false)
                }
            }
            if (preview.senderTemplateCoverage.isNotEmpty()) append('\n')
            append("],\n\"approvedTemplates\":[")
            preview.approvedTemplateCoverage.forEachIndexed { index, template ->
                if (index > 0) append(',')
                append('\n')
                obj {
                    field("templateId", template.templateId, true)
                    field(
                        "displayName",
                        ApprovedTemplateDiagnosticSanitizer.sanitizeDisplayName(
                            template.displayName,
                            template.transactionType,
                        ),
                        true,
                    )
                    field("transactionType", template.transactionType, true)
                    field(
                        "canonicalSignature",
                        ApprovedTemplateDiagnosticSanitizer.sanitizeCanonicalStructure(
                            template.canonicalSignature,
                        ),
                        true,
                    )
                    field("active", template.active, true)
                    field("approved", template.approved, true)
                    array("requiredPlaceholders", template.requiredPlaceholders, true)
                    array("optionalPlaceholders", template.optionalPlaceholders, true)
                    field("historicalAssociatedMessages", template.historicalMessageCount, true)
                    field("messagesAttempted", template.currentCandidateMessages, true)
                    field("messagesMatched", template.successfulMatches, true)
                    field(
                        "messagesRejected",
                        template.currentCandidateMessages - template.successfulMatches,
                        true,
                    )
                    field(
                        "roundTripStatus",
                        "NOT_AVAILABLE_NO_PERSISTED_SMS_ASSOCIATIONS",
                        true,
                    )
                    append("\"failureCounts\":")
                    map(template.failureCounts)
                }
            }
            if (preview.approvedTemplateCoverage.isNotEmpty()) append('\n')
            append("],\n\"unmatchedGroups\":[")
            preview.unmatchedTemplateGroups.forEachIndexed { index, group ->
                if (index > 0) append(',')
                append('\n')
                obj {
                    field("count", group.count, true)
                    field(
                        "sender",
                        ApprovedTemplateDiagnosticSanitizer.sanitizeSender(group.normalizedSender),
                        true,
                    )
                    field("senderId", group.senderProfileId, true)
                    field("closestTemplateId", group.closestTemplateId, true)
                    field(
                        "closestTemplateName",
                        ApprovedTemplateDiagnosticSanitizer.sanitizeDisplayName(
                            group.closestTemplateName,
                            group.closestTemplateTransactionType,
                        ),
                        true,
                    )
                    field("failureReason", group.failureReason, true)
                    field(
                        "normalizedStructuralRepresentation",
                        ApprovedTemplateDiagnosticSanitizer.sanitizeCanonicalStructure(
                            group.normalizedStructuralRepresentation,
                        ),
                        true,
                    )
                    field(
                        "redactedRepresentativeMessage",
                        group.redactedRepresentativeMessage,
                        true,
                    )
                    anchors("matchedAnchors", group.matchedAnchors, true)
                    anchors("failedAnchors", group.failedAnchors, false)
                }
            }
            if (preview.unmatchedTemplateGroups.isNotEmpty()) append('\n')
            append("]\n}")
        }
    }

    private fun StringBuilder.anchors(
        name: String,
        anchors: List<com.baraa.masroof.data.repository.TemplateAnchorDiagnostic>,
        comma: Boolean,
    ) {
        append(quote(name)).append(":[")
        anchors.forEachIndexed { index, anchor ->
            if (index > 0) append(',')
            obj {
                field("expected", anchor.expected, true)
                field("actualStructuralLine", anchor.actualStructuralLine, false)
            }
        }
        append(']')
        if (comma) append(',')
        append('\n')
    }

    private fun StringBuilder.array(name: String, values: List<String>, comma: Boolean) {
        append(quote(name)).append(':')
        append(values.joinToString(prefix = "[", postfix = "]") { quote(it) })
        if (comma) append(',')
        append('\n')
    }

    private fun StringBuilder.map(values: Map<String, Int>) {
        append('{')
        values.entries.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            append(quote(entry.key)).append(':').append(entry.value)
        }
        append('}')
    }

    private inline fun StringBuilder.obj(block: StringBuilder.() -> Unit) {
        append("{\n")
        block()
        append('}')
    }

    private fun StringBuilder.field(name: String, value: Any?, comma: Boolean) {
        append(quote(name)).append(':')
        when (value) {
            null -> append("null")
            is Number, is Boolean -> append(value)
            else -> append(quote(value.toString()))
        }
        if (comma) append(',')
        append('\n')
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}

object ApprovedTemplateDiagnosticShareHelper {
    fun exportAndShare(context: Context, input: ApprovedTemplateDiagnosticExportInput) {
        val directory = File(context.cacheDir, "diagnostic_reports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val file = File(directory, "masroof-template-diagnostics-$timestamp.json")
        file.writeText(ApprovedTemplateDiagnosticReport.renderJson(input), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Masroof ApprovedTemplateMatcher diagnostics")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(share, "تصدير تقرير التشخيص").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
