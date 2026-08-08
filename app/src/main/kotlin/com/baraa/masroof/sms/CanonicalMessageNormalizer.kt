package com.baraa.masroof.sms

import java.security.MessageDigest

/**
 * Bump whenever the canonical structure rules change in a way that produces
 * a different signature for a structurally identical message.
 *
 * Every persisted [com.baraa.masroof.data.db.MessagePatternDefinitionEntity]
 * carries the version it was created under; runtime matching only considers
 * patterns at the current version. Older versions are surfaced as STALE.
 */
const val NORMALIZATION_VERSION: Int = 2

/**
 * Stable, deterministic structural interpretation of one SMS.
 *
 * The normalizer is the single source of truth shared by training and import.
 * It consumes a raw body OR a saved template (with `{PLACEHOLDER}` tokens) and
 * produces the same [CanonicalMessageStructure] regardless of input form.
 *
 * Training and import MUST call the same production normalizer.
 */
object CanonicalMessageNormalizer {

    /** Token classification for one labeled line value. */
    enum class ValueToken {
        TEXT,
        MONEY,
        CURRENCY,
        DATE,
        TIME,
        DATETIME,
        LAST4,
        REFERENCE,
        IBAN_LAST4,
        EMPTY,
    }

    /** One canonical line in the message structure. */
    data class CanonicalLine(
        /** Normalized label (Arabic letters folded, NFKC, lowercase). */
        val label: String,
        /** Token type for the value side of this line. */
        val valueToken: ValueToken,
        /** True when the line is informational and may be absent from instances. */
        val optional: Boolean,
        /** Original raw label, used only for diagnostic display (sanitized). */
        val originalLabel: String,
    )

    /** Ordered canonical structure of a message. */
    data class CanonicalMessageStructure(
        val lines: List<CanonicalLine>,
        val normalizationVersion: Int,
    ) {
        /** True when this line group is empty. */
        val isEmpty: Boolean get() = lines.isEmpty()

        /** Ordered labels (without token type or optional flag). */
        val labels: List<String> get() = lines.map { it.label }

        /**
         * Structural fingerprint: ordered `label|token|required` triples.
         * Independent of merchant / amount / date / time / last four / reference.
         *
         * Optional context lines (balance / due / limit) are excluded from
         * the fingerprint so a body that differs only by the presence of an
         * optional context line yields the same fingerprint. They are still
         * tracked on [lines] for diagnostics and matcher compatibility.
         */
        fun structuralFingerprint(): String =
            lines
                .filterNot { it.optional }
                .joinToString("\n") { "${it.label}|${it.valueToken.name}" }
    }

    /**
     * Normalize a raw SMS body into its canonical structure.
     * Tokenization of variable values is label-aware: amounts become
     * [ValueToken.MONEY], identifiers become [ValueToken.LAST4], free text
     * becomes [ValueToken.TEXT].
     */
    fun normalizeBody(body: String?): CanonicalMessageStructure {
        if (body.isNullOrBlank()) return CanonicalMessageStructure(emptyList(), NORMALIZATION_VERSION)
        return normalizeFromLines(
            com.baraa.masroof.transaction.LineBasedFieldParser.splitLines(body),
        )
    }

    /**
     * Normalize a saved template (which contains `{PLACEHOLDER}` tokens).
     * Same output as [normalizeBody] when applied to a body that the template
     * was built from.
     */
    fun normalizeTemplate(templateText: String?): CanonicalMessageStructure {
        if (templateText.isNullOrBlank()) return CanonicalMessageStructure(emptyList(), NORMALIZATION_VERSION)
        val parsed = templateText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val split = splitLabelValue(line) ?: return@map ParsedTemplateLine(
                    rawLabel = line,
                    label = normalizeLabel(line),
                    value = "",
                    isWallet = false,
                )
                ParsedTemplateLine(
                    rawLabel = split.first.trim(),
                    label = normalizeLabel(split.first),
                    value = split.second,
                    isWallet = false,
                )
            }
            .toList()
        return normalizeParsed(parsed)
    }

    private fun normalizeFromLines(
        lines: List<com.baraa.masroof.transaction.ParsedLine>,
    ): CanonicalMessageStructure {
        val parsed = lines.map { line ->
            val (labelPart, wallet) = MessageTypeCueCatalog.stripWalletSuffix(line.label.trim())
            ParsedTemplateLine(
                rawLabel = labelPart.trim(),
                label = normalizeLabel(labelPart),
                value = line.value,
                isWallet = wallet != null,
            )
        }
        return normalizeParsed(parsed)
    }

    private fun normalizeParsed(
        parsed: List<ParsedTemplateLine>,
    ): CanonicalMessageStructure {
        val out = ArrayList<CanonicalLine>(parsed.size)
        for (line in parsed) {
            if (line.label.isBlank()) continue
            val optional = PatternStructure.isOptionalContextAnchor(line.label)
            val token = classifyValue(line.rawLabel, line.label, line.value)
            out += CanonicalLine(
                label = line.label,
                valueToken = token,
                optional = optional,
                originalLabel = line.rawLabel,
            )
        }
        return CanonicalMessageStructure(out, NORMALIZATION_VERSION)
    }

    private data class ParsedTemplateLine(
        val rawLabel: String,
        val label: String,
        val value: String,
        val isWallet: Boolean,
    )

    private fun classifyValue(rawLabel: String, label: String, value: String): ValueToken {
        val trimmed = value.trim()
        // Use the *original* label (before normalization) for regex lookup so
        // Arabic letter folding like ة→ه does not break label-set matching.
        if (trimmed.isEmpty()) return ValueToken.EMPTY
        // Multi-placeholder values must classify as the strongest token.
        val placeholders = PLACEHOLDER_REGEX.findAll(trimmed).map { it.groupValues[1].uppercase() }.toList()
        if (placeholders.isNotEmpty()) {
            if (placeholders.any { it == "DATETIME" }) return ValueToken.DATETIME
            if (placeholders.any { it == "DATE" } && placeholders.any { it == "TIME" }) return ValueToken.DATETIME
            return placeholderToToken(placeholders.first())
        }
        // Label-driven tokenization: amount labels always produce MONEY.
        val monetaryRole = com.baraa.masroof.transaction.MonetaryFieldClassifier.classify(rawLabel)
        if (monetaryRole == com.baraa.masroof.transaction.MonetaryRole.TRANSACTION_AMOUNT) {
            return ValueToken.MONEY
        }
        val fields = CanonicalPatternFieldClassifier.classify(rawLabel)
        if (fields.any {
                it == com.baraa.masroof.data.db.PatternCanonicalField.IBAN_LAST4 ||
                    it == com.baraa.masroof.data.db.PatternCanonicalField.SOURCE_IBAN_LAST4 ||
                    it == com.baraa.masroof.data.db.PatternCanonicalField.DESTINATION_IBAN_LAST4
            }
        ) return ValueToken.IBAN_LAST4
        if (fields.any {
                it == com.baraa.masroof.data.db.PatternCanonicalField.CREDIT_CARD_LAST4 ||
                    it == com.baraa.masroof.data.db.PatternCanonicalField.DEBIT_CARD_LAST4 ||
                    it == com.baraa.masroof.data.db.PatternCanonicalField.ACCOUNT_LAST4 ||
                    it == com.baraa.masroof.data.db.PatternCanonicalField.SOURCE_ACCOUNT_LAST4 ||
                    it == com.baraa.masroof.data.db.PatternCanonicalField.DESTINATION_ACCOUNT_LAST4 ||
                    it == com.baraa.masroof.data.db.PatternCanonicalField.WALLET_LAST4
            }
        ) return ValueToken.LAST4
        if (
            com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_DATE in fields ||
            com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_TIME in fields
        ) return ValueToken.DATETIME
        if (com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_REFERENCE in fields) {
            return ValueToken.REFERENCE
        }
        if (com.baraa.masroof.data.db.PatternCanonicalField.CURRENCY in fields) return ValueToken.CURRENCY
        // Anything else is treated as free text (merchant, beneficiary, bank name…).
        return ValueToken.TEXT
    }

    private fun placeholderToToken(name: String): ValueToken = when (name) {
        "AMOUNT", "TRANSACTION_AMOUNT", "AVAILABLE_BALANCE", "TOTAL_DUE", "CREDIT_LIMIT" ->
            ValueToken.MONEY
        "CURRENCY" -> ValueToken.CURRENCY
        "DATE" -> ValueToken.DATE
        "TIME" -> ValueToken.TIME
        "DATETIME" -> ValueToken.DATETIME
        "CREDIT_CARD_LAST4", "DEBIT_CARD_LAST4", "ACCOUNT_LAST4",
        "WALLET_LAST4", "SOURCE_ACCOUNT_LAST4", "DESTINATION_ACCOUNT_LAST4",
        -> ValueToken.LAST4
        "IBAN_LAST4", "SOURCE_IBAN_LAST4", "DESTINATION_IBAN_LAST4" -> ValueToken.IBAN_LAST4
        "TRANSACTION_ID", "REFERENCE" -> ValueToken.REFERENCE
        else -> ValueToken.TEXT
    }

    private val PLACEHOLDER_REGEX = Regex("""\{\s*([A-Za-z0-9_]+)\s*\}""")

    /** NFKC + Arabic letter folding + lowercase. Stable across input forms. */
    fun normalizeLabel(raw: String): String {
        var s = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC)
        s = s.lowercase(java.util.Locale.ROOT)
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ة', 'ه').replace('ى', 'ي')
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun splitLabelValue(line: String): Pair<String, String>? {
        val separators = listOf("：", ":", "=")
        for (separator in separators) {
            val idx = line.indexOf(separator)
            if (idx <= 0) continue
            val label = line.substring(0, idx).trim()
            if (label.isNotBlank()) {
                var sepEnd = idx + separator.length
                while (sepEnd < line.length && line[sepEnd].isWhitespace()) sepEnd++
                return label to line.substring(sepEnd).trim()
            }
        }
        return null
    }
}

/**
 * Deterministic structural signature for one [CanonicalMessageStructure].
 *
 * Two structurally equivalent messages produce the same signature. Merchant
 * names, amounts, dates, times, last-fours, and references never participate
 * in the hash.
 */
object StructuralSignatureGenerator {

    /** Build a textual signature. */
    fun text(structure: CanonicalMessageNormalizer.CanonicalMessageStructure): String =
        "v${structure.normalizationVersion}|" + structure.structuralFingerprint()

    /** Stable hash of [text] suitable for `(senderProfileId, hash)` uniqueness. */
    fun hash(structure: CanonicalMessageNormalizer.CanonicalMessageStructure): String {
        val md = MessageDigest.getInstance("SHA-256")
        val raw = text(structure).toByteArray(Charsets.UTF_8)
        val digest = md.digest(raw)
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 32)
    }
}