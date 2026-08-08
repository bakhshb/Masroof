package com.baraa.masroof.sms

import com.baraa.masroof.data.db.PatternVariantAnchorEntity
import java.util.regex.Pattern

/**
 * Deterministic matcher for approved SMS templates.
 *
 * Static anchors must match; placeholders capture variable values.
 * Optional context lines (balance / due) may be absent on either side.
 * Regex is used only for placeholder boundaries — not for re-inferring semantics.
 */
object TemplateMatcher {

    enum class FailureReason {
        EMPTY_INPUT,
        REQUIRED_LINE_MISSING,
        STATIC_TEXT_MISMATCH,
        LABEL_MISMATCH,
        LINE_SHAPE_MISMATCH,
        PLACEHOLDER_VALIDATION_MISMATCH,
    }

    data class TraceStep(
        val templateLineIndex: Int,
        val bodyLineIndex: Int?,
        val templateLine: String,
        val bodyLine: String?,
        val matched: Boolean,
        val reason: FailureReason? = null,
    )

    data class MatchResult(
        val matched: Boolean,
        /** Placeholder name → captured raw value from the SMS. */
        val values: Map<String, String>,
        /** Higher = more static anchors / placeholders agreed. */
        val score: Int,
        val failureReason: FailureReason? = null,
        val failedTemplateLine: String? = null,
        val failedBodyLine: String? = null,
        val trace: List<TraceStep> = emptyList(),
    )

    private val PLACEHOLDER = Regex("""\{([A-Z0-9_]+)\}""")

    private data class LineResult(
        val values: Map<String, String>? = null,
        val reason: FailureReason? = null,
    )

    fun match(
        templateText: String?,
        body: String?,
        anchors: List<PatternVariantAnchorEntity> = emptyList(),
    ): MatchResult {
        if (templateText.isNullOrBlank() || body.isNullOrBlank()) {
            return MatchResult(
                matched = false,
                values = emptyMap(),
                score = 0,
                failureReason = FailureReason.EMPTY_INPUT,
            )
        }
        val tLines = templateText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val bLines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (tLines.isEmpty() || bLines.isEmpty()) {
            return MatchResult(
                matched = false,
                values = emptyMap(),
                score = 0,
                failureReason = FailureReason.EMPTY_INPUT,
            )
        }

        val captured = linkedMapOf<String, String>()
        val trace = mutableListOf<TraceStep>()
        var bi = 0
        var score = 0
        for ((ti, tLine) in tLines.withIndex()) {
            var matched = false
            val startBi = bi
            var lastFailure = FailureReason.REQUIRED_LINE_MISSING
            var failedBodyLine: String? = null
            while (bi < bLines.size) {
                val bodyLine = bLines[bi]
                val lineResult = matchLine(tLine, bodyLine)
                if (lineResult.values != null) {
                    captured.putAll(lineResult.values)
                    score += 10 + lineResult.values.size
                    trace += TraceStep(ti, bi, tLine, bodyLine, matched = true)
                    matched = true
                    bi++
                    break
                }
                lastFailure = lineResult.reason ?: FailureReason.REQUIRED_LINE_MISSING
                failedBodyLine = bodyLine
                trace += TraceStep(ti, bi, tLine, bodyLine, matched = false, reason = lastFailure)
                if (isOptionalBodyLine(bodyLine)) {
                    bi++
                    continue
                }
                break
            }
            if (!matched) {
                if (isOptionalTemplateLine(tLine, anchors)) {
                    // Optional field absent in this SMS — stay at startBi.
                    bi = startBi
                    continue
                }
                return MatchResult(
                    matched = false,
                    values = emptyMap(),
                    score = 0,
                    failureReason = lastFailure,
                    failedTemplateLine = tLine,
                    failedBodyLine = failedBodyLine,
                    trace = trace,
                )
            }
        }
        return MatchResult(true, captured, score, trace = trace)
    }

    fun matches(
        templateText: String?,
        body: String?,
        anchors: List<PatternVariantAnchorEntity> = emptyList(),
    ): Boolean = match(templateText, body, anchors).matched

    private fun matchLine(templateLine: String, bodyLine: String): LineResult {
        val tSplit = splitPreserve(templateLine)
        val bSplit = splitPreserve(bodyLine)
        if (tSplit == null && bSplit == null) {
            val tFold = structuralFold(
                MessageTypeCueCatalog.stripWalletSuffix(templateLine).first,
            )
            val bFold = structuralFold(
                MessageTypeCueCatalog.stripWalletSuffix(bodyLine).first,
            )
            return if (tFold == bFold) {
                LineResult(values = emptyMap())
            } else {
                LineResult(reason = FailureReason.STATIC_TEXT_MISMATCH)
            }
        }
        if (tSplit == null || bSplit == null) {
            return LineResult(reason = FailureReason.LINE_SHAPE_MISMATCH)
        }
        val (tLabel, _, tValue) = tSplit
        val (bLabel, _, bValue) = bSplit
        val tLabelFold = structuralFold(
            MessageTypeCueCatalog.stripWalletSuffix(tLabel).first,
        )
        val bLabelFold = structuralFold(
            MessageTypeCueCatalog.stripWalletSuffix(bLabel).first,
        )
        if (tLabelFold != bLabelFold) {
            return LineResult(reason = FailureReason.LABEL_MISMATCH)
        }
        return captureValue(tValue, bValue)?.let { LineResult(values = it) }
            ?: LineResult(reason = FailureReason.PLACEHOLDER_VALIDATION_MISMATCH)
    }

    private fun captureValue(templateValue: String, bodyValue: String): Map<String, String>? {
        val tv = templateValue.trim()
        val bv = bodyValue.trim()
        val matcher = PLACEHOLDER.toPattern().matcher(tv)
        val names = mutableListOf<String>()
        val pattern = StringBuilder()
        var last = 0
        var found = false
        while (matcher.find()) {
            found = true
            pattern.append(staticRegex(tv.substring(last, matcher.start())))
            val name = matcher.group(1) ?: "MERCHANT"
            names += name
            pattern.append("(?<g${names.lastIndex}>${placeholderRegex(name)})")
            last = matcher.end()
        }
        if (!found) {
            return if (MessageTypeCueCatalog.foldArabic(tv) == MessageTypeCueCatalog.foldArabic(bv)) {
                emptyMap()
            } else {
                null
            }
        }
        pattern.append(staticRegex(tv.substring(last)))
        val full = runCatching {
            Pattern.compile("^\\s*$pattern\\s*$", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
        }.getOrNull() ?: return null
        // Named groups require a real Matcher; Java named groups can't start with digits — use g0, g1.
        val m = full.matcher(normalizeForValueMatch(bv))
        if (!m.matches()) return null
        val out = linkedMapOf<String, String>()
        for ((i, name) in names.withIndex()) {
            val v = runCatching { m.group("g$i") }.getOrNull()?.trim().orEmpty()
            if (v.isNotEmpty()) out[name] = v
        }
        return out
    }

    private fun placeholderRegex(name: String): String = when (name.uppercase()) {
        "AMOUNT", "AVAILABLE_BALANCE", "TOTAL_DUE", "CREDIT_LIMIT" ->
            """[-+]?\d{1,3}(?:,\d{3})+(?:\.\d+)?|[-+]?\d+(?:\.\d+)?"""
        "CURRENCY" -> """(?:SAR|SR|USD|EUR|ريال|ر\.س)"""
        "DATE" -> """(?:\d{1,2}[-/.\u060c]\d{1,2}[-/.\u060c]\d{2,4}|\d{4}[-/.\u060c]\d{1,2}[-/.\u060c]\d{1,2})"""
        "TIME" -> """(?:[01]?\d|2[0-3]):[0-5]\d(?::[0-5]\d)?"""
        "DATETIME" -> """(?:\d{1,2}[-/.\u060c]\d{1,2}[-/.\u060c]\d{2,4}|\d{4}[-/.\u060c]\d{1,2}[-/.\u060c]\d{1,2})\s+(?:[01]?\d|2[0-3]):[0-5]\d(?::[0-5]\d)?"""
        "CREDIT_CARD_LAST4", "DEBIT_CARD_LAST4", "ACCOUNT_LAST4",
        "IBAN_LAST4", "WALLET_LAST4", "SOURCE_ACCOUNT_LAST4", "DESTINATION_ACCOUNT_LAST4",
        -> """(?:[*xX•\-\s]*)?\d{4}"""
        "TRANSACTION_ID", "REFERENCE" -> """[A-Za-z0-9\-_/]{4,}"""
        "MERCHANT", "BENEFICIARY", "COUNTERPARTY", "BANK_NAME" -> """.{1,120}?"""
        else -> """.{0,120}?"""
    }

    private fun isOptionalTemplateLine(
        line: String,
        anchors: List<PatternVariantAnchorEntity>,
    ): Boolean {
        val normalized = PatternStructure.normalizeAnchor(PatternStructure.labelOf(line))
        anchors.firstOrNull { it.normalizedAnchor == normalized }?.let { return !it.required }
        val split = splitPreserve(line)
        if (split != null) {
            if (PatternStructure.isOptionalContextAnchor(split.first.trim())) return true
            val placeholders = PLACEHOLDER.findAll(split.third).map { it.groupValues[1].uppercase() }
            if (placeholders.all { it in OPTIONAL_PLACEHOLDERS } && placeholders.any()) return true
        }
        return false
    }

    private fun isOptionalBodyLine(line: String): Boolean {
        val split = splitPreserve(line) ?: return false
        return MessageTypeCueCatalog.isOptionalContextLabel(split.first.trim())
    }

    private val OPTIONAL_PLACEHOLDERS = setOf(
        "AVAILABLE_BALANCE", "TOTAL_DUE", "CREDIT_LIMIT", "CURRENCY",
    )

    private fun normalizeForValueMatch(raw: String): String {
        var s = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC)
        s = s.map { ch ->
            when (ch) {
                '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                '٫' -> '.'; '٬' -> ','
                else -> ch
            }
        }.joinToString("")
        return s.replace(Regex("""[\u00A0\u2007\u202F\u200E\u200F]+"""), " ")
            .replace(Regex("""[ \t]+"""), " ")
            .trim()
    }

    private fun structuralFold(raw: String): String =
        MessageTypeCueCatalog.foldArabic(normalizeForValueMatch(raw))
            .replace('：', ':')
            .replace('،', ',')
            .replace(Regex("""\s*([:=,])\s*"""), "$1")

    /** Static template text is exact apart from harmless spacing/punctuation. */
    private fun staticRegex(raw: String): String {
        val normalized = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC)
        val out = StringBuilder()
        var whitespace = false
        for (ch in normalized) {
            if (ch.isWhitespace() || ch in listOf('\u00A0', '\u2007', '\u202F', '\u200E', '\u200F')) {
                whitespace = true
                continue
            }
            if (whitespace) {
                out.append("""\s*""")
                whitespace = false
            }
            when (ch) {
                ':', '：' -> out.append("""[:：]""")
                ',', '،' -> out.append("""[,،]""")
                else -> out.append(Pattern.quote(ch.toString()))
            }
        }
        if (whitespace) out.append("""\s*""")
        return out.toString()
    }

    private fun splitPreserve(line: String): Triple<String, String, String>? {
        for (m in listOf("：", ":", "=")) {
            val idx = line.indexOf(m)
            if (idx <= 0) continue
            val label = line.substring(0, idx)
            var sepEnd = idx + m.length
            while (sepEnd < line.length && line[sepEnd].isWhitespace()) sepEnd++
            if (label.isNotBlank()) {
                return Triple(label, line.substring(idx, sepEnd), line.substring(sepEnd))
            }
        }
        return null
    }
}
