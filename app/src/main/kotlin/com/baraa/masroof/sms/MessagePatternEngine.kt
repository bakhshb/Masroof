package com.baraa.masroof.sms

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.data.db.PatternFieldDefinitionEntity
import com.baraa.masroof.data.db.PatternFieldRole
import com.baraa.masroof.data.db.PatternValueType
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.IdentifierRole
import com.baraa.masroof.transaction.LineBasedFieldParser
import com.baraa.masroof.transaction.ParsedIdentifierEvidence
import com.baraa.masroof.transaction.ParsedTransaction
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import java.math.BigDecimal
import java.util.Locale

data class PatternMatchResult(
    val pattern: MessagePattern,
    val exactSignature: Boolean,
)

/**
 * Matches a body against stored message pattern definitions for one sender.
 */
object MessagePatternMatcher {
    fun match(
        body: String?,
        patterns: List<MessagePattern>,
    ): PatternMatchResult? {
        if (body.isNullOrBlank() || patterns.isEmpty()) return null
        val signature = SmsStructureNormalizer.signatureFromBody(body)
        val importable = patterns.filter {
            it.definition.status == MessagePatternStatus.APPROVED ||
                it.definition.status == MessagePatternStatus.DEPRECATED
        }
        val exact = importable.firstOrNull { it.definition.normalizedSignature == signature }
        if (exact != null) return PatternMatchResult(exact, exactSignature = true)

        val ignored = patterns.filter { it.definition.status == MessagePatternStatus.IGNORED }
        if (ignored.any { it.definition.normalizedSignature == signature }) {
            return PatternMatchResult(ignored.first { it.definition.normalizedSignature == signature }, true)
        }
        return null
    }

    fun isIgnored(body: String?, patterns: List<MessagePattern>): Boolean {
        val signature = SmsStructureNormalizer.signatureFromBody(body)
        return patterns.any {
            it.definition.status == MessagePatternStatus.IGNORED &&
                it.definition.normalizedSignature == signature
        }
    }
}

data class PatternExtractionResult(
    val amount: BigDecimal?,
    val currency: Currency?,
    val merchant: String?,
    val identifierEvidence: List<ParsedIdentifierEvidence>,
    val accountOrCardLastFour: String?,
    val transactionType: TransactionType?,
    val parsingNotes: List<String>,
    val missingRequired: List<PatternCanonicalField>,
)

/**
 * Extracts typed fields using PatternFieldDefinition labels only (never stored values).
 */
object PatternFieldExtractor {

    private val MONEY = Regex("""[-+]?\d{1,3}(?:,\d{3})+(?:\.\d+)?|[-+]?\d+(?:\.\d+)?""")
    private val LAST4 = Regex("""(?<!\d)(\d{4})(?!\d)""")

    fun extract(body: String?, pattern: MessagePattern): PatternExtractionResult {
        val lines = LineBasedFieldParser.splitLines(body.orEmpty())
        val byLabel = lines.associateBy { normalize(it.label) }
        var amount: BigDecimal? = null
        var currency: Currency? = null
        var merchant: String? = null
        val evidence = mutableListOf<ParsedIdentifierEvidence>()
        var lastFour: String? = null
        val notes = mutableListOf("pattern_extract:${pattern.definition.id}")
        val missing = mutableListOf<PatternCanonicalField>()

        for (field in pattern.fields) {
            val line = byLabel[normalize(field.sourceLabel)]
            val value = line?.value?.trim().orEmpty()
            if (value.isEmpty()) {
                if (field.required) missing += field.canonicalField
                continue
            }
            when (field.canonicalField) {
                PatternCanonicalField.TRANSACTION_AMOUNT -> {
                    if (isBalanceLike(field.sourceLabel)) {
                        notes += "skipped_balance_as_amount"
                        continue
                    }
                    amount = parseMoney(value)
                    if (amount == null && field.required) missing += field.canonicalField
                }
                PatternCanonicalField.AVAILABLE_BALANCE,
                PatternCanonicalField.CARD_AMOUNT_DUE,
                -> notes += "context_field:${field.canonicalField}"
                PatternCanonicalField.CURRENCY -> currency = parseCurrency(value)
                PatternCanonicalField.MERCHANT,
                PatternCanonicalField.BENEFICIARY,
                -> if (merchant.isNullOrBlank()) merchant = value.take(80)
                PatternCanonicalField.ACCOUNT_LAST4,
                PatternCanonicalField.SOURCE_ACCOUNT_LAST4,
                PatternCanonicalField.DESTINATION_ACCOUNT_LAST4,
                PatternCanonicalField.CREDIT_CARD_LAST4,
                PatternCanonicalField.DEBIT_CARD_LAST4,
                PatternCanonicalField.IBAN_LAST4,
                PatternCanonicalField.SOURCE_IBAN_LAST4,
                PatternCanonicalField.DESTINATION_IBAN_LAST4,
                PatternCanonicalField.WALLET_LAST4,
                -> {
                    val four = LAST4.find(value)?.groupValues?.getOrNull(1)
                    if (four != null) {
                        lastFour = lastFour ?: four
                        evidence += ParsedIdentifierEvidence(
                            type = toIdentifierType(field.canonicalField),
                            lastFour = four,
                            role = toRole(field),
                            confidence = 95,
                            extractionRule = "pattern_field:${field.sourceLabel}",
                        )
                    } else if (field.required) {
                        missing += field.canonicalField
                    }
                }
                PatternCanonicalField.TRANSACTION_REFERENCE -> {
                    // Never treat reference as account id.
                    notes += "reference_ignored_as_id"
                }
                else -> Unit
            }
        }

        val type = pattern.definition.transactionType
            ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }

        return PatternExtractionResult(
            amount = amount,
            currency = currency,
            merchant = merchant,
            identifierEvidence = evidence,
            accountOrCardLastFour = lastFour,
            transactionType = type,
            parsingNotes = notes,
            missingRequired = missing.distinct(),
        )
    }

    fun toParsedTransaction(
        extraction: PatternExtractionResult,
        fallback: ParsedTransaction,
    ): ParsedTransaction {
        if (extraction.amount == null) return fallback
        return fallback.copy(
            amount = extraction.amount,
            currency = extraction.currency ?: fallback.currency,
            merchant = extraction.merchant ?: fallback.merchant,
            accountOrCardLastFourDigits = extraction.accountOrCardLastFour
                ?: fallback.accountOrCardLastFourDigits,
            identifierEvidence = (extraction.identifierEvidence + fallback.identifierEvidence)
                .distinctBy { it.type to it.lastFour to it.role },
            transactionType = extraction.transactionType ?: fallback.transactionType,
            status = fallback.status,
            confidence = maxOf(fallback.confidence, 70),
            parsingNotes = fallback.parsingNotes + extraction.parsingNotes,
            matchedRules = fallback.matchedRules + "message_pattern",
            missingFields = fallback.missingFields.filterNot { it == "amount" } +
                extraction.missingRequired.map { it.name.lowercase(Locale.ROOT) },
        )
    }

    private fun toIdentifierType(field: PatternCanonicalField): AccountIdentifierType = when (field) {
        PatternCanonicalField.CREDIT_CARD_LAST4 -> AccountIdentifierType.CREDIT_CARD_LAST4
        PatternCanonicalField.DEBIT_CARD_LAST4 -> AccountIdentifierType.DEBIT_CARD_LAST4
        PatternCanonicalField.IBAN_LAST4,
        PatternCanonicalField.SOURCE_IBAN_LAST4,
        PatternCanonicalField.DESTINATION_IBAN_LAST4,
        -> AccountIdentifierType.IBAN_LAST4
        PatternCanonicalField.WALLET_LAST4 -> AccountIdentifierType.WALLET_LAST4
        else -> AccountIdentifierType.ACCOUNT_LAST4
    }

    private fun toRole(field: PatternFieldDefinitionEntity): IdentifierRole = when (field.role) {
        PatternFieldRole.SOURCE -> IdentifierRole.SOURCE
        PatternFieldRole.DESTINATION -> IdentifierRole.DESTINATION
        else -> when (field.canonicalField) {
            PatternCanonicalField.SOURCE_ACCOUNT_LAST4,
            PatternCanonicalField.SOURCE_IBAN_LAST4,
            -> IdentifierRole.SOURCE
            PatternCanonicalField.DESTINATION_ACCOUNT_LAST4,
            PatternCanonicalField.DESTINATION_IBAN_LAST4,
            -> IdentifierRole.DESTINATION
            else -> IdentifierRole.UNSPECIFIED
        }
    }

    private fun isBalanceLike(label: String): Boolean {
        val lower = label.lowercase(Locale.ROOT)
        return "رصيد" in label || "balance" in lower || "حد ائتمان" in label || "credit limit" in lower ||
            "مستحق" in label
    }

    private fun parseMoney(value: String): BigDecimal? {
        val match = MONEY.find(value.replace(",", "")) ?: return null
        return runCatching { BigDecimal(match.value) }.getOrNull()?.takeIf { it.signum() > 0 }
    }

    private fun parseCurrency(value: String): Currency? {
        val v = value.uppercase(Locale.ROOT)
        return when {
            "SAR" in v || "SR" in v || "ريال" in value -> Currency.SAR
            else -> null
        }
    }

    private fun normalize(label: String): String =
        java.text.Normalizer.normalize(label, java.text.Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .trim()
}
