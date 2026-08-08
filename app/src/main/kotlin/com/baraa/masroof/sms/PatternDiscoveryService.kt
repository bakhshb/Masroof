package com.baraa.masroof.sms

import com.baraa.masroof.accounts.AccountSmsAnalyzer
import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.data.db.PatternExtractionStrategy
import com.baraa.masroof.data.db.PatternFieldRole
import com.baraa.masroof.data.db.PatternValueType
import com.baraa.masroof.transaction.LineBasedFieldParser
import com.baraa.masroof.transaction.TransactionTypeTaxonomy

data class DiscoveredMessagePattern(
    val signature: String,
    val friendlyNameHint: String,
    val messageCount: Int,
    val latestTimestamp: Long,
    val sanitizedSamples: List<String>,
    val suggestedFields: List<SuggestedPatternField>,
    val looksLikeOtpOrMarketing: Boolean,
    /** Stable grouping key (type token), avoids mixing شراء with تحويل. */
    val typeKey: String = "TYPE:UNKNOWN",
    val transactionTypeName: String? = null,
    val direction: String? = null,
    val channel: String? = null,
    /** Exact structural template derived from an SMS in this cluster. */
    val templateText: String = "",
    val placeholders: List<String> = emptyList(),
    /** Stable exact structural identity for this PatternVariant. */
    val canonicalKey: String = "",
    /** Structural logical grouping key for its PatternFamily, never a transaction type. */
    val familyKey: String = "",
    /** Id of an already-saved pattern this cluster matches, or null when new. */
    val matchedPatternId: Long? = null,
    /** Status of the matched saved pattern (APPROVED / IGNORED / …), or null. */
    val matchedPatternStatus: com.baraa.masroof.data.db.MessagePatternStatus? = null,
    /** Observed wallet/provider brands within this family (metadata only). */
    val observedChannels: List<String> = emptyList(),
    /** Optional context slots seen in some (not necessarily all) messages. */
    val optionalSlots: List<String> = emptyList(),
    /**
     * Discovery confidence from occurrence count only (1 = low candidate).
     * Never gates whether a cluster is created.
     */
    val discoveryConfidence: Int = 0,
    /** True when cues indicate non-financial / settings / OTP content. */
    val looksLikeNonFinancial: Boolean = false,
)

data class SuggestedPatternField(
    val canonicalField: PatternCanonicalField,
    val sourceLabel: String,
    val valueType: PatternValueType,
    val role: PatternFieldRole = PatternFieldRole.PRIMARY,
    val required: Boolean = false,
    val extractionStrategy: PatternExtractionStrategy = PatternExtractionStrategy.LABELED_LINE,
)

/**
 * Clusters inbox SMS for one sender by exact normalized PatternVariant identity.
 * Family assignment is separate and never merges structural variants.
 */
object PatternDiscoveryService {

    fun discover(
        messages: List<SmsMessage>,
        existingPatterns: List<com.baraa.masroof.data.db.MessagePatternDefinitionEntity> = emptyList(),
    ): List<DiscoveredMessagePattern> {
        data class Acc(
            val canonicalKey: String,
            var signature: String,
            var count: Int = 0,
            var latest: Long = 0L,
            val samples: MutableList<String> = mutableListOf(),
            val labelHits: MutableMap<String, Int> = linkedMapOf(),
            var otpHits: Int = 0,
            var nonFinancialHits: Int = 0,
            var nameHint: String = "نمط رسالة",
            var typeKey: String = "TYPE:UNKNOWN",
            var transactionTypeName: String? = null,
            var direction: String? = null,
            var channel: String? = null,
            var templateText: String = "",
            var placeholders: List<String> = emptyList(),
            var sourceBody: String? = null,
            val observedChannels: MutableSet<String> = linkedSetOf(),
            val optionalSlots: MutableSet<String> = linkedSetOf(),
        )
        val buckets = linkedMapOf<String, Acc>()
        for (sms in messages) {
            val body = sms.body.orEmpty()
            if (body.isBlank()) continue
            val built = MessageTemplateEngine.buildFromSms(body)
            val cue = MessageTypeCueCatalog.detect(body)
            val fingerprint = SemanticPatternCanonicalizer.fromBody(body)
            val canonicalKey = TemplateCanonicalizer.canonicalKey(built.templateText, built.signature)
            val acc = buckets.getOrPut(canonicalKey) {
                Acc(
                    canonicalKey = canonicalKey,
                    signature = built.signature,
                    nameHint = fingerprint.displayNameAr,
                    typeKey = cue.typeToken,
                    transactionTypeName = built.transactionType?.name ?: cue.transactionType?.name,
                    direction = built.direction ?: cue.direction,
                    channel = null, // wallet is metadata, not cluster identity
                    templateText = built.templateText,
                    placeholders = built.placeholders,
                    sourceBody = body,
                )
            }
            acc.count++
            acc.observedChannels += fingerprint.observedChannels
            acc.optionalSlots += fingerprint.optionalSlots.map { it.name }
            // Prefer the richest template (most optional placeholders) as exemplar.
            if (built.templateText.lines().size > acc.templateText.lines().size) {
                acc.templateText = built.templateText
                acc.placeholders = built.placeholders
                acc.sourceBody = body
                acc.signature = built.signature
            } else if (acc.templateText.isBlank()) {
                acc.templateText = built.templateText
                acc.placeholders = built.placeholders
                acc.sourceBody = body
            }
            val sample = AccountSmsAnalyzer.sanitizedPreview(
                body,
                maxChars = 400,
                preserveNewlines = true,
            )
            if (sms.timestamp >= acc.latest) {
                acc.latest = sms.timestamp
                if (acc.samples.size < 3) {
                    acc.samples += sample
                }
            } else if (acc.samples.size < 3) {
                acc.samples += sample
            }
            if (SmsStructureNormalizer.looksLikeOtpOrMarketing(body)) acc.otpHits++
            if (MessageTypeCueCatalog.isNonFinancialCue(body) ||
                cue.transactionType == com.baraa.masroof.transaction.TransactionType.NON_FINANCIAL
            ) {
                acc.nonFinancialHits++
            }
            for (line in LineBasedFieldParser.splitLines(body)) {
                val label = line.label.trim()
                if (label.isNotEmpty()) {
                    acc.labelHits[label] = (acc.labelHits[label] ?: 0) + 1
                }
            }
        }
        return buckets.values
            .map { acc ->
                val matched = matchExisting(acc.canonicalKey, acc.sourceBody, existingPatterns)
                val looksOtp = acc.otpHits * 2 >= acc.count && acc.count > 0
                val looksNonFin = acc.nonFinancialHits * 2 >= acc.count && acc.count > 0
                DiscoveredMessagePattern(
                    signature = acc.signature,
                    friendlyNameHint = acc.nameHint,
                    messageCount = acc.count,
                    latestTimestamp = acc.latest,
                    sanitizedSamples = acc.samples.distinct().take(3),
                    suggestedFields = suggestFields(acc.labelHits.keys),
                    looksLikeOtpOrMarketing = looksOtp,
                    typeKey = acc.typeKey,
                    transactionTypeName = acc.transactionTypeName,
                    direction = acc.direction,
                    channel = acc.observedChannels.firstOrNull(),
                    templateText = acc.templateText,
                    placeholders = acc.placeholders,
                    canonicalKey = acc.canonicalKey,
                    familyKey = PatternStructure.familyKey(
                        CanonicalMessageNormalizer.normalizeTemplate(acc.templateText),
                        TransactionTypeTaxonomy.parse(acc.transactionTypeName),
                        acc.channel,
                    ),
                    matchedPatternId = matched?.id,
                    matchedPatternStatus = matched?.status,
                    observedChannels = acc.observedChannels.toList(),
                    optionalSlots = acc.optionalSlots.toList(),
                    discoveryConfidence = com.baraa.masroof.transaction.TransactionTypeTaxonomy
                        .discoveryConfidence(acc.count),
                    looksLikeNonFinancial = looksNonFin || looksOtp,
                )
            }
            .sortedWith(
                compareByDescending<DiscoveredMessagePattern> { it.messageCount }
                    .thenByDescending { it.latestTimestamp },
            )
    }

    /** A cluster matches a saved pattern only by exact structure or template instance. */
    private fun matchExisting(
        canonicalKey: String,
        sourceBody: String?,
        existing: List<com.baraa.masroof.data.db.MessagePatternDefinitionEntity>,
    ): com.baraa.masroof.data.db.MessagePatternDefinitionEntity? {
        if (existing.isEmpty()) return null
        existing.firstOrNull {
            it.canonicalKey.isNotBlank() && it.canonicalKey == canonicalKey &&
                it.status == com.baraa.masroof.data.db.MessagePatternStatus.APPROVED &&
                it.deprecatedAt == null && it.isActive
        }?.let { return it }
        existing.firstOrNull { it.canonicalKey.isNotBlank() && it.canonicalKey == canonicalKey }
            ?.let { return it }
        if (!sourceBody.isNullOrBlank()) {
            existing.firstOrNull {
                it.status == com.baraa.masroof.data.db.MessagePatternStatus.APPROVED &&
                    it.deprecatedAt == null &&
                    it.isActive &&
                    !it.templateText.isNullOrBlank() &&
                    TemplateMatcher.matches(it.templateText, sourceBody)
            }?.let { return it }
            existing.firstOrNull {
                !it.templateText.isNullOrBlank() &&
                    MessageTemplateEngine.matches(it.templateText, sourceBody)
            }?.let { return it }
        }
        return null
    }

    fun suggestFields(labels: Collection<String>): List<SuggestedPatternField> {
        val out = linkedMapOf<String, SuggestedPatternField>()
        for (label in labels) {
            val mapped = mapLabel(label) ?: continue
            out.putIfAbsent(label.trim().lowercase(), mapped.copy(sourceLabel = label.trim()))
        }
        return out.values.toList()
    }

    private fun mapLabel(label: String): SuggestedPatternField? {
        val l = label.trim()
        val lower = l.lowercase(LocaleOrRoot())
        fun field(
            c: PatternCanonicalField,
            vt: PatternValueType,
            role: PatternFieldRole = PatternFieldRole.PRIMARY,
            required: Boolean = false,
        ) = SuggestedPatternField(c, l, vt, role, required)

        // Money roles first via shared classifier — never map Due Amount via a
        // bare "amount" substring to TRANSACTION_AMOUNT.
        when (com.baraa.masroof.transaction.MonetaryFieldClassifier.classify(l)) {
            com.baraa.masroof.transaction.MonetaryRole.TRANSACTION_AMOUNT ->
                return field(PatternCanonicalField.TRANSACTION_AMOUNT, PatternValueType.MONEY, required = true)
            com.baraa.masroof.transaction.MonetaryRole.AVAILABLE_BALANCE ->
                return field(PatternCanonicalField.AVAILABLE_BALANCE, PatternValueType.MONEY)
            com.baraa.masroof.transaction.MonetaryRole.TOTAL_DUE,
            com.baraa.masroof.transaction.MonetaryRole.OUTSTANDING_BALANCE,
            -> return field(PatternCanonicalField.CARD_AMOUNT_DUE, PatternValueType.MONEY)
            com.baraa.masroof.transaction.MonetaryRole.CREDIT_LIMIT ->
                return field(PatternCanonicalField.CARD_AMOUNT_DUE, PatternValueType.MONEY)
            com.baraa.masroof.transaction.MonetaryRole.FEE,
            com.baraa.masroof.transaction.MonetaryRole.TAX,
            com.baraa.masroof.transaction.MonetaryRole.CASHBACK,
            com.baraa.masroof.transaction.MonetaryRole.OTHER_INFORMATIONAL_AMOUNT,
            -> return null
            com.baraa.masroof.transaction.MonetaryRole.UNKNOWN -> Unit
        }

        return when {
            lower.contains("بطاقة ائتمان") || lower.contains("ائتمانية") || lower.contains("credit card") ->
                field(PatternCanonicalField.CREDIT_CARD_LAST4, PatternValueType.LAST4)
            lower.contains("مدى") || lower.contains("debit") || lower.contains("بطاقة خصم") ->
                field(PatternCanonicalField.DEBIT_CARD_LAST4, PatternValueType.LAST4)
            lower.contains("آيبان") || lower.contains("iban") ->
                field(PatternCanonicalField.IBAN_LAST4, PatternValueType.LAST4)
            lower.contains("إلى حساب") || lower.contains("حساب المستفيد") ||
                lower.contains("destination") ->
                field(PatternCanonicalField.DESTINATION_ACCOUNT_LAST4, PatternValueType.LAST4, PatternFieldRole.DESTINATION)
            lower.contains("من حساب") || lower.contains("خصمت من") || lower.contains("source") ->
                field(PatternCanonicalField.SOURCE_ACCOUNT_LAST4, PatternValueType.LAST4, PatternFieldRole.SOURCE)
            lower.contains("حساب") || lower.contains("account") || lower.contains("بطاقة") ||
                lower.contains("card") ->
                field(PatternCanonicalField.ACCOUNT_LAST4, PatternValueType.LAST4)
            lower == "at" || lower == "لدى" || lower == "ل" ||
                lower.contains("merchant") || lower.contains("تاجر") ->
                field(PatternCanonicalField.MERCHANT, PatternValueType.TEXT)
            lower.contains("مرجع") || lower.contains("reference") || lower.contains("ref") ->
                field(PatternCanonicalField.TRANSACTION_REFERENCE, PatternValueType.REFERENCE)
            lower.contains("وقت") || lower.contains("time") || lower == "في" || lower == "on" ->
                field(PatternCanonicalField.TRANSACTION_TIME, PatternValueType.TIME)
            lower.contains("تاريخ") || lower.contains("date") ->
                field(PatternCanonicalField.TRANSACTION_DATE, PatternValueType.DATE)
            lower.contains("عملة") || lower.contains("currency") ->
                field(PatternCanonicalField.CURRENCY, PatternValueType.CURRENCY_CODE)
            else -> null
        }
    }

    private fun LocaleOrRoot(): java.util.Locale = java.util.Locale.ROOT
}
