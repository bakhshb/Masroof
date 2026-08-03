package com.baraa.masroof.diagnostics

import java.time.Instant

/**
 * One sanitized error event captured at runtime. The error category is a
 * short stable code; the message is a short Arabic description; the
 * timestamp is the wall-clock time. **Never** include stack traces,
 * transaction bodies, merchant names, amounts, account digits, API keys,
 * authorization headers, or AI request/response bodies.
 */
data class DiagnosticError(
    val timestampMillis: Long,
    val category: ErrorCategory,
    val message: String,
) {
    enum class ErrorCategory {
        SMS_PERMISSION_DENIED,
        SMS_PROVIDER_UNAVAILABLE,
        DATABASE_ERROR,
        MIGRATION_FAILURE,
        PARSER_EXCEPTION,
        DUPLICATE_ANALYSIS_EXCEPTION,
        AI_AUTH_FAILURE,
        AI_TIMEOUT,
        AI_MALFORMED_RESPONSE,
        NETWORK_UNAVAILABLE,
        KEYSTORE_FAILURE,
        UNKNOWN,
    }

    fun toJsonLine(): String {
        val safe = message.replace("\"", "\\\"").replace("\n", " ")
        return """{"ts":${'$'}timestampMillis,"category":"${'$'}category","message":"${'$'}safe"}"""
    }
}

/**
 * A small ring buffer of the most recent [limit] [DiagnosticError]
 * events. Used for "آخر خطأ منقح" in the diagnostics screen.
 *
 * Designed to be:
 *  - thread-safe (synchronized on `this`)
 *  - bounded (drops the oldest when full)
 *  - in-memory only (cleared on process death)
 */
class DiagnosticErrorLog(private val limit: Int = DEFAULT_LIMIT) {

    private val events: ArrayDeque<DiagnosticError> = ArrayDeque()

    @Synchronized
    fun record(category: DiagnosticError.ErrorCategory, message: String) {
        if (events.size >= limit) events.removeFirst()
        events.addLast(DiagnosticError(System.currentTimeMillis(), category, message))
    }

    @Synchronized
    fun snapshot(): List<DiagnosticError> = events.toList()

    @Synchronized
    fun clear() { events.clear() }

    companion object {
        const val DEFAULT_LIMIT: Int = 100
    }
}

/**
 * Single sanitized diagnostic snapshot used by the diagnostics screen
 * and the exportable report. All fields are safe to surface to the
 * user and to share externally.
 */
data class DiagnosticSnapshot(
    val appVersionName: String,
    val appVersionCode: Long,
    val databaseSchemaVersion: Int,
    val androidVersion: String,
    val deviceManufacturer: String,
    val deviceModel: String,
    val smsPermissionGranted: Boolean,
    val smsScannedCount: Long,
    val smsFinancialDetectedCount: Long,
    val smsParsedCount: Long,
    val smsParseFailureCount: Long,
    val savedTransactionsCount: Long,
    val exactDuplicatesCount: Long,
    val possibleDuplicatesCount: Long,
    val needsReviewCount: Long,
    val categoryCount: Long,
    val merchantMemoryCount: Long,
    val aiEnabled: Boolean,
    val aiProviderName: String?,
    val aiModelName: String?,
    val lastAiOutcome: String, // sanitized short label, never raw response
    val parserNames: List<String>,
    val ruleNames: List<String>,
    val recentErrors: List<DiagnosticError>,
    val buildTimestamp: String,
    val diagnosticReportVersion: String,
) {
    companion object {
        const val REPORT_VERSION: String = "v1"
        const val EMPTY_OUTCOME: String = "(لم يُجرَ اتصال بعد)"
    }
}