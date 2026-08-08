package com.baraa.masroof.sms

import com.baraa.masroof.transaction.TransactionType
import java.util.regex.Pattern

data class BuiltMessageTemplate(
    /** Human-readable structural template with {PLACEHOLDER} tokens. */
    val templateText: String,
    val placeholders: List<String>,
    val transactionType: TransactionType?,
    val direction: String?,
    val channel: String?,
    val displayName: String,
    /** Machine signature derived from the same SMS (for DB uniqueness / legacy). */
    val signature: String,
)

/**
 * Builds and matches deterministic SMS structural templates.
 * Does not use similarity, embeddings, or nearest-neighbor selection.
 */
object MessageTemplateEngine {

    private val PLACEHOLDER = Regex("""\{([A-Z0-9_]+)\}""")
    private val MONEY_NUM = Regex(
        """[-+]?\d{1,3}(?:,\d{3})+(?:\.\d+)?|[-+]?\d+(?:\.\d+)?""",
    )
    private val CURRENCY_TOKEN = Regex("""(?i)\b(SAR|SR|USD|EUR|ريال|ر\.س)\b""")
    private val TIME = Regex("""\b([01]?\d|2[0-3]):[0-5]\d(?::[0-5]\d)?\b""")
    private val DATE = Regex(
        """\b(\d{1,2}[-/.\u060c]\d{1,2}[-/.\u060c]\d{2,4}|\d{4}[-/.\u060c]\d{1,2}[-/.\u060c]\d{1,2})\b""",
    )
    private val LAST4 = Regex("""(?<!\d)\d{4}(?!\d)""")
    private val LONG_REF = Regex("""\d{5,}""")

    fun buildFromSms(body: String?): BuiltMessageTemplate {
        val raw = body.orEmpty().trim()
        if (raw.isEmpty()) {
            return BuiltMessageTemplate(
                templateText = "",
                placeholders = emptyList(),
                transactionType = null,
                direction = null,
                channel = null,
                displayName = "نمط رسالة",
                signature = "empty",
            )
        }
        val cue = MessageTypeCueCatalog.detect(raw)
        val originalLines = raw.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() }.toList()

        val outLines = mutableListOf<String>()
        val placeholders = linkedSetOf<String>()

        for (orig in originalLines) {
            val split = splitPreserve(orig)
            if (split == null) {
                val (cleaned, _) = MessageTypeCueCatalog.stripWalletSuffix(orig.trim())
                outLines += cleaned.trim()
                continue
            }
            val (labelPart, sep, valuePart) = split
            val placeholderValue = templatizeValue(labelPart.trim(), valuePart, placeholders)
            outLines += "$labelPart$sep$placeholderValue"
        }

        val templateText = outLines.joinToString("\n")
        return BuiltMessageTemplate(
            templateText = templateText,
            placeholders = placeholders.toList(),
            transactionType = cue.transactionType,
            direction = cue.direction,
            channel = cue.channel,
            displayName = SmsStructureNormalizer.friendlyNameHint(raw),
            signature = SmsStructureNormalizer.signatureFromBody(raw),
        )
    }

    fun matches(templateText: String?, body: String?): Boolean =
        TemplateMatcher.matches(templateText, body)

    private fun isOptionalBodyLine(line: String): Boolean {
        val split = splitPreserve(line) ?: return false
        return MessageTypeCueCatalog.isOptionalContextLabel(split.first.trim())
    }

    private fun lineMatches(templateLine: String, bodyLine: String): Boolean {
        val tSplit = splitPreserve(templateLine)
        val bSplit = splitPreserve(bodyLine)
        if (tSplit == null && bSplit == null) {
            return MessageTypeCueCatalog.foldArabic(templateLine) ==
                MessageTypeCueCatalog.foldArabic(
                    MessageTypeCueCatalog.stripWalletSuffix(bodyLine).first,
                )
        }
        if (tSplit == null || bSplit == null) return false
        val (tLabel, _, tValue) = tSplit
        val (bLabel, _, bValue) = bSplit
        if (MessageTypeCueCatalog.foldArabic(tLabel) != MessageTypeCueCatalog.foldArabic(bLabel)) {
            return false
        }
        return valueMatchesTemplate(tValue, bValue)
    }

    private fun valueMatchesTemplate(templateValue: String, bodyValue: String): Boolean {
        // Build regex from template value: literals escaped, placeholders typed.
        val tv = templateValue.trim()
        val matcher = PLACEHOLDER.toPattern().matcher(tv)
        val pattern = StringBuilder()
        var last = 0
        var found = false
        while (matcher.find()) {
            found = true
            pattern.append(Pattern.quote(tv.substring(last, matcher.start())))
            pattern.append(placeholderRegex(matcher.group(1) ?: "MERCHANT"))
            last = matcher.end()
        }
        if (!found) {
            return MessageTypeCueCatalog.foldArabic(tv) == MessageTypeCueCatalog.foldArabic(bodyValue.trim())
        }
        pattern.append(Pattern.quote(tv.substring(last)))
        val full = Pattern.compile("^\\s*$pattern\\s*$", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
        return full.matcher(bodyValue.trim()).matches()
    }

    private fun placeholderRegex(name: String): String = when (name) {
        "AMOUNT", "AVAILABLE_BALANCE", "TOTAL_DUE" ->
            """[-+]?\d{1,3}(?:,\d{3})+(?:\.\d+)?|[-+]?\d+(?:\.\d+)?"""
        "CURRENCY" -> """(?:SAR|SR|USD|EUR|ريال|ر\.س)"""
        "DATE" -> """\d{1,2}[-/.\u060c]\d{1,2}[-/.\u060c]\d{2,4}|\d{4}[-/.\u060c]\d{1,2}[-/.\u060c]\d{1,2}"""
        "TIME" -> """(?:[01]?\d|2[0-3]):[0-5]\d(?::[0-5]\d)?"""
        "CREDIT_CARD_LAST4", "DEBIT_CARD_LAST4", "ACCOUNT_LAST4",
        "IBAN_LAST4", "WALLET_LAST4",
        -> """\d{4}"""
        "TRANSACTION_ID" -> """[A-Za-z0-9\-]{4,}"""
        "MERCHANT", "BENEFICIARY", "BANK_NAME" -> """.{1,120}?"""
        else -> """.{0,120}?"""
    }.let { "(?:$it)" }

    private fun templatizeValue(
        label: String,
        value: String,
        placeholders: MutableSet<String>,
    ): String {
        val foldedLabel = MessageTypeCueCatalog.foldArabic(label)
        val canonicalFields = CanonicalPatternFieldClassifier.classify(label)
        var remaining = value
        val controlled = MessageTypeCueCatalog.detectFromFragment(value)
        if (controlled != null && controlled.typeToken != "TYPE:UNKNOWN" &&
            MessageTypeCueCatalog.foldArabic(value).length >= 4
        ) {
            // Type words in values are structural constants (e.g. Type: تحويل صادر).
            return value.trim()
        }

        fun put(token: String): String {
            placeholders += token
            return "{$token}"
        }

        when {
            com.baraa.masroof.data.db.PatternCanonicalField.CARD_AMOUNT_DUE in canonicalFields -> {
                remaining = replaceMoneyKeepingCurrency(remaining) { put("TOTAL_DUE") }
            }
            com.baraa.masroof.data.db.PatternCanonicalField.AVAILABLE_BALANCE in canonicalFields -> {
                remaining = replaceMoneyKeepingCurrency(remaining) { put("AVAILABLE_BALANCE") }
            }
            com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_AMOUNT in canonicalFields -> {
                remaining = replaceMoneyKeepingCurrency(remaining) { put("AMOUNT") }
            }
            com.baraa.masroof.data.db.PatternCanonicalField.CREDIT_CARD_LAST4 in canonicalFields -> {
                remaining = LAST4.replace(remaining) { put("CREDIT_CARD_LAST4") }
            }
            com.baraa.masroof.data.db.PatternCanonicalField.DEBIT_CARD_LAST4 in canonicalFields -> {
                remaining = LAST4.replace(remaining) { put("DEBIT_CARD_LAST4") }
            }
            canonicalFields.any {
                it == com.baraa.masroof.data.db.PatternCanonicalField.IBAN_LAST4 ||
                    it == com.baraa.masroof.data.db.PatternCanonicalField.SOURCE_IBAN_LAST4 ||
                    it == com.baraa.masroof.data.db.PatternCanonicalField.DESTINATION_IBAN_LAST4
            } -> {
                remaining = LAST4.replace(remaining) { put("IBAN_LAST4") }
            }
            com.baraa.masroof.data.db.PatternCanonicalField.WALLET_LAST4 in canonicalFields -> {
                remaining = LAST4.replace(remaining) { put("WALLET_LAST4") }
            }
            canonicalFields.any {
                it == com.baraa.masroof.data.db.PatternCanonicalField.ACCOUNT_LAST4 ||
                    it == com.baraa.masroof.data.db.PatternCanonicalField.SOURCE_ACCOUNT_LAST4 ||
                    it == com.baraa.masroof.data.db.PatternCanonicalField.DESTINATION_ACCOUNT_LAST4
            } -> {
                remaining = LAST4.replace(remaining) { put("ACCOUNT_LAST4") }
            }
            com.baraa.masroof.data.db.PatternCanonicalField.MERCHANT in canonicalFields -> {
                placeholders += "MERCHANT"
                remaining = "{MERCHANT}"
            }
            com.baraa.masroof.data.db.PatternCanonicalField.BENEFICIARY in canonicalFields -> {
                placeholders += "BENEFICIARY"
                remaining = "{BENEFICIARY}"
            }
            isBankLabel(foldedLabel) -> {
                placeholders += "BANK_NAME"
                remaining = "{BANK_NAME}"
            }
            com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_REFERENCE in canonicalFields -> {
                remaining = if (LONG_REF.containsMatchIn(remaining)) {
                    LONG_REF.replace(remaining) { put("TRANSACTION_ID") }
                } else {
                    placeholders += "TRANSACTION_ID"
                    "{TRANSACTION_ID}"
                }
            }
            com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_DATE in canonicalFields ||
                com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_TIME in canonicalFields -> {
                if (com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_TIME in canonicalFields) {
                    remaining = TIME.replace(remaining) { put("TIME") }
                }
                if (com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_DATE in canonicalFields) {
                    remaining = DATE.replace(remaining) { put("DATE") }
                }
            }
            else -> {
                // Unknown labels remain literal. Arbitrary numbers must never
                // become transaction amounts or account identifiers.
            }
        }
        return remaining.trim()
    }

    private fun replaceMoneyKeepingCurrency(value: String, amountToken: () -> String): String {
        return MONEY_NUM.replace(value) { amountToken() }
    }

    private fun isCreditCardLabel(n: String) =
        "ائتمان" in n || "credit" in n

    private fun isDebitCardLabel(n: String) =
        "مدى" in n || "debit" in n || "بطاقه خصم" in n || "بطاقة خصم" in n

    /** Account / bank-account labels — never treat bare «خصم» as a debit card. */
    private fun isAccountLabel(n: String) =
        "حساب" in n || "account" in n

    private fun isIbanLabel(n: String) =
        "ايبان" in n || "iban" in n

    private fun isWalletLabel(n: String) =
        "محفظه" in n || "محفظة" in n || "wallet" in n

    private fun isMerchantLabel(n: String) =
        n == "لدي" || n == "لدى" || "تاجر" in n || "merchant" in n || n == "at" || n == "ل"

    private fun isBeneficiaryLabel(n: String) =
        "مستفيد" in n || "beneficiary" in n

    private fun isBankLabel(n: String) =
        "بنك" in n || "bank" in n || "مؤسسه" in n || "institution" in n

    /**
     * Splits `label[sep]value` preserving the separator characters as they appear.
     */
    private fun splitPreserve(line: String): Triple<String, String, String>? {
        val markers = listOf("：", ":", "=")
        for (m in markers) {
            val idx = line.indexOf(m)
            if (idx <= 0) continue
            val label = line.substring(0, idx)
            var sepEnd = idx + m.length
            while (sepEnd < line.length && line[sepEnd].isWhitespace()) sepEnd++
            val sep = line.substring(idx, sepEnd)
            val value = line.substring(sepEnd)
            if (label.isNotBlank()) return Triple(label, sep, value)
        }
        return null
    }
}
