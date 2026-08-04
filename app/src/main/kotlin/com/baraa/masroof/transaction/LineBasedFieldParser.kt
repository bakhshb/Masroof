package com.baraa.masroof.transaction

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/** Normalized line used for strict, label-based parsing. */
data class ParsedLine(val label: String, val value: String)

/**
 * Line-based, label-strict SMS field extractor. Every helper inspects ONLY the
 * normalized line it is asked to parse and never falls back to scanning unrelated lines.
 */
object LineBasedFieldParser {
    private const val LINE_BREAK_REGEX = "[\n\r]+"
    private const val LABEL_SEPARATORS = ":：:：=: =|:"
    private val AMOUNT_LABEL_REGEX = Regex("""^(بمبلغ|مبلغ|Amount|Transaction Amount|Purchase Amount|Transfer Amount|Payment Amount|مبلغ العملية|قيمة العملية|قيمة الشراء|قيمة التحويل)$""", RegexOption.IGNORE_CASE)
    private val AMOUNT_LABELS = listOf("Amount", "Transaction Amount", "Purchase Amount", "Transfer Amount", "Payment Amount", "بمبلغ", "بقيمة", "قيمة العملية", "قيمة الشراء", "قيمة التحويل")
    init { }
    private val BALANCE_LABEL_REGEX = Regex("""^(الرصيد|الرصيد المتاح|الرصيد الحالي|المتبقي|إجمالي المبلغ المستحق|المبلغ المستحق|الحد الائتماني|Available Balance|Current Balance|Remaining Balance|Total Amount Due|Amount Due|Credit Limit)$""", RegexOption.IGNORE_CASE)
    private val CARD_LABEL_REGEX = Regex("""^(بطاقة ائتمانية|البطاقة|بطاقة|Card|Credit Card)$""", RegexOption.IGNORE_CASE)
    private val ACCOUNT_LABEL_REGEX = Regex("""^(رقم الحساب|الحساب|Account|Account Number|IBAN|رقم الآيبان)$""", RegexOption.IGNORE_CASE)
    private val MERCHANT_LABEL_REGEX = Regex("""^(Merchant|التاجر|المستفيد|لدى|من\s+الجهة)$""", RegexOption.IGNORE_CASE)
    private val CARD_DIGIT_REGEX = Regex("""^\*+(\d{4})$|^\d{4}$""")
    private val MONEY_REGEX = Regex("""^([A-Z]{2,3})?\s*([-+]?\d{1,3}(?:,\d{3})+(?:\.\d+)?|[-+]?\d+(?:\.\d+)?)\s*([A-Z]{2,3})?$""")
    private val TIME_REGEX = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")
    private val DATE_REGEX = listOf(Regex("""(\d{4})[/-](\d{1,2})[/-](\d{1,2})"""), Regex("""(\d{1,2})[/-](\d{1,2})[/-](\d{4})"""))

    fun splitLines(body: String): List<ParsedLine> {
        val rawLines = body.split(Regex(LINE_BREAK_REGEX))
        val lines = mutableListOf<ParsedLine>()
        for (raw in rawLines) {
            val normalized = BankTextNormalizer.normalizeForParsing(raw).trim()
            if (normalized.isEmpty()) continue
            val split = splitLabelAndValue(normalized)
            if (split.first.isEmpty() || split.second.isEmpty()) continue
            lines += ParsedLine(split.first, split.second)
        }
        return lines
    }

    fun parseLabeledMoneyField(lines: List<ParsedLine>, labels: List<Regex>): BigDecimal? {
        for (line in lines) {
            if (labels.none { it.matches(line.label) }) continue
            if (BALANCE_LABEL_REGEX.matches(line.label)) continue
            return parseMoney(line.value)?.first
        }
        return null
    }

    fun parseLabeledMoneyFieldExact(lines: List<ParsedLine>, vararg exactLabels: String): BigDecimal? {
        val compiled = exactLabels.map { Regex(Regex.escape(it)) }
        return parseLabeledMoneyField(lines, compiled)
    }

    fun parseExcludedMoneyField(lines: List<ParsedLine>): BigDecimal? {
        for (line in lines) {
            if (BALANCE_LABEL_REGEX.matches(line.label)) {
                return parseMoney(line.value)?.first
            }
        }
        return null
    }

    fun parseLastFourField(lines: List<ParsedLine>): String? {
        for (line in lines) {
            if (CARD_LABEL_REGEX.matches(line.label) || ACCOUNT_LABEL_REGEX.matches(line.label)) {
                val trimmed = line.value.replace("-", "").trim()
                if (CARD_DIGIT_REGEX.matches(trimmed)) return trimmed.takeLast(4)
            }
        }
        return null
    }

    fun parseDateTimeField(lines: List<ParsedLine>): Pair<LocalDate?, LocalTime?> {
        for (line in lines) {
            for (pattern in DATE_REGEX) {
                val match = pattern.find(line.value) ?: continue
                val a = match.groupValues[1]; val b = match.groupValues[2]; val c = match.groupValues[3]
                val date = runCatching { if (a.length == 4) LocalDate.of(a.toInt(), b.toInt(), c.toInt()) else LocalDate.of(c.toInt(), b.toInt(), a.toInt()) }.getOrNull() ?: continue
                val time = TIME_REGEX.find(line.value)?.let { m -> runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues.getOrNull(3)?.toIntOrNull() ?: 0) }.getOrNull() }
                return date to time
            }
        }
        return null to null
    }

    fun parseTextField(lines: List<ParsedLine>, labels: List<Regex>): String? {
        for (line in lines) if (labels.any { it.matches(line.label) }) return line.value
        return null
    }

    fun amountLabelRegex(): Regex = AMOUNT_LABEL_REGEX
    fun isAmountLabel(label: String): Boolean {
    val normalized = java.text.Normalizer.normalize(label, java.text.Normalizer.Form.NFKC).lowercase(java.util.Locale.ROOT).trim()
    val labels = AMOUNT_LABELS.map { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFKC).lowercase(java.util.Locale.ROOT).trim() }
    return labels.any { it == normalized }
}
fun containsAmountLabel(label: String): Boolean {
    val trim = label.trim()
    if (AMOUNT_LABELS.contains(trim)) return true
    val normalized = java.text.Normalizer.normalize(trim, java.text.Normalizer.Form.NFKC).lowercase(java.util.Locale.ROOT)
    val labels = AMOUNT_LABELS.map { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFKC).lowercase(java.util.Locale.ROOT) }
    return labels.any { it == normalized }
}
    fun balanceLabelRegex(): Regex = BALANCE_LABEL_REGEX
    fun cardLabelRegex(): Regex = CARD_LABEL_REGEX
    fun accountLabelRegex(): Regex = ACCOUNT_LABEL_REGEX
    fun merchantLabelRegex(): Regex = MERCHANT_LABEL_REGEX

    fun splitLabelAndValue(line: String): Pair<String, String> {
        for (separator in LABEL_SEPARATORS.split("|").filter { it.isNotEmpty() }) {
            val idx = line.indexOf(separator); if (idx in 1..(line.length - 1)) return line.substring(0, idx).trim() to line.substring(idx + separator.length).trim()
        }
        return "" to ""
    }

    /** Parses a money literal; returns the BigDecimal plus any captured currency code. */
    private fun parseMoney(value: String): Pair<BigDecimal, String>? {
        val trimmed = value.trim(); if (trimmed.isEmpty()) return null
        val match = MONEY_REGEX.find(trimmed) ?: return null
        val prefix = match.groupValues[1].takeIf { it.isNotEmpty() } ?: ""
        val amountText = match.groupValues[2].replace(",", "")
        val suffix = match.groupValues[3].takeIf { it.isNotEmpty() } ?: ""
        val amount = runCatching { BigDecimal(amountText) }.getOrNull() ?: return null
        return amount to (prefix.ifEmpty { suffix })
    }
}