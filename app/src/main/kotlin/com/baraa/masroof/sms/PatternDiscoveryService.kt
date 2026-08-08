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
    /** Versioned semantic identity for its user-visible PatternFamily. */
    val familyKey: String = "",
    /** Exact structural observations retained below this semantic pattern. */
    val exactVariants: List<DiscoveredMessagePattern> = emptyList(),
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
 * Discovers exact structures first, then groups safe equivalents by semantic
 * identity. The returned list is user-visible semantic patterns; exact
 * structures remain available in [DiscoveredMessagePattern.exactVariants].
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
        val exactPatterns = buckets.values
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
                    familyKey = semanticKey(
                        templateText = acc.templateText,
                        transactionTypeName = acc.transactionTypeName,
                        exactFallback = acc.canonicalKey,
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
        return exactPatterns
            .groupBy { it.familyKey }
            .values
            .map { variants ->
                val representative = variants.maxWithOrNull(
                    compareBy<DiscoveredMessagePattern> { it.messageCount }
                        .thenBy { it.latestTimestamp },
                ) ?: variants.first()
                representative.copy(
                    messageCount = variants.sumOf { it.messageCount },
                    latestTimestamp = variants.maxOf { it.latestTimestamp },
                    sanitizedSamples = variants.flatMap { it.sanitizedSamples }.distinct().take(3),
                    suggestedFields = variants.flatMap { it.suggestedFields }
                        .distinctBy { it.canonicalField to CanonicalMessageNormalizer.normalizeLabel(it.sourceLabel) },
                    looksLikeOtpOrMarketing = variants.all { it.looksLikeOtpOrMarketing },
                    looksLikeNonFinancial = variants.all { it.looksLikeNonFinancial },
                    observedChannels = variants.flatMap { it.observedChannels }.distinct(),
                    optionalSlots = variants.flatMap { it.optionalSlots }.distinct(),
                    matchedPatternId = variants.firstNotNullOfOrNull { it.matchedPatternId },
                    matchedPatternStatus = variants.mapNotNull { it.matchedPatternStatus }
                        .minByOrNull { statusPriority(it) },
                    exactVariants = variants.map { it.copy(exactVariants = emptyList()) },
                )
            }
            .sortedWith(
                compareByDescending<DiscoveredMessagePattern> { it.messageCount }
                    .thenByDescending { it.latestTimestamp },
            )
    }

    private fun semanticKey(
        templateText: String,
        transactionTypeName: String?,
        exactFallback: String,
    ): String = when (
        val result = SemanticPatternSchemaNormalizer.fromTemplate(templateText, transactionTypeName)
    ) {
        is SemanticSchemaResult.Safe -> result.key
        is SemanticSchemaResult.NonFinancial -> "non-financial|$exactFallback"
        is SemanticSchemaResult.Ambiguous -> "review:${result.reason}|$exactFallback"
    }

    private fun statusPriority(status: com.baraa.masroof.data.db.MessagePatternStatus): Int = when (status) {
        com.baraa.masroof.data.db.MessagePatternStatus.APPROVED -> 0
        com.baraa.masroof.data.db.MessagePatternStatus.UNKNOWN -> 1
        com.baraa.masroof.data.db.MessagePatternStatus.IGNORED -> 2
        com.baraa.masroof.data.db.MessagePatternStatus.DEPRECATED -> 3
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
            val sourceSemantic = SemanticPatternSchemaNormalizer.fromBody(sourceBody)
            if (sourceSemantic is SemanticSchemaResult.Safe) {
                val semanticHits = existing.filter {
                    it.status == com.baraa.masroof.data.db.MessagePatternStatus.APPROVED &&
                        it.deprecatedAt == null &&
                        it.isActive &&
                        SemanticPatternSchemaNormalizer.fromTemplate(
                            it.templateText,
                            it.transactionType,
                        ).let { saved ->
                            saved is SemanticSchemaResult.Safe && saved.key == sourceSemantic.key
                        }
                }
                if (semanticHits.map { it.id }.distinct().size == 1) return semanticHits.single()
            }
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
        val out = linkedMapOf<Pair<PatternCanonicalField, String>, SuggestedPatternField>()
        for (label in labels) {
            val normalized = CanonicalMessageNormalizer.normalizeLabel(label)
            for (canonical in CanonicalPatternFieldClassifier.classify(label)) {
                val mapped = fieldFor(canonical, label.trim())
                out.putIfAbsent(canonical to normalized, mapped)
            }
        }
        return out.values.toList()
    }

    private fun fieldFor(canonical: PatternCanonicalField, label: String): SuggestedPatternField {
        val valueType = when (canonical) {
            PatternCanonicalField.TRANSACTION_AMOUNT,
            PatternCanonicalField.AVAILABLE_BALANCE,
            PatternCanonicalField.CARD_AMOUNT_DUE,
            -> PatternValueType.MONEY
            PatternCanonicalField.TRANSACTION_DATE -> PatternValueType.DATE
            PatternCanonicalField.TRANSACTION_TIME -> PatternValueType.TIME
            PatternCanonicalField.CURRENCY -> PatternValueType.CURRENCY_CODE
            PatternCanonicalField.TRANSACTION_REFERENCE -> PatternValueType.REFERENCE
            PatternCanonicalField.MERCHANT,
            PatternCanonicalField.BENEFICIARY,
            PatternCanonicalField.SOURCE_INSTITUTION,
            PatternCanonicalField.DESTINATION_INSTITUTION,
            PatternCanonicalField.CHANNEL,
            -> PatternValueType.TEXT
            else -> PatternValueType.LAST4
        }
        val role = when (canonical) {
            PatternCanonicalField.SOURCE_ACCOUNT_LAST4,
            PatternCanonicalField.SOURCE_IBAN_LAST4,
            PatternCanonicalField.SOURCE_INSTITUTION,
            -> PatternFieldRole.SOURCE
            PatternCanonicalField.DESTINATION_ACCOUNT_LAST4,
            PatternCanonicalField.DESTINATION_IBAN_LAST4,
            PatternCanonicalField.DESTINATION_INSTITUTION,
            -> PatternFieldRole.DESTINATION
            PatternCanonicalField.AVAILABLE_BALANCE,
            PatternCanonicalField.CARD_AMOUNT_DUE,
            -> PatternFieldRole.CONTEXT
            else -> PatternFieldRole.PRIMARY
        }
        return SuggestedPatternField(
            canonicalField = canonical,
            sourceLabel = label,
            valueType = valueType,
            role = role,
            required = canonical == PatternCanonicalField.TRANSACTION_AMOUNT,
        )
    }
}
