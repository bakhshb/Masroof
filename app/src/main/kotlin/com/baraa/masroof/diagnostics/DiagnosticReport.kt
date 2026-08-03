package com.baraa.masroof.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a [DiagnosticSnapshot] as a shareable, sanitized plain-text
 * report. The output is suitable for sending through Android Sharesheet
 * (or writing to a temporary file via FileProvider) without leaking any
 * transaction data, SMS body, API key, or authorization header.
 *
 * Two formats are supported:
 *  - [renderText]   — human-readable Arabic summary
 *  - [renderJson]   — machine-readable JSON dump
 */
object DiagnosticReport {

    private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun renderText(s: DiagnosticSnapshot): String {
        val sb = StringBuilder()
        sb.appendLine("تقرير تشخيص تطبيق مصروف")
        sb.appendLine("=".repeat(40))
        sb.appendLine("إصدار التقرير: ${'$'}{s.diagnosticReportVersion}")
        sb.appendLine("وقت الإنشاء: ${'$'}{DATE_FMT.format(Date())}")
        sb.appendLine()
        sb.appendLine("=== التطبيق ===")
        sb.appendLine("الإصدار: ${'$'}{s.appVersionName} (رقم ${'$'}{s.appVersionCode})")
        sb.appendLine("Android: ${'$'}{s.androidVersion}")
        sb.appendLine("الجهاز: ${'$'}{s.deviceManufacturer} ${'$'}{s.deviceModel}")
        sb.appendLine("قاعدة البيانات: الإصدار ${'$'}{s.databaseSchemaVersion}")
        sb.appendLine()
        sb.appendLine("=== الأذونات ===")
        val permStatus = if (s.smsPermissionGranted) "ممنوحة" else "مرفوضة"
        sb.appendLine("صلاحية قراءة الرسائل: ${'$'}permStatus")
        sb.appendLine()
        sb.appendLine("=== الرسائل والعمليات ===")
        sb.appendLine("رسائل SMS تم فحصها: ${'$'}{s.smsScannedCount}")
        sb.appendLine("رسائل مالية مكتشفة: ${'$'}{s.smsFinancialDetectedCount}")
        sb.appendLine("رسائل تم تحليلها: ${'$'}{s.smsParsedCount}")
        sb.appendLine("رسائل تعذر تحليلها: ${'$'}{s.smsParseFailureCount}")
        sb.appendLine("عمليات محفوظة: ${'$'}{s.savedTransactionsCount}")
        sb.appendLine("مكررات مطابقة: ${'$'}{s.exactDuplicatesCount}")
        sb.appendLine("قد تكون مكررة: ${'$'}{s.possibleDuplicatesCount}")
        sb.appendLine("تحتاج مراجعة: ${'$'}{s.needsReviewCount}")
        sb.appendLine()
        sb.appendLine("=== التصنيفات والتجار ===")
        sb.appendLine("عدد التصنيفات: ${'$'}{s.categoryCount}")
        sb.appendLine("عدد التجار المحفوظين: ${'$'}{s.merchantMemoryCount}")
        sb.appendLine()
        sb.appendLine("=== التصنيف الذكي ===")
        val aiStatus = if (s.aiEnabled) "مفعّل" else "معطّل"
        sb.appendLine("الحالة: ${'$'}aiStatus")
        val noAi = "(لا يوجد)"
        sb.appendLine("المزود: ${'$'}{s.aiProviderName ?: noAi}")
        sb.appendLine("النموذج: ${'$'}{s.aiModelName ?: noAi}")
        sb.appendLine("آخر نتيجة اتصال: ${'$'}{s.lastAiOutcome}")
        sb.appendLine()
        sb.appendLine("=== المحللات (الترتيب التنازلي حسب الأولوية) ===")
        for (name in s.parserNames) sb.appendLine(" - ${'$'}name")
        sb.appendLine()
        sb.appendLine("=== قواعد التصنيف (الترتيب حسب الأولوية) ===")
        for (name in s.ruleNames) sb.appendLine(" - ${'$'}name")
        sb.appendLine()
        sb.appendLine("=== الأخطاء المنقحة (الأحدث أولاً) ===")
        if (s.recentErrors.isEmpty()) {
            sb.appendLine("(لا توجد أخطاء مسجلة)")
        } else {
            for (e in s.recentErrors.asReversed()) {
                sb.appendLine(" - [${'$'}{DATE_FMT.format(Date(e.timestampMillis))}] ${'$'}{e.category}: ${'$'}{e.message}")
            }
        }
        sb.appendLine()
        sb.appendLine("(هذا التقرير منقّح — لا يحتوي على أسماء أو مبالغ أو أرقام بطاقات.)")
        return sb.toString()
    }

    fun renderJson(s: DiagnosticSnapshot): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"reportVersion\":\"").append(jsonEscape(s.diagnosticReportVersion)).append("\",")
        sb.append("\"generatedAt\":\"").append(jsonEscape(DATE_FMT.format(Date()))).append("\",")
        sb.append("\"buildTimestamp\":\"").append(jsonEscape(s.buildTimestamp)).append("\",")
        sb.append("\"appVersion\":\"").append(jsonEscape(s.appVersionName))
            .append("\",\"appVersionCode\":").append(s.appVersionCode).append(",")
        sb.append("\"databaseSchemaVersion\":").append(s.databaseSchemaVersion).append(",")
        sb.append("\"androidVersion\":\"").append(jsonEscape(s.androidVersion)).append("\",")
        sb.append("\"deviceManufacturer\":\"").append(jsonEscape(s.deviceManufacturer)).append("\",")
        sb.append("\"deviceModel\":\"").append(jsonEscape(s.deviceModel)).append("\",")
        sb.append("\"smsPermissionGranted\":").append(s.smsPermissionGranted).append(",")
        sb.append("\"counters\":{")
        sb.append("\"smsScanned\":").append(s.smsScannedCount).append(",")
        sb.append("\"smsFinancialDetected\":").append(s.smsFinancialDetectedCount).append(",")
        sb.append("\"smsParsed\":").append(s.smsParsedCount).append(",")
        sb.append("\"smsParseFailure\":").append(s.smsParseFailureCount).append(",")
        sb.append("\"savedTransactions\":").append(s.savedTransactionsCount).append(",")
        sb.append("\"exactDuplicates\":").append(s.exactDuplicatesCount).append(",")
        sb.append("\"possibleDuplicates\":").append(s.possibleDuplicatesCount).append(",")
        sb.append("\"needsReview\":").append(s.needsReviewCount).append(",")
        sb.append("\"categories\":").append(s.categoryCount).append(",")
        sb.append("\"merchantMemories\":").append(s.merchantMemoryCount)
        sb.append("},")
        sb.append("\"ai\":{")
        sb.append("\"enabled\":").append(s.aiEnabled).append(",")
        sb.append("\"providerName\":").append(jsonNullable(s.aiProviderName)).append(",")
        sb.append("\"modelName\":").append(jsonNullable(s.aiModelName)).append(",")
        sb.append("\"lastOutcome\":\"").append(jsonEscape(s.lastAiOutcome)).append("\"")
        sb.append("},")
        sb.append("\"parserNames\":[")
        s.parserNames.forEachIndexed { i, n ->
            if (i > 0) sb.append(",")
            sb.append("\"").append(jsonEscape(n)).append("\"")
        }
        sb.append("],")
        sb.append("\"ruleNames\":[")
        s.ruleNames.forEachIndexed { i, n ->
            if (i > 0) sb.append(",")
            sb.append("\"").append(jsonEscape(n)).append("\"")
        }
        sb.append("],")
        sb.append("\"recentErrors\":[")
        s.recentErrors.forEachIndexed { i, e ->
            if (i > 0) sb.append(",")
            sb.append(e.toJsonLine())
        }
        sb.append("]")
        sb.append("}")
        return sb.toString()
    }

    private fun jsonNullable(s: String?): String =
        if (s == null) "null" else "\"" + jsonEscape(s) + "\""

    private fun jsonEscape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        }
    }
}