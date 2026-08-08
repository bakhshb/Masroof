package com.baraa.masroof.sms

import com.baraa.masroof.transaction.MoneyFlowDirection
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.transaction.TransactionTypeTaxonomy

/** Structural helpers shared by discovery, migration, and matching. */
object PatternStructure {

    /**
     * Bank-agnostic semantic key for a [MessagePatternFamilyEntity].
     *
     * Built from approved financial meaning — never from a single structural
     * label. Two structurally different POS templates belong to one family if
     * they share the same `(transactionType, channel, direction, paymentInstrument)`.
     *
     * Pure functions here must never depend on Room, Compose, or Android APIs.
     */
    fun familyKey(
        transactionType: TransactionType?,
        direction: MoneyFlowDirection?,
        channel: String?,
        paymentInstrument: PaymentInstrument,
    ): String {
        val type = transactionType?.name ?: "UNKNOWN"
        val dir = (direction ?: MoneyFlowDirection.NONE).name
        val ch = channel?.trim().orEmpty().uppercase().ifBlank { "NONE" }
        val pi = paymentInstrument.name
        return "$type|$dir|$ch|$pi"
    }

    /**
     * Family key from a free-form template/body. Direction is derived from the
     * transaction type taxonomy; payment instrument is inferred from the first
     * typed-identifier line in the structure.
     */
    fun familyKey(
        structure: CanonicalMessageNormalizer.CanonicalMessageStructure,
        transactionType: TransactionType?,
        channel: String?,
    ): String {
        val direction = transactionType?.let { TransactionTypeTaxonomy.directionOf(it) }
        val instrument = detectPaymentInstrument(structure)
        return familyKey(transactionType, direction, channel, instrument)
    }

    /** Payment-instrument channel inferred from identifier labels in the structure. */
    enum class PaymentInstrument {
        DEBIT_CARD,
        CREDIT_CARD,
        ACCOUNT,
        IBAN,
        WALLET,
        UNKNOWN,
    }

    fun detectPaymentInstrument(
        structure: CanonicalMessageNormalizer.CanonicalMessageStructure,
    ): PaymentInstrument {
        for (line in structure.lines) {
            when (line.valueToken) {
                CanonicalMessageNormalizer.ValueToken.IBAN_LAST4 -> return PaymentInstrument.IBAN
                CanonicalMessageNormalizer.ValueToken.LAST4 -> {
                    val label = line.label
                    if ("ائتمان" in label || "credit" in label) return PaymentInstrument.CREDIT_CARD
                    if ("مدى" in label || "debit" in label) return PaymentInstrument.DEBIT_CARD
                    if ("محفظه" in label || "محفظة" in label || "wallet" in label) return PaymentInstrument.WALLET
                    if ("حساب" in label || "account" in label) return PaymentInstrument.ACCOUNT
                    return PaymentInstrument.ACCOUNT
                }
                else -> continue
            }
        }
        return PaymentInstrument.UNKNOWN
    }

    /** Normalizes literal labels/anchors without classifying the transaction. */
    fun normalizeAnchor(raw: String): String = CanonicalMessageNormalizer.normalizeLabel(raw)

    fun labelOf(line: String): String = split(line)?.first ?: line.trim()

    fun split(line: String): Triple<String, String, String>? {
        for (separator in SEPARATORS) {
            val index = line.indexOf(separator)
            if (index <= 0) continue
            var end = index + separator.length
            while (end < line.length && line[end].isWhitespace()) end++
            val label = line.substring(0, index).trim()
            if (label.isNotBlank()) return Triple(label, line.substring(index, end), line.substring(end))
        }
        return null
    }

    /** Contextual balances are optional, never a pattern identity by themselves. */
    fun isOptionalContextAnchor(raw: String): Boolean {
        val label = normalizeAnchor(raw)
        return listOf(
            "الرصيد المتاح", "الرصيد", "اجمالي المبلغ المستحق", "المبلغ المستحق",
            "available balance", "balance", "total due", "credit limit",
        ).any { it == label || label.contains(it) }
    }

    /**
     * @deprecated Family identity is now semantic (TransactionType + direction +
     * payment instrument + channel). This single-label structural family key
     * caused duplicate top-level cards; retained only as a no-op alias for
     * backwards-compatibility in callers that should be migrated.
     */
    @Deprecated("Use semantic familyKey with structured inputs.")
    @Suppress("UNUSED_PARAMETER")
    fun legacyStructuralFamilyKey(templateText: String?, signature: String): String =
        signature.substringBefore("#revision:")

    fun anchorsFromTemplate(templateText: String?): List<Pair<String, Boolean>> =
        templateText.orEmpty().lineSequence()
            .map { labelOf(it) }
            .map(::normalizeAnchor)
            .filter { it.isNotBlank() }
            .distinct()
            .map { it to !isOptionalContextAnchor(it) }
            .toList()

    private val SEPARATORS = listOf("：", ":", "=")
}