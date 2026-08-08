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
    private val AMOUNT_LABEL_REGEX = Regex("""^(بمبلغ|بقيمة|مبلغ|المبلغ|Amount|Transaction Amount|Purchase Amount|Transfer Amount|Payment Amount|Debited Amount|Credited Amount|Withdrawal Amount|مبلغ العملية|قيمة العملية|قيمة الشراء|قيمة التحويل|مبلغ التحويل|قيمة السحب|مبلغ السحب|المبلغ المخصوم|المبلغ المحول|المبلغ المستقطع|قيمة الخصم|مبلغ الخصم|قيمة الشحن|مبلغ الشحن|القيمة|of|القسط)$""", RegexOption.IGNORE_CASE)
    private val AMOUNT_LABELS = listOf(
        "Amount", "Transaction Amount", "Purchase Amount", "Transfer Amount", "Payment Amount",
        "Debited Amount", "Credited Amount", "Withdrawal Amount",
        "بمبلغ", "بقيمة", "مبلغ", "المبلغ", "مبلغ العملية", "قيمة العملية", "قيمة الشراء", "قيمة التحويل",
        "مبلغ التحويل", "قيمة السحب", "مبلغ السحب", "المبلغ المخصوم", "المبلغ المحول",
        "المبلغ المستقطع", "قيمة الخصم", "مبلغ الخصم", "قيمة الشحن", "مبلغ الشحن",
        "القيمة",
        // Compact English bank SMS: "... of : 33.03 SAR ..."
        "of",
        // Loan installment SMS
        "القسط",
    )
    init { }
    private val BALANCE_LABEL_REGEX = Regex(
        """^(الرصيد|الرصيد المتاح|الرصيد الحالي|المتبقي|المبلغ المتبقي|إجمالي المبلغ المستحق|المبلغ المستحق|الحد الائتماني|الحد الائتماني الجديد|حد ائتماني جديد|Available Balance|Available Balance is|Current Balance|Remaining Balance|Total Amount Due|Amount Due|Due Amount|Credit Limit|New Credit Limit)$""",
        RegexOption.IGNORE_CASE,
    )
    private val CARD_LABEL_REGEX = Regex("""^(بطاقة ائتمانية|البطاقة|بطاقة|Card|Credit Card|Debit Card|بطاقة مدى رقم|بطاقة مدى)$""", RegexOption.IGNORE_CASE)
    private val ACCOUNT_LABEL_REGEX = Regex(
        """^(رقم الحساب|الحساب|خصمت من حساب|أودعت إلى حساب|أودع إلى حساب|الى حساب|إلى حساب|الى|إلى|من حساب|من|Account|Debited from account|Credited to account)$""",
        RegexOption.IGNORE_CASE,
    )
    // "في" is datetime in Saudi SMS (في: 09:08 30-07-2026), not merchant.
    private val MERCHANT_LABEL_REGEX = Regex("""^(Merchant|التاجر|المستفيد|لدى|at|لـ|ل|الخدمة|Name|اسم المرسل)$""", RegexOption.IGNORE_CASE)
    private val BANK_ACCOUNT_LABEL_REGEX = Regex(
        """^(رقم الحساب|الحساب|خصمت من حساب|أودعت إلى حساب|أودع إلى حساب|الى حساب|إلى حساب|الى|إلى|من حساب|من|Account|Debited from account|Credited to account)$""",
        RegexOption.IGNORE_CASE,
    )
    private val CARD_DIGIT_REGEX = Regex("""^\*+(\d{4})$|^\d{4}$""")
    private val MONEY_REGEX = Regex(
        """^([A-Z]{2,3})?\s*([-+]?\d{1,3}(?:,\d{3})+(?:\.\d+)?|[-+]?\d+(?:\.\d+)?)\s*([A-Z]{2,3})?$""",
        RegexOption.IGNORE_CASE,
    )
    private val TIME_REGEX = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")
    private val DATE_REGEX = listOf(Regex("""(\d{4})[/-](\d{1,2})[/-](\d{1,2})"""), Regex("""(\d{1,2})[/-](\d{1,2})[/-](\d{4})"""))

    /**
     * Compact English SMS packs many `Label: value` pairs on one line.
     * Longer markers must be listed before shorter ones (`Available Balance is`
     * before `Available Balance`; word-boundary `at`/`of`/`on`).
     */
    private val INLINE_FIELD_MARKER = Regex(
        """(?i)(Available Balance is|Available Balance|Due Amount|Amount Due|Total Amount Due|Current Balance|Credit Card|Debit Card|بطاقة ائتمانية|بطاقة مدى رقم|بطاقة مدى|بمبلغ|المبلغ|مبلغ العملية|قيمة العملية|\bat|\bof|\bon)\s*:""",
    )

    fun splitLines(body: String): List<ParsedLine> {
        val rawLines = expandCompactInlineFields(body).flatMap { chunk ->
            chunk.split(Regex(LINE_BREAK_REGEX))
        }
        val lines = mutableListOf<ParsedLine>()
        for (raw in rawLines) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            val normalized = BankTextNormalizer.normalizeForParsing(trimmed)
            val split = splitLabelAndValue(normalized)
            if (split.first.isEmpty()) continue
            val originalSplit = splitLabelAndValue(trimmed)
            val originalValue = if (originalSplit.first.isEmpty()) split.second else originalSplit.second
            lines += ParsedLine(split.first, originalValue.trim())
        }
        return lines
    }

    /**
     * Turns a single-line multi-field English SMS into one line per field so
     * label-strict extraction can find amount / card / merchant / date.
     * Leaves already well-structured multi-line Arabic SMS unchanged.
     */
    internal fun expandCompactInlineFields(body: String): List<String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        val existingLabeledLines = trimmed.lines().count { line ->
            val t = line.trim()
            t.contains(':') || t.contains('：')
        }
        // Multi-line labeled Saudi SMS (Arabic POS, etc.) — do not rewrite.
        if (existingLabeledLines >= 3) return listOf(trimmed)

        val matches = INLINE_FIELD_MARKER.findAll(trimmed).toList()
        if (matches.size < 2) return listOf(trimmed)

        val out = mutableListOf<String>()
        val prefix = trimmed.substring(0, matches.first().range.first).trim()
        if (prefix.isNotEmpty()) out += prefix
        for (i in matches.indices) {
            val match = matches[i]
            val label = match.groupValues[1].trim()
            val valueStart = match.range.last + 1
            val valueEnd = if (i + 1 < matches.size) matches[i + 1].range.first else trimmed.length
            val value = trimmed.substring(valueStart, valueEnd).trim()
            out += "$label: $value"
        }
        return out
    }

    fun lastFourFromValue(value: String): String? {
        val trimmed = BankTextNormalizer.normalizeForParsing(value).trim()
        if (trimmed.isEmpty()) return null
        // Masked card/account forms: ****1234, ••••1234, XXXX1234
        Regex("""(?i)^(?:\*+|•+|x+)[- ]?(\d{4})$""").find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        // Exact four digits only — never scrape arbitrary trailing digits from amounts/refs.
        if (trimmed.length == 4 && trimmed.all { it.isDigit() }) return trimmed
        // Labeled values that end with a masked last-four, e.g. "حساب ****7271"
        Regex("""(?i)(?:\*+|•+|x+)[- ]?(\d{4})\s*$""").find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    fun parseLabeledMoneyField(lines: List<ParsedLine>, labels: List<Regex>): BigDecimal? {
        for (line in lines) {
            if (labels.none { it.matches(line.label) }) continue
            if (BALANCE_LABEL_REGEX.matches(line.label)) continue
            return parseMoney(line.value)?.first
        }
        return null
    }

    fun parseTransactionAmount(lines: List<ParsedLine>): BigDecimal? {
        for (line in lines) {
            if (!MonetaryFieldClassifier.isTransactionAmount(line.label)) continue
            val parsed = parseMoney(line.value)?.first
            if (parsed != null && parsed.signum() > 0) return parsed
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

    fun parseMoneyValue(value: String): BigDecimal? = parseMoney(value)?.first

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
    fun isAmountLabel(label: String): Boolean =
        MonetaryFieldClassifier.isTransactionAmount(label)

    fun containsAmountLabel(label: String): Boolean =
        MonetaryFieldClassifier.isTransactionAmount(label)
    fun balanceLabelRegex(): Regex = BALANCE_LABEL_REGEX
    fun cardLabelRegex(): Regex = CARD_LABEL_REGEX
    fun accountLabelRegex(): Regex = ACCOUNT_LABEL_REGEX
    fun merchantLabelRegex(): Regex = MERCHANT_LABEL_REGEX
    fun bankAccountLabelRegex(): Regex = BANK_ACCOUNT_LABEL_REGEX

    fun splitLabelAndValue(line: String): Pair<String, String> {
    for (separator in LABEL_SEPARATORS.split("|").filter { it.isNotEmpty() }) {
        val idx = line.indexOf(separator); if (idx in 1..(line.length - 1)) return line.substring(0, idx).trim() to line.substring(idx + separator.length).trim()
    }
    val colon = line.indexOf(':')
    if (colon in 1..(line.length - 1)) return line.substring(0, colon).trim() to line.substring(colon + 1).trim()
    // Lines without any separator are treated as label-only entries. This
    // preserves multi-word labels like "شراء عبر الانترنت" so type-detection
    // can match them later.
    return line.trim() to ""
}

    /** Parses a money literal; returns the BigDecimal plus any captured currency code. */
    private fun parseMoney(value: String): Pair<BigDecimal, String>? {
        val trimmed = BankTextNormalizer.normalizeForParsing(value).trim()
        if (trimmed.isEmpty()) return null
        val match = MONEY_REGEX.find(trimmed) ?: return null
        val prefix = match.groupValues[1].takeIf { it.isNotEmpty() } ?: ""
        val amountText = match.groupValues[2].replace(",", "")
        val suffix = match.groupValues[3].takeIf { it.isNotEmpty() } ?: ""
        val amount = runCatching { BigDecimal(amountText) }.getOrNull() ?: return null
        return amount to (prefix.ifEmpty { suffix })
    }
}