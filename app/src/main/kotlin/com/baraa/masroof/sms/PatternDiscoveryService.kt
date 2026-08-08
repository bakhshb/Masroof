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
    /** DEBUG-only: throwable.message (for NoClassDefFoundError this is the
     *  missing class FQN). Never contains SMS content. Empty in release. */
    val exceptionMessage: String = "",
    /** DEBUG-only: cause class simple name, or "". */
    val causeClass: String = "",
    /** DEBUG-only: cause.message, or "". */
    val causeMessage: String = "",
    /** DEBUG-only: first few stack frames as "ClassName.methodName(fileName:line)".
     *  Internal class/method names only — never SMS. Empty in release. */
    val topStackFrames: List<String> = emptyList(),
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
    /**
     * Failures from OPTIONAL enrichment stages only (sanitized preview,
     * display fingerprint, suggested-field enrichment). These NEVER block a
     * pattern from being created and are NOT counted in [failedMessages].
     */
    val optionalStageFailures: List<PatternDiscoveryFailure> = emptyList(),
) {
    /** CORE failures only — messages that could not become a pattern. */
    val coreFailedMessages: Int get() = failures.size

    /** OPTIONAL enrichment failures — never blocked a pattern. */
    val optionalStageFailureCount: Int get() = optionalStageFailures.size

    /**
     * Reconciling invariant — every input message is accounted for exactly once:
     *   input == processed + skippedOtp + skippedNonFinancial + skippedBlank + coreFailed
     * Optional enrichment failures are deliberately excluded because they
     * never discard a message.
     */
    fun isReconciled(): Boolean =
        inputMessages == processedMessages + skippedOtp + skippedNonFinancial +
            skippedBlank + failedMessages

    /**
     * DEBUG-safe aggregation of every failure (core + optional) by
     * [PatternDiscoveryStage] + exception class. Never includes raw SMS —
     * only short body hashes (first 3 per group) so the dominant failing
     * stage and exact exception class can be surfaced to the user.
     */
    fun failureBreakdown(): List<StageFailureBreakdown> {
        val tagged = failures.map { false to it } + optionalStageFailures.map { true to it }
        return tagged.groupBy { (optional, f) -> Triple(f.stage, f.exceptionClass, optional) }
            .map { (key, list) ->
                StageFailureBreakdown(
                    stage = key.first,
                    exceptionClass = key.second,
                    count = list.size,
                    optional = key.third,
                    sampleBodyHashes = list.map { it.second.bodyHash }.distinct().take(3),
                    exceptionMessage = list.firstOrNull()?.second?.exceptionMessage.orEmpty(),
                    causeClass = list.firstOrNull()?.second?.causeClass.orEmpty(),
                    causeMessage = list.firstOrNull()?.second?.causeMessage.orEmpty(),
                    topStackFrames = list.firstOrNull()?.second?.topStackFrames.orEmpty(),
                )
            }
            .sortedWith(
                compareBy<StageFailureBreakdown> { it.optional }
                    .thenBy { it.stage.name }
                    .thenByDescending { it.count },
            )
    }
}

/** DEBUG-safe per-stage failure aggregation (no raw SMS). */
data class StageFailureBreakdown(
    val stage: PatternDiscoveryStage,
    val exceptionClass: String,
    val count: Int,
    /** True for OPTIONAL enrichment-stage failures that never blocked a pattern. */
    val optional: Boolean,
    /** First 3 short body hashes for this group (never raw SMS). */
    val sampleBodyHashes: List<String>,
    /** DEBUG-only representative throwable detail (first failure in group). */
    val exceptionMessage: String = "",
    val causeClass: String = "",
    val causeMessage: String = "",
    val topStackFrames: List<String> = emptyList(),
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
 * Optional enrichment stages for [PatternDiscoveryService.discoverSafely].
 *
 * These stages produce display metadata only (sanitized preview, display-name
 * fingerprint, suggested fields). A failure in any of them must NEVER discard
 * an otherwise valid financial SMS — the caller wraps each in a non-throwing
 * handler and records an [PatternDiscoveryFailure] into
 * [PatternDiscoveryResult.optionalStageFailures] instead.
 *
 * Defaults call the real production functions. Tests inject throwing
 * overrides to verify the non-fatal contract without depending on a
 * specific real-world input that happens to throw.
 */
data class OptionalDiscoveryStages(
    val sanitizedPreview: (String) -> String? = { body -> AccountSmsAnalyzer.safeSanitizedPreview(body) },
    val fingerprint: (String) -> SemanticPatternCanonicalizer.Fingerprint =
        SemanticPatternCanonicalizer::fromBody,
    val suggestFields: (List<com.baraa.masroof.transaction.ParsedLine>) -> List<SuggestedPatternField> =
        { lines -> PatternDiscoveryService.suggestFields(lines) },
) {
    companion object { val DEFAULT = OptionalDiscoveryStages() }
}

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
    ): PatternDiscoveryResult = discoverSafely(messages, existingPatterns, beforeStage, OptionalDiscoveryStages.DEFAULT)

    internal fun discoverSafely(
        messages: List<SmsMessage>,
        existingPatterns: List<com.baraa.masroof.data.db.MessagePatternDefinitionEntity> = emptyList(),
        beforeStage: (SmsMessage, PatternDiscoveryStage) -> Unit = { _, _ -> },
        optionalStages: OptionalDiscoveryStages = OptionalDiscoveryStages.DEFAULT,
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
        val optionalStageFailures = mutableListOf<PatternDiscoveryFailure>()
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
                // CORE — OTP / non-financial classification.
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
                // CORE — deterministic structural template + signature.
                val built = discoveryStage(sms, PatternDiscoveryStage.TEMPLATE_BUILD, beforeStage) {
                    MessageTemplateEngine.buildFromSms(body)
                }
                // OPTIONAL — display-name fingerprint + channel/slot metadata.
                val fingerprint = optionalStage(
                    sms, body, PatternDiscoveryStage.SEMANTIC_FINGERPRINT, beforeStage, optionalStageFailures,
                ) { optionalStages.fingerprint(body) }
                val displayName = fingerprint?.displayNameAr
                    ?: cue.displayNameAr.ifBlank { "نمط رسالة" }
                val observedChannels = fingerprint?.observedChannels ?: emptySet()
                val optionalSlots = fingerprint?.optionalSlots ?: emptySet()
                // CORE — canonical variant identity.
                val canonicalKey = discoveryStage(
                    sms,
                    PatternDiscoveryStage.CANONICAL_KEY,
                    beforeStage,
                ) {
                    TemplateCanonicalizer.canonicalKey(built.templateText, built.signature)
                }
                // OPTIONAL — sanitized preview (privacy/UI metadata only).
                val sample = optionalStage(
                    sms, body, PatternDiscoveryStage.SANITIZED_PREVIEW, beforeStage, optionalStageFailures,
                ) { optionalStages.sanitizedPreview(body) }
                // OPTIONAL — suggested-field enrichment.
                val lines = optionalStage(
                    sms, body, PatternDiscoveryStage.LINE_PARSE, beforeStage, optionalStageFailures,
                ) { LineBasedFieldParser.splitLines(body) }
                val suggested = if (lines != null) {
                    optionalStage(
                        sms, body, PatternDiscoveryStage.LINE_PARSE, beforeStage, optionalStageFailures,
                    ) { optionalStages.suggestFields(lines) } ?: emptyList()
                } else {
                    emptyList()
                }
                // CORE — semantic family identity. Ambiguity produces a
                // review: candidate (never a throw, never a disappearance).
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
                        nameHint = displayName,
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
                acc.observedChannels += observedChannels
                acc.optionalSlots += optionalSlots.map { it.name }
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
                if (sms.timestamp >= acc.latest) acc.latest = sms.timestamp
                if (sample != null && acc.samples.size < 3) acc.samples += sample
                processedMessages++
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: DiscoveryStageException) {
                failures += failureOf(sms, body, failure.stage, failure.cause ?: failure)
            } catch (failure: Throwable) {
                failures += failureOf(sms, body, PatternDiscoveryStage.SEMANTIC_SCHEMA, failure)
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
            optionalStageFailures = optionalStageFailures,
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

    /**
     * Non-fatal OPTIONAL enrichment stage. Any failure is recorded into
     * [failuresSink] (which becomes [PatternDiscoveryResult.optionalStageFailures])
     * and null is returned. The SMS continues through CORE stages and still
     * produces a pattern. Never returns raw SMS as a fallback.
     */
    private inline fun <T> optionalStage(
        sms: SmsMessage,
        body: String,
        stage: PatternDiscoveryStage,
        beforeStage: (SmsMessage, PatternDiscoveryStage) -> Unit,
        failuresSink: MutableList<PatternDiscoveryFailure>,
        block: () -> T,
    ): T? = try {
        beforeStage(sms, stage)
        block()
    } catch (fatal: VirtualMachineError) {
        throw fatal
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        failuresSink += failureOf(sms, body, stage, failure)
        null
    }

    private fun failureOf(
        sms: SmsMessage,
        body: String,
        stage: PatternDiscoveryStage,
        cause: Throwable,
    ): PatternDiscoveryFailure = PatternDiscoveryFailure(
        smsId = sms.id.takeIf { it > 0L },
        senderHash = sms.sender?.takeIf { it.isNotBlank() }?.let(::safeHash),
        bodyHash = safeHash(body),
        stage = stage,
        exceptionClass = cause.javaClass.simpleName.ifBlank { cause.javaClass.name },
        exceptionMessage = safeDebugMessage(cause),
        causeClass = cause.cause?.javaClass?.simpleName?.ifBlank { cause.cause?.javaClass?.name }.orEmpty(),
        causeMessage = safeDebugMessage(cause.cause),
        topStackFrames = safeDebugStackFrames(cause),
    )

    /**
     * DEBUG-only, SMS-free throwable detail. [NoClassDefFoundError.message]
     * is normally the missing class FQN — exactly what is needed to diagnose
     * Android class-loading/packaging failures. Returns "" in release builds
     * so no internal detail is surfaced to end users.
     */
    private fun safeDebugMessage(throwable: Throwable?): String {
        if (!com.baraa.masroof.BuildConfig.DEBUG) return ""
        val message = throwable?.message?.trim().orEmpty()
        // No SMS can appear here (this is an exception message), but guard
        // against an unreasonably long message just in case.
        return message.take(200)
    }

    private fun safeDebugStackFrames(throwable: Throwable): List<String> {
        if (!com.baraa.masroof.BuildConfig.DEBUG) return emptyList()
        return throwable.stackTrace.take(5).map { frame ->
            buildString {
                append(frame.className.substringAfterLast('.'))
                append('.')
                append(frame.methodName)
                val where = frame.fileName
                if (where != null) {
                    append('(')
                    append(where)
                    if (frame.lineNumber > 0) {
                        append(':')
                        append(frame.lineNumber)
                    }
                    append(')')
                }
            }
        }
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

    /**
     * Value-aware field suggester. Each candidate field is only emitted when
     * the line value actually fits the canonical shape (a 4-digit for any *_LAST4
     * field, a money literal for AMOUNT / AVAILABLE_BALANCE / CARD_AMOUNT_DUE,
     * a date or time for DATE / TIME, …). This keeps the suggested fields in
     * lock-step with the placeholders MessageTemplateEngine actually inserts
     * (the engine only writes a `{*_LAST4}` token when the value contains a
     * 4-digit, an `{AMOUNT}` when the value parses as money, etc.), so the
     * template ↔ fields contract holds automatically — patterns that need two
     * last4s (card payment: card last4 + source-account last4) work, and
     * lines whose label happens to match a field type but whose value is the
     * type-indicator word (e.g. `بطاقة إئتمانية: تسديد` where the value
     * `تسديد` is not a 4-digit) no longer produce a spurious unused field.
     */
    fun suggestFields(lines: List<com.baraa.masroof.transaction.ParsedLine>): List<SuggestedPatternField> {
        val out = linkedMapOf<Pair<PatternCanonicalField, String>, SuggestedPatternField>()
        for (line in lines) {
            val label = line.label
            val value = line.value
            val normalized = CanonicalMessageNormalizer.normalizeLabel(label)
            for (canonical in CanonicalPatternFieldClassifier.classify(label)) {
                if (!isValueSuitableFor(canonical, value)) continue
                val mapped = fieldFor(canonical, label.trim())
                out.putIfAbsent(canonical to normalized, mapped)
            }
        }
        return out.values.toList()
    }

    /** Back-compat overload: label-only suggester (no value gate). Prefer the
     *  value-aware [suggestFields] overload; this is kept so legacy callers
     *  still compile and behaves like the previous implementation. */
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

    private fun isValueSuitableFor(
        canonical: PatternCanonicalField,
        value: String,
    ): Boolean {
        val v = value.trim()
        if (v.isEmpty()) return false
        return when (canonical) {
            PatternCanonicalField.CREDIT_CARD_LAST4,
            PatternCanonicalField.DEBIT_CARD_LAST4,
            PatternCanonicalField.ACCOUNT_LAST4,
            PatternCanonicalField.SOURCE_ACCOUNT_LAST4,
            PatternCanonicalField.DESTINATION_ACCOUNT_LAST4,
            PatternCanonicalField.IBAN_LAST4,
            PatternCanonicalField.SOURCE_IBAN_LAST4,
            PatternCanonicalField.DESTINATION_IBAN_LAST4,
            PatternCanonicalField.WALLET_LAST4,
            -> com.baraa.masroof.transaction.LineBasedFieldParser
                .lastFourFromValue(v) != null
            PatternCanonicalField.TRANSACTION_AMOUNT,
            PatternCanonicalField.AVAILABLE_BALANCE,
            PatternCanonicalField.CARD_AMOUNT_DUE,
            -> com.baraa.masroof.transaction.LineBasedFieldParser
                .parseMoneyValue(v) != null
            PatternCanonicalField.TRANSACTION_DATE,
            PatternCanonicalField.TRANSACTION_TIME,
            -> com.baraa.masroof.transaction.LineBasedFieldParser
                .parseDateTimeField(listOf(com.baraa.masroof.transaction.ParsedLine("", v)))
                .let { (d, t) -> d != null || t != null }
            PatternCanonicalField.CURRENCY -> v.contains("SAR") || v.contains("USD") ||
                v.contains("EUR") || v.contains("SR") || v.contains("ريال") || v.contains("ر.س")
            else -> true  // text fields (MERCHANT, BENEFICIARY, REFERENCE, BANK, CHANNEL)
        }
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
