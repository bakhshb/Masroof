package com.baraa.masroof.sms

import com.baraa.masroof.accounts.AccountSmsAnalyzer
import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.data.db.PatternExtractionStrategy
import com.baraa.masroof.data.db.PatternFieldRole
import com.baraa.masroof.data.db.PatternValueType
import com.baraa.masroof.transaction.LineBasedFieldParser
import com.baraa.masroof.transaction.TransactionTypeTaxonomy
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

enum class PatternDiscoveryStage {
    TEMPLATE_BUILD,
    TYPE_CUE,
    SEMANTIC_FINGERPRINT,
    CANONICAL_KEY,
    SANITIZED_PREVIEW,
    LINE_PARSE,
    SEMANTIC_SCHEMA,
}

data class PatternDiscoveryFailure(
    val smsId: Long?,
    val senderHash: String?,
    val bodyHash: String,
    val stage: PatternDiscoveryStage,
    val exceptionClass: String,
)

data class PatternDiscoveryResult(
    val patterns: List<DiscoveredMessagePattern>,
    val inputMessages: Int,
    val processedMessages: Int,
    val skippedOtp: Int,
    val skippedNonFinancial: Int,
    val failedMessages: Int,
    val failures: List<PatternDiscoveryFailure>,
    val skippedBlank: Int = 0,
)

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
    ): List<DiscoveredMessagePattern> = discoverSafely(messages, existingPatterns).patterns

    fun discoverSafely(
        messages: List<SmsMessage>,
        existingPatterns: List<com.baraa.masroof.data.db.MessagePatternDefinitionEntity> = emptyList(),
    ): PatternDiscoveryResult = discoverSafely(messages, existingPatterns) { _, _ -> }

    internal fun discoverSafely(
        messages: List<SmsMessage>,
        existingPatterns: List<com.baraa.masroof.data.db.MessagePatternDefinitionEntity>,
        beforeStage: (SmsMessage, PatternDiscoveryStage) -> Unit,
    ): PatternDiscoveryResult {
        data class Acc(
            val canonicalKey: String,
            var signature: String,
            var count: Int = 0,
            var latest: Long = 0L,
            val samples: MutableList<String> = mutableListOf(),
            val suggestedFields: MutableList<SuggestedPatternField> = mutableListOf(),
            var nameHint: String = "نمط رسالة",
            var typeKey: String = "TYPE:UNKNOWN",
            var transactionTypeName: String? = null,
            var direction: String? = null,
            var channel: String? = null,
            var templateText: String = "",
            var placeholders: List<String> = emptyList(),
            var matchedPatternId: Long? = null,
            var matchedPatternStatus: com.baraa.masroof.data.db.MessagePatternStatus? = null,
            var familyKey: String = "",
            val observedChannels: MutableSet<String> = linkedSetOf(),
            val optionalSlots: MutableSet<String> = linkedSetOf(),
        )
        val buckets = linkedMapOf<String, Acc>()
        val failures = mutableListOf<PatternDiscoveryFailure>()
        var processedMessages = 0
        var skippedOtp = 0
        var skippedNonFinancial = 0
        var skippedBlank = 0
        for (sms in messages) {
            val body = sms.body.orEmpty()
            if (body.isBlank()) {
                skippedBlank++
                continue
            }
            try {
                val looksOtp = discoveryStage(sms, PatternDiscoveryStage.TYPE_CUE, beforeStage) {
                    SmsStructureNormalizer.looksLikeOtpOrMarketing(body)
                }
                if (looksOtp) {
                    skippedOtp++
                    continue
                }
                val cue = discoveryStage(sms, PatternDiscoveryStage.TYPE_CUE, beforeStage) {
                    MessageTypeCueCatalog.detect(body)
                }
                if (
                    cue.transactionType == com.baraa.masroof.transaction.TransactionType.NON_FINANCIAL ||
                    MessageTypeCueCatalog.isNonFinancialCue(body)
                ) {
                    skippedNonFinancial++
                    continue
                }
                val built = discoveryStage(sms, PatternDiscoveryStage.TEMPLATE_BUILD, beforeStage) {
                    MessageTemplateEngine.buildFromSms(body)
                }
                val fingerprint = discoveryStage(
                    sms,
                    PatternDiscoveryStage.SEMANTIC_FINGERPRINT,
                    beforeStage,
                ) {
                    SemanticPatternCanonicalizer.fromBody(body)
                }
                val canonicalKey = discoveryStage(
                    sms,
                    PatternDiscoveryStage.CANONICAL_KEY,
                    beforeStage,
                ) {
                    TemplateCanonicalizer.canonicalKey(built.templateText, built.signature)
                }
                val sample = discoveryStage(
                    sms,
                    PatternDiscoveryStage.SANITIZED_PREVIEW,
                    beforeStage,
                ) {
                    AccountSmsAnalyzer.sanitizedPreview(
                        body,
                        maxChars = 400,
                        preserveNewlines = true,
                    )
                }
                val lines = discoveryStage(sms, PatternDiscoveryStage.LINE_PARSE, beforeStage) {
                    LineBasedFieldParser.splitLines(body)
                }
                val suggested = discoveryStage(sms, PatternDiscoveryStage.LINE_PARSE, beforeStage) {
                    suggestFields(lines.map { it.label })
                }
                val familyKey = discoveryStage(
                    sms,
                    PatternDiscoveryStage.SEMANTIC_SCHEMA,
                    beforeStage,
                ) {
                    semanticKey(
                        templateText = built.templateText,
                        transactionTypeName = built.transactionType?.name ?: cue.transactionType?.name,
                        exactFallback = canonicalKey,
                    )
                }
                val matched = discoveryStage(
                    sms,
                    PatternDiscoveryStage.SEMANTIC_SCHEMA,
                    beforeStage,
                ) {
                    matchExisting(canonicalKey, body, existingPatterns)
                }
                val acc = buckets.getOrPut(canonicalKey) {
                    Acc(
                        canonicalKey = canonicalKey,
                        signature = built.signature,
                        nameHint = fingerprint.displayNameAr,
                        typeKey = cue.typeToken,
                        transactionTypeName = built.transactionType?.name ?: cue.transactionType?.name,
                        direction = built.direction ?: cue.direction,
                        channel = null,
                        templateText = built.templateText,
                        placeholders = built.placeholders,
                        matchedPatternId = matched?.id,
                        matchedPatternStatus = matched?.status,
                        familyKey = familyKey,
                    )
                }
                acc.count++
                acc.observedChannels += fingerprint.observedChannels
                acc.optionalSlots += fingerprint.optionalSlots.map { it.name }
                acc.suggestedFields += suggested
                if (acc.matchedPatternId == null && matched != null) {
                    acc.matchedPatternId = matched.id
                    acc.matchedPatternStatus = matched.status
                }
                if (built.templateText.lines().size > acc.templateText.lines().size) {
                    acc.templateText = built.templateText
                    acc.placeholders = built.placeholders
                    acc.signature = built.signature
                    acc.familyKey = familyKey
                } else if (acc.templateText.isBlank()) {
                    acc.templateText = built.templateText
                    acc.placeholders = built.placeholders
                    acc.familyKey = familyKey
                }
                if (sms.timestamp >= acc.latest) {
                    acc.latest = sms.timestamp
                    if (acc.samples.size < 3) acc.samples += sample
                } else if (acc.samples.size < 3) {
                    acc.samples += sample
                }
                processedMessages++
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: DiscoveryStageException) {
                failures += PatternDiscoveryFailure(
                    smsId = sms.id.takeIf { it > 0L },
                    senderHash = sms.sender?.takeIf { it.isNotBlank() }?.let(::safeHash),
                    bodyHash = safeHash(body),
                    stage = failure.stage,
                    exceptionClass = failure.cause?.javaClass?.simpleName
                        ?: failure.javaClass.simpleName,
                )
            } catch (failure: Throwable) {
                failures += PatternDiscoveryFailure(
                    smsId = sms.id.takeIf { it > 0L },
                    senderHash = sms.sender?.takeIf { it.isNotBlank() }?.let(::safeHash),
                    bodyHash = safeHash(body),
                    stage = PatternDiscoveryStage.SEMANTIC_SCHEMA,
                    exceptionClass = failure.javaClass.simpleName,
                )
            }
        }
        val exactPatterns = buckets.values
            .map { acc ->
                DiscoveredMessagePattern(
                    signature = acc.signature,
                    friendlyNameHint = acc.nameHint,
                    messageCount = acc.count,
                    latestTimestamp = acc.latest,
                    sanitizedSamples = acc.samples.distinct().take(3),
                    suggestedFields = acc.suggestedFields.distinctBy {
                        it.canonicalField to CanonicalMessageNormalizer.normalizeLabel(it.sourceLabel)
                    },
                    looksLikeOtpOrMarketing = false,
                    typeKey = acc.typeKey,
                    transactionTypeName = acc.transactionTypeName,
                    direction = acc.direction,
                    channel = acc.observedChannels.firstOrNull(),
                    templateText = acc.templateText,
                    placeholders = acc.placeholders,
                    canonicalKey = acc.canonicalKey,
                    familyKey = acc.familyKey,
                    matchedPatternId = acc.matchedPatternId,
                    matchedPatternStatus = acc.matchedPatternStatus,
                    observedChannels = acc.observedChannels.toList(),
                    optionalSlots = acc.optionalSlots.toList(),
                    discoveryConfidence = com.baraa.masroof.transaction.TransactionTypeTaxonomy
                        .discoveryConfidence(acc.count),
                    looksLikeNonFinancial = false,
                )
            }
        val patterns = exactPatterns
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
        return PatternDiscoveryResult(
            patterns = patterns,
            inputMessages = messages.size,
            processedMessages = processedMessages,
            skippedOtp = skippedOtp,
            skippedNonFinancial = skippedNonFinancial,
            failedMessages = failures.size,
            failures = failures,
            skippedBlank = skippedBlank,
        )
    }

    private class DiscoveryStageException(
        val stage: PatternDiscoveryStage,
        cause: Throwable,
    ) : RuntimeException(cause)

    private inline fun <T> discoveryStage(
        sms: SmsMessage,
        stage: PatternDiscoveryStage,
        beforeStage: (SmsMessage, PatternDiscoveryStage) -> Unit,
        block: () -> T,
    ): T = try {
        beforeStage(sms, stage)
        block()
    } catch (fatal: VirtualMachineError) {
        throw fatal
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: DiscoveryStageException) {
        throw failure
    } catch (failure: Throwable) {
        throw DiscoveryStageException(stage, failure)
    }

    private fun safeHash(value: String): String = runCatching {
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
    }.getOrElse {
        value.hashCode().toUInt().toString(16).padStart(8, '0').take(12)
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
                PatternRuntimeEligibility.isEligible(it)
        }?.let { return it }
        existing.firstOrNull {
            it.canonicalKey.isNotBlank() && it.canonicalKey == canonicalKey &&
                (
                    it.status != com.baraa.masroof.data.db.MessagePatternStatus.APPROVED ||
                        PatternRuntimeEligibility.isEligible(it)
                    )
        }
            ?.let { return it }
        if (!sourceBody.isNullOrBlank()) {
            val sourceSemantic = SemanticPatternSchemaNormalizer.fromBody(sourceBody)
            if (sourceSemantic is SemanticSchemaResult.Safe) {
                val semanticHits = existing.filter {
                    PatternRuntimeEligibility.isEligible(it) &&
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
                PatternRuntimeEligibility.isEligible(it) &&
                    !it.templateText.isNullOrBlank() &&
                    TemplateMatcher.matches(it.templateText, sourceBody)
            }?.let { return it }
            existing.firstOrNull {
                it.status != com.baraa.masroof.data.db.MessagePatternStatus.APPROVED &&
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
