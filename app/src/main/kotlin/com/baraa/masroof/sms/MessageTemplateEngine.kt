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
        // Expand compact inline fields (multiple label:value pairs on a single
        // line, e.g. English credit-card SMS) so each label is templatized on
        // its own. Each chunk produced by expandCompactInlineFields is either
        // a single "label: value" pair or a label-only fragment, matching the
        // pre-existing per-line assumption so original labels, separators and
        // ordering are preserved.
        val chunks = com.baraa.masroof.transaction.LineBasedFieldParser
            .expandCompactInlineFields(raw)

        val outLines = mutableListOf<String>()
        val placeholders = linkedSetOf<String>()

        // For multi-line bodies expandCompactInlineFields returns the whole body
        // as a single chunk; for compact-inline it returns per-label chunks.
        // In both cases each logical line (between newlines) carries at most
        // one label:value pair, so split per line before templatizing.
        val lines = chunks.flatMap { it.split('\n') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (orig in lines) {
            if (orig.isBlank()) continue
            val split = splitPreserve(orig)
            if (split == null) {
                val (cleaned, _) = MessageTypeCueCatalog.stripWalletSuffix(orig.trim())
                // Bank lines like "[البنك الرياض]" stay literal: the bank is
                // part of the pattern's identity (which counterparty bank)
                // and emitting {BANK_NAME} would break strict template
                // matching because the template label no longer matches the
                // body label. Users who want a generic bank pattern can edit
                // the bank line in the editor.
                outLines += cleaned.trim()
                continue
            }
            val (labelPart, sep, valuePart) = split
            // Trim the label so a trailing space before the colon
            // (e.g. "خصمت من حساب : 3002") doesn't leak into the template
            // as "خصمت من حساب : {ACCOUNT_LAST4}".
            val cleanedLabel = labelPart.trim()
            val placeholderValue = templatizeValue(cleanedLabel, valuePart, placeholders)
            outLines += "$cleanedLabel$sep$placeholderValue"
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
        "TRANSACTION_ID" -> """[A-Za-z0-9\-_/]{4,}"""
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
                // Compact English amount line: "of: 41.30 SAR At Amazon SA".
                // After money replace the tail still carries the merchant as
                // literal text; promote it so it is captured per-SMS, not baked
                // into the template.
                remaining = extractCompactMerchantTail(remaining) { put("MERCHANT") }
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
                // Distinct IBAN tokens (source vs destination) so a transfer can
                // carry both counterparty IBANs without overwriting.
                val ibanToken = when {
                    com.baraa.masroof.data.db.PatternCanonicalField
                        .SOURCE_IBAN_LAST4 in canonicalFields -> "SOURCE_IBAN_LAST4"
                    com.baraa.masroof.data.db.PatternCanonicalField
                        .DESTINATION_IBAN_LAST4 in canonicalFields -> "DESTINATION_IBAN_LAST4"
                    else -> "IBAN_LAST4"
                }
                val last4 = com.baraa.masroof.transaction.LineBasedFieldParser
                    .lastFourFromValue(remaining)
                if (last4 != null) {
                    remaining = LAST4.replace(remaining) { put(ibanToken) }
                } else if (remaining.isNotBlank()) {
                    // Non-digit value: promote to BENEFICIARY so a textual
                    // counterparty identifier is captured.
                    put("BENEFICIARY")
                    remaining = "{BENEFICIARY}"
                }
            }
            com.baraa.masroof.data.db.PatternCanonicalField.WALLET_LAST4 in canonicalFields -> {
                remaining = LAST4.replace(remaining) { put("WALLET_LAST4") }
            }
            canonicalFields.any {
                it == com.baraa.masroof.data.db.PatternCanonicalField.ACCOUNT_LAST4 ||
                    it == com.baraa.masroof.data.db.PatternCanonicalField.SOURCE_ACCOUNT_LAST4 ||
                    it == com.baraa.masroof.data.db.PatternCanonicalField.DESTINATION_ACCOUNT_LAST4
            } -> {
                // Distinct tokens for source vs destination so a transfer can
                // carry both account last4s in one template without overwriting.
                val accountToken = when {
                    com.baraa.masroof.data.db.PatternCanonicalField
                        .SOURCE_ACCOUNT_LAST4 in canonicalFields -> "SOURCE_ACCOUNT_LAST4"
                    com.baraa.masroof.data.db.PatternCanonicalField
                        .DESTINATION_ACCOUNT_LAST4 in canonicalFields -> "DESTINATION_ACCOUNT_LAST4"
                    else -> "ACCOUNT_LAST4"
                }
                val last4 = com.baraa.masroof.transaction.LineBasedFieldParser
                    .lastFourFromValue(remaining)
                if (last4 != null) {
                    remaining = LAST4.replace(remaining) { put(accountToken) }
                } else if (remaining.isNotBlank()) {
                    // Value-aware: bare من/إلى/حساب with a non-digit value is
                    // a person/beneficiary name, not an account. Promote to
                    // BENEFICIARY so the sender/recipient name is captured
                    // per-SMS at match time instead of being baked literal.
                    put("BENEFICIARY")
                    remaining = "{BENEFICIARY}"
                }
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
                if (LONG_REF.containsMatchIn(remaining) || remaining.isNotBlank()) {
                    // Capture the entire reference value (including any
                    // alphanumeric prefix like "2BTMS"), not just the digit
                    // run, so the reference is fully captured per-SMS.
                    put("TRANSACTION_ID")
                    remaining = "{TRANSACTION_ID}"
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

    /**
     * Compact-English merchant tail extractor. On a TRANSACTION_AMOUNT line
     * like `of: 41.30 SAR At Amazon SA`, after money replacement the value
     * ends with ` At <merchant>`. Promote that trailing merchant to the
     * provided token so it is captured per-SMS at match time instead of
     * being baked into the template as literal text. Returns the value
     * unchanged when no merchant cue is present.
     */
    private fun extractCompactMerchantTail(value: String, put: () -> String): String {
        // Search the ORIGINAL value (case-insensitive) for the " at " cue so
        // indices align with the unmodified string. foldArabic would collapse
        // whitespace and break index correspondence.
        val atIdx = value.indexOf(" at ", ignoreCase = true)
        if (atIdx < 0) return value
        val merchant = value.substring(atIdx + 4).trim()
        if (merchant.isEmpty()) return value
        // Single put() call: put() returns the braced token ({MERCHANT}) and
        // also registers it in the placeholders set as a side effect. Adding
        // extra braces around the return value produced {{MERCHANT}}} which
        // broke the validator's brace-balance check.
        val token = put()
        return value.substring(0, atIdx) + " " + token
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
