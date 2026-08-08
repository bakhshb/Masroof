package com.baraa.masroof.data.repository

import androidx.room.withTransaction
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.data.db.MasroofDatabase
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.AccountBalanceCalculator
import com.baraa.masroof.ledger.AccountMatcher
import com.baraa.masroof.ledger.AccountSummary
import com.baraa.masroof.ledger.JournalGenerationService
import com.baraa.masroof.ledger.JournalPostingStatus
import com.baraa.masroof.ledger.LedgerRepository
import com.baraa.masroof.ledger.LocalTreatmentAuditor
import com.baraa.masroof.ledger.PostingSide
import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleEngine
import com.baraa.masroof.rules.RuleEngineFactory
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.sms.SenderNormalizer
import com.baraa.masroof.sms.PatternDiscoveryService
import com.baraa.masroof.sms.SmsStructureNormalizer
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.MerchantNormalizer
import com.baraa.masroof.transaction.ParsedTransaction
import com.baraa.masroof.transaction.TransactionFingerprint
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

enum class SmsImportMode { REGISTERED_ACCOUNTS_ONLY, DISCOVER_NEW_SENDERS }

/**
 * What [SmsImportOrchestrator.commit] should persist.
 *
 * - [READY_ONLY]: postable READY rows only (primary «استيراد»).
 * - [REVIEW_CANDIDATES]: unmatched templates + account/review rows (primary «مراجعة»).
 * - [ALL]: legacy / onboarding path that processes every preview disposition.
 */
enum class SmsImportCommitMode {
    READY_ONLY,
    /** Persist only UNKNOWN semantic pattern candidates; never transactions. */
    PATTERN_CANDIDATES_ONLY,
    /** Message-review rows only (account link / classification) — not template gates. */
    MESSAGE_REVIEW_ONLY,
    REVIEW_CANDIDATES,
    ALL,
}

internal fun runtimeEligibleApprovedBySemanticKey(
    definitions: List<com.baraa.masroof.data.db.MessagePatternDefinitionEntity>,
): Map<String, com.baraa.masroof.data.db.MessagePatternDefinitionEntity> =
    definitions
        .filter(com.baraa.masroof.sms.PatternRuntimeEligibility::isEligible)
        .mapNotNull { approved ->
            val semantic = com.baraa.masroof.sms.SemanticPatternSchemaNormalizer
                .fromTemplate(approved.templateText, approved.transactionType)
                as? com.baraa.masroof.sms.SemanticSchemaResult.Safe
            semantic?.key?.let { it to approved }
        }
        .toMap()

internal fun safeDiagnosticSample(
    body: String?,
    sanitizer: (String?) -> String = com.baraa.masroof.diagnostics.TextSanitizer::sanitize,
): String? {
    if (body.isNullOrBlank()) return null
    return runCatching {
        sanitizer(body)
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(240)
            .takeIf { it.isNotBlank() }
    }.getOrNull()
}

enum class ImportDisposition {
    READY, NEEDS_ACCOUNT, NEEDS_CONFIRMATION, NEEDS_INSTITUTION, UNPARSED,
    EXACT_DUPLICATE, POSSIBLE_DUPLICATE, BEFORE_TRACKING_START, UNREGISTERED_SENDER, IGNORED,
    /** Registered sender SMS with no matching approved template — still counted in scan. */
    UNMATCHED_TEMPLATE,
    /** Multiple approved templates matched — needs user review, still counted in scan. */
    AMBIGUOUS_TEMPLATE,
    /** Template matched but amount/entity extraction failed — still counted in scan. */
    TEMPLATE_EXTRACTION_FAILED,
}

/**
 * Assigns exactly one final disposition. Priority is exclusive — the first
 * matching condition wins so counters never double-count the same message.
 */
object ImportDispositionClassifier {
    fun classify(
        isUnregisteredSender: Boolean = false,
        isNonFinancial: Boolean = false,
        isUnparsed: Boolean = false,
        isExactDuplicate: Boolean = false,
        isPossibleDuplicate: Boolean = false,
        isBeforeTrackingStart: Boolean = false,
        needsInstitution: Boolean = false,
        accountMatched: Boolean = false,
        needsConfirmation: Boolean = false,
    ): ImportDisposition = when {
        isUnregisteredSender -> ImportDisposition.UNREGISTERED_SENDER
        isNonFinancial -> ImportDisposition.IGNORED
        isUnparsed -> ImportDisposition.UNPARSED
        isExactDuplicate -> ImportDisposition.EXACT_DUPLICATE
        isPossibleDuplicate -> ImportDisposition.POSSIBLE_DUPLICATE
        isBeforeTrackingStart -> ImportDisposition.BEFORE_TRACKING_START
        needsInstitution -> ImportDisposition.NEEDS_INSTITUTION
        !accountMatched -> ImportDisposition.NEEDS_ACCOUNT
        needsConfirmation -> ImportDisposition.NEEDS_CONFIRMATION
        else -> ImportDisposition.READY
    }
}

/**
 * Near-duplicate detection for separate SMS messages that describe the same
 * purchase (push + digest). Exact fingerprint collisions remain EXACT_DUPLICATE.
 */
object NearDuplicateDetector {
    /** ±2 hours around an existing similar transaction. */
    const val DUPLICATE_WINDOW_MILLIS: Long = 2L * 60L * 60L * 1000L

    fun isPossibleDuplicate(
        candidateTimestamp: Long,
        candidateSimilarityKey: String?,
        existingByKey: List<TransactionEntity>,
        batchTimestampsByKey: Map<String, List<Long>> = emptyMap(),
    ): Boolean {
        val key = candidateSimilarityKey?.takeIf { it.isNotBlank() } ?: return false
        val inDb = existingByKey.any { existing ->
            kotlin.math.abs(existing.smsTimestamp - candidateTimestamp) <= DUPLICATE_WINDOW_MILLIS
        }
        if (inDb) return true
        val priorInBatch = batchTimestampsByKey[key].orEmpty()
        return priorInBatch.any { prior ->
            kotlin.math.abs(prior - candidateTimestamp) <= DUPLICATE_WINDOW_MILLIS
        }
    }
}

/**
 * Structured result of a single SMS import operation. Every count is
 * defined precisely:
 *  - [scannedMessages]        : raw SMS rows the user asked us to scan
 *  - [recognizedTransactions] : parsed successfully into a transaction
 *  - [importedTransactions]   : rows actually written to the transactions table
 *  - [linkedTransactions]     : rows linked to a financial account (linkedAccountId is set)
 *  - [postedTransactions]     : rows whose journal+postings were POSTED inside the same Room tx
 *  - [duplicateTransactions]  : fingerprint collisions; ignored
 *  - [needsReviewTransactions]: rows written but matched no account/rule
 *  - [unparsedMessages]       : bodies we couldn't parse (returns unrecognized)
 *  - [updatedAccountIds]      : accounts whose balance was recomputed from POSTED journals
 */
data class SmsImportResult(
    val scannedMessages: Int = 0,
    val recognizedTransactions: Int = 0,
    val readyTransactions: Int = 0,
    val importedTransactions: Int = 0,
    val linkedTransactions: Int = 0,
    val postedTransactions: Int = 0,
    val needsReviewTransactions: Int = 0,
    val duplicateTransactions: Int = 0,
    val unparsedMessages: Int = 0,
    val nonFinancialMessages: Int = 0,
    val unregisteredSenderMessages: Int = 0,
    val beforeTrackingStartCount: Int = 0,
    val updatedAccountIds: List<Long> = emptyList(),
    val affectedAccounts: List<AffectedAccountSummary> = emptyList(),
    val perTransactionLog: List<TransactionImportLog> = emptyList(),
    val permissionMissing: Boolean = false,
    val permissionMessage: String? = null,
    val trackingStartDateHint: LocalDate? = null,
    val importedAt: Long = 0L,
) {
    data class AffectedAccountSummary(
        val accountId: Long,
        val accountName: String,
        val openingBalance: BigDecimal,
        val openingBalanceDate: java.time.LocalDate?,
        val totalCredits: BigDecimal,
        val totalDebits: BigDecimal,
        val calculatedBalance: BigDecimal,
        val lastUpdatedAt: Long = 0L,
        val accountNature: com.baraa.masroof.transaction.AccountNature =
            com.baraa.masroof.transaction.AccountNature.ASSET,
        /** Increases bank cash (or raises card outstanding). */
        val moneyIn: BigDecimal = totalDebits,
        /** Leaves bank cash (or pays down card outstanding). */
        val moneyOut: BigDecimal = totalCredits,
    )

    data class TransactionImportLog(
        val smsId: Long,
        val sender: String?,
        val amount: BigDecimal?,
        val transactionType: String,
        val linkedAccountId: Long?,
        val journalEntryId: Long?,
        val debitAccountId: Long?,
        val creditAccountId: Long?,
        val postingStatus: String,
        val includedInCalculatedBalance: Boolean,
    )

    val isSuccess: Boolean get() = importedTransactions > 0

    companion object {
        val Empty = SmsImportResult()
        fun permissionMissing(message: String) = Empty.copy(permissionMissing = true, permissionMessage = message)
    }
}

/**
 * Pure, side-effect-free preview used by the "scan" step. Carries every
 * computed candidate so the user can review before any data is persisted.
 */
/**
 * Per-stage counts after the inbox has already been date-filtered.
 *
 * Invariant for messages that enter template matching:
 * [templateInput] == [templateMatched] + [unmatchedTemplate] + [ambiguousTemplate]
 */
data class ScanFilterFunnel(
    val rawSms: Int = 0,
    val afterOtpFilter: Int = 0,
    val afterSenderFilter: Int = 0,
    /** Registered, non-OTP, non-ignored messages that entered template matching. */
    val templateInput: Int = 0,
    val templateMatched: Int = 0,
    val unmatchedTemplate: Int = 0,
    val ambiguousTemplate: Int = 0,
    /** Matched template but amount/entity extraction failed (subset of matched). */
    val extractionFailed: Int = 0,
    /** Explicit IGNORED-pattern hits (excluded from [templateInput]). */
    val ignoredPattern: Int = 0,
) {
    val templateOutcomeSum: Int
        get() = templateMatched + unmatchedTemplate + ambiguousTemplate

    val templateInvariantHolds: Boolean
        get() = templateInput == templateOutcomeSum

    fun toLogMap(): Map<String, Any?> = mapOf(
        "rawSms" to rawSms,
        "afterOtpFilter" to afterOtpFilter,
        "afterSenderFilter" to afterSenderFilter,
        "templateInput" to templateInput,
        "templateMatched" to templateMatched,
        "unmatchedTemplate" to unmatchedTemplate,
        "ambiguousTemplate" to ambiguousTemplate,
        "extractionFailed" to extractionFailed,
        "ignoredPattern" to ignoredPattern,
        "templateInvariantHolds" to templateInvariantHolds,
    )
}

/**
 * One pending-candidate cluster explained for debug/report after scan.
 * Never includes unregistered senders or clusters covered by APPROVED templates.
 */
data class CandidatePatternDiagnostic(
    val senderRaw: String?,
    val senderNormalized: String?,
    val senderProfileId: Long?,
    val senderRegistered: Boolean,
    val candidatePatternId: Long?,
    val canonicalKey: String,
    val transactionType: String?,
    val messageCount: Int,
    val approvedEquivalentId: Long?,
    val reason: String,
)

data class ApprovedTemplateCoverage(
    val templateId: Long,
    val displayName: String,
    val transactionType: String?,
    val canonicalSignature: String,
    val active: Boolean,
    val approved: Boolean,
    val requiredPlaceholders: List<String>,
    val optionalPlaceholders: List<String>,
    val historicalMessageCount: Int,
    val currentCandidateMessages: Int,
    val successfulMatches: Int,
    val failureCounts: Map<String, Int>,
)

data class SenderTemplateCoverage(
    val normalizedSender: String,
    val senderProfileId: Long,
    val approvedTemplatesLoaded: Int,
    val messagesEnteringMatcher: Int,
    val matched: Int,
    val unmatched: Int,
    val ambiguous: Int,
)

data class TemplateAnchorDiagnostic(
    val expected: String,
    val actualStructuralLine: String?,
)

data class UnmatchedTemplateGroupDiagnostic(
    val count: Int,
    val normalizedSender: String,
    val senderProfileId: Long,
    val closestTemplateId: Long?,
    val closestTemplateName: String?,
    val closestTemplateTransactionType: String?,
    val failureReason: String,
    val normalizedStructuralRepresentation: String,
    val redactedRepresentativeMessage: String,
    val matchedAnchors: List<TemplateAnchorDiagnostic>,
    val failedAnchors: List<TemplateAnchorDiagnostic>,
)

data class ScanPreview(
    val mode: SmsImportMode = SmsImportMode.REGISTERED_ACCOUNTS_ONLY,
    val configuredSenderCount: Int = 0,
    val hasRegisteredSenders: Boolean = true,
    val scannedMessages: Int = 0,
    val recognizedTransactions: Int = 0,
    val nonFinancialMessages: Int = 0,
    val unparsedMessages: Int = 0,
    val unregisteredSenderMessages: Int = 0,
    /** OTP / 3-D Secure challenge SMS skipped (not ledger transactions). */
    val otpOrAuthMessages: Int = 0,
    val duplicateTransactions: Int = 0,
    val needsReviewTransactions: Int = 0,
    val beforeTrackingStartCount: Int = 0,
    /** Registered-sender messages with no matching approved template. */
    val unmatchedTemplateMessages: Int = 0,
    val ambiguousTemplateMessages: Int = 0,
    /** Template matched but extraction failed. */
    val extractionFailedMessages: Int = 0,
    /**
     * Distinct candidate (UNKNOWN) patterns that cover [patternApprovalCount] messages.
     * UI must show this for «مراجعة N نمطاً», never the raw message count as «أنماط».
     * Only counts structures that do NOT already have an equivalent APPROVED template.
     */
    val candidatePatternCount: Int = 0,
    /**
     * Per-cluster diagnostics for pending candidates (registered senders only).
     * Used to explain rediscovery / duplicates after approval.
     */
    val candidateDiagnostics: List<CandidatePatternDiagnostic> = emptyList(),
    /** One primary deterministic rejection reason per unmatched registered SMS. */
    val templateFailureCounts: Map<String, Int> = emptyMap(),
    /** Coverage of each approved template against current sender SMS. */
    val approvedTemplateCoverage: List<ApprovedTemplateCoverage> = emptyList(),
    val senderTemplateCoverage: List<SenderTemplateCoverage> = emptyList(),
    val unmatchedTemplateGroups: List<UnmatchedTemplateGroupDiagnostic> = emptyList(),
    val institutionGroups: List<InstitutionGroup> = emptyList(),
    val perTransaction: List<PreviewItem> = emptyList(),
    val discoveredSenders: List<DiscoveredSender> = emptyList(),
    /** Aggregated skips (no SMS bodies) for «رسائل لم تُستورد». */
    val skippedSenders: List<SkippedSenderGroup> = emptyList(),
    /** Complete sender/count/date groups for unregistered messages; never contains SMS bodies. */
    val unregisteredSenderGroups: List<SkippedSenderGroup> = emptyList(),
    /** Stage funnel for diagnostics (raw count is independent of templates). */
    val filterFunnel: ScanFilterFunnel? = null,
    /** True when READ_SMS was missing — never present this as an empty inbox. */
    val permissionMissing: Boolean = false,
    val permissionMessage: String? = null,
    /** Non-permission scan failure; raw inbox count may still be set. */
    val scanError: String? = null,
) {
    data class DiscoveredSender(val sender: String, val messageCount: Int, val latestTimestamp: Long, val likelyInstitution: String?)

    enum class SkipReason {
        UNREGISTERED_SENDER,
        NO_AMOUNT,
        NON_FINANCIAL,
        OTP_OR_AUTH,
        UNKNOWN_PATTERN,
        AMBIGUOUS_TEMPLATE,
        TEMPLATE_EXTRACTION_FAILED,
    }

    data class SkippedSenderGroup(
        val senderDisplay: String,
        val reason: SkipReason,
        val messageCount: Int,
        /** One TextSanitizer-redacted sample; never the raw SMS body. */
        val redactedSample: String? = null,
        val latestTimestamp: Long = 0L,
    ) {
        val reasonAr: String
            get() = when (reason) {
                SkipReason.UNREGISTERED_SENDER -> "مرسل غير مسجل على حساباتك"
                SkipReason.NO_AMOUNT -> "تعذّر استخراج المبلغ"
                SkipReason.NON_FINANCIAL -> "ليست رسالة مالية واضحة"
                SkipReason.OTP_OR_AUTH -> "رمز تحقق / تأكيد هوية — ليست عملية مالية"
                SkipReason.UNKNOWN_PATTERN -> "نمط جديد يحتاج مراجعة"
                SkipReason.AMBIGUOUS_TEMPLATE -> "أكثر من قالب مطابق — يحتاج مراجعة"
                SkipReason.TEMPLATE_EXTRACTION_FAILED -> "طابق القالب لكن تعذّر استخراج المبلغ"
            }
    }

    /** Mutable accumulator while scanning; never stores raw SMS. */
    data class SkipAccum(
        var count: Int = 0,
        var redactedSample: String? = null,
        var latestTimestamp: Long = 0L,
    )

    /** Exclusive count of messages with [ImportDisposition.READY]. */
    val readyCount: Int
        get() = if (perTransaction.isNotEmpty()) {
            perTransaction.count { it.disposition == ImportDisposition.READY }
        } else {
            // Test / empty-item fallback when only aggregate counters are supplied.
            (recognizedTransactions - needsReviewTransactions - duplicateTransactions - beforeTrackingStartCount)
                .coerceAtLeast(0)
        }

    /** Alias for UI counters — same as [readyCount]. */
    val readyToImport: Int get() = readyCount

    /**
     * Message/import reviewables for the operations review queue
     * (account link / classification / possible duplicate).
     * Never includes template-approval gates.
     */
    val messageReviewCount: Int
        get() = if (perTransaction.isNotEmpty()) {
            perTransaction.count { isMessageReviewDisposition(it.disposition) }
        } else {
            needsReviewTransactions
        }

    /**
     * Template gates resolved in «رسائل البنوك», not the operations review queue.
     * This is a **message** count — see [candidatePatternCount] for pattern count.
     */
    val patternApprovalCount: Int
        get() = if (perTransaction.isNotEmpty()) {
            perTransaction.count { isPatternApprovalDisposition(it.disposition) }
        } else {
            unmatchedTemplateMessages + ambiguousTemplateMessages + extractionFailedMessages
        }

    /**
     * Distinct candidate patterns needing approval for registered senders.
     * Never invents a count when [candidatePatternCount] is zero.
     */
    val patternsNeedingApproval: Int get() = candidatePatternCount

    /** Combined count — prefer [messageReviewCount] / [patternApprovalCount] in UI. */
    val needsReview: Int
        get() = if (perTransaction.isNotEmpty()) {
            messageReviewCount + patternApprovalCount
        } else {
            needsReviewTransactions
        }

    val matchedTemplate: Int get() = filterFunnel?.templateMatched ?: 0
    val unmatchedTemplate: Int get() = unmatchedTemplateMessages
    val ambiguousTemplate: Int get() = ambiguousTemplateMessages
    val extractionFailed: Int get() = extractionFailedMessages
    val duplicate: Int get() = duplicateTransactions
    val ignored: Int get() = nonFinancialMessages
    val nonFinancial: Int get() = nonFinancialMessages
    val totalSms: Int get() = scannedMessages
    val needsAccountLink: Int
        get() = perTransaction.count {
            it.disposition == ImportDisposition.NEEDS_ACCOUNT ||
                it.disposition == ImportDisposition.NEEDS_INSTITUTION
        }
    val needsClassification: Int
        get() = perTransaction.count { it.disposition == ImportDisposition.NEEDS_CONFIRMATION }

    val reviewDispositionCount: Int
        get() = perTransaction.count { isReviewDisposition(it.disposition) }

    data class InstitutionGroup(
        val institutionName: String,
        val totalRecognized: Int,
        val readyToImport: Int,
        val needsReview: Int,
        val unparsed: Int,
    )

    data class PreviewItem(
        val smsId: Long,
        val sender: String?,
        val amount: BigDecimal?,
        val transactionType: TransactionType,
        val proposedAccountId: Long?,
        val proposedAccountName: String?,
        val isDuplicate: Boolean,
        val needsReview: Boolean,
        val isBeforeTrackingStart: Boolean,
        val date: LocalDate?,
        val disposition: ImportDisposition,
        /** Exact immutable template revision used by scan; null when unmatched. */
        val patternRevisionId: Long? = null,
    )

    companion object {
        const val MAX_SKIPPED_GROUPS = 10

        fun isMessageReviewDisposition(disposition: ImportDisposition): Boolean = when (disposition) {
            ImportDisposition.NEEDS_ACCOUNT,
            ImportDisposition.NEEDS_CONFIRMATION,
            ImportDisposition.NEEDS_INSTITUTION,
            ImportDisposition.POSSIBLE_DUPLICATE,
            -> true
            else -> false
        }

        fun isPatternApprovalDisposition(disposition: ImportDisposition): Boolean = when (disposition) {
            ImportDisposition.UNMATCHED_TEMPLATE,
            ImportDisposition.AMBIGUOUS_TEMPLATE,
            ImportDisposition.TEMPLATE_EXTRACTION_FAILED,
            -> true
            else -> false
        }

        fun isReviewDisposition(disposition: ImportDisposition): Boolean =
            isMessageReviewDisposition(disposition) || isPatternApprovalDisposition(disposition)

        fun aggregateSkipped(
            buckets: Map<Pair<String, SkipReason>, SkipAccum>,
        ): List<SkippedSenderGroup> =
            buckets
                .map { (key, acc) ->
                    SkippedSenderGroup(
                        senderDisplay = key.first,
                        reason = key.second,
                        messageCount = acc.count,
                        redactedSample = acc.redactedSample,
                        latestTimestamp = acc.latestTimestamp,
                    )
                }
                .sortedWith(
                    compareByDescending<SkippedSenderGroup> { it.messageCount }
                        .thenBy { it.senderDisplay },
                )
                .take(MAX_SKIPPED_GROUPS)

        fun aggregateUnregisteredSenders(
            buckets: Map<Pair<String, SkipReason>, SkipAccum>,
        ): List<SkippedSenderGroup> =
            buckets
                .filterKeys { it.second == SkipReason.UNREGISTERED_SENDER }
                .map { (key, acc) ->
                    SkippedSenderGroup(
                        senderDisplay = key.first,
                        reason = key.second,
                        messageCount = acc.count,
                        redactedSample = null,
                        latestTimestamp = acc.latestTimestamp,
                    )
                }
                .sortedWith(
                    compareByDescending<SkippedSenderGroup> { it.messageCount }
                        .thenBy { it.senderDisplay },
                )
    }
}

/**
 * Atomic, single-Room-transaction SMS importer.
 *
 * Step 1 — [scan] parses every SMS in memory, classifies them and produces
 *          a [ScanPreview]. No database writes.
 *
 * Step 2 — [commit] opens **one** `database.withTransaction { ... }` block
 *          and, in order:
 *            a. insert SMS fingerprints
 *            b. insert/update the parsed transaction rows
 *            c. resolve the account link
 *            d. create the journal draft
 *            e. POST the journal (postings flushed in the same Room tx)
 *            f. mark `TransactionPostingStatus.POSTED`
 *            g. recompute affected account balances from POSTED journals
 *
 * The returned [SmsImportResult] reflects **only** persisted state. The
 * UI must never claim "linked 41" or "posted 41" unless those counters
 * match actual journal + posting rows.
 */
class SmsImportOrchestrator(
    private val database: MasroofDatabase,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: com.baraa.masroof.data.repository.CategoryRepository,
    private val merchantMemoryRepository: MerchantMemoryRepository,
    private val accountIdentifierRepository: AccountIdentifierRepository,
    private val accountMatcher: AccountMatcher,
    private val journalGenerationService: JournalGenerationService,
    private val ledgerRepository: LedgerRepository,
    private val systemAccounts: suspend (com.baraa.masroof.ledger.SystemAccountKey) -> Long,
    private val institutionResolver: com.baraa.masroof.ledger.FinancialInstitutionResolver,
    private val smsBodyRepository: TransactionSmsBodyRepository? = null,
    private val senderProfileRepository: SenderProfileRepository? = null,
    private val messagePatternRepository: MessagePatternRepository? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    /** Raw SMS is intentionally never persisted by the importer. */
    private suspend fun rememberSmsBody(transactionId: Long, body: String?) = Unit
    /**
     * Step 1 — read-only scan. Returns a [ScanPreview] describing what
     * the eventual commit would do.
     *
     * [messages] must already be the date-window inbox slice. Template matching
     * runs only after OTP / registered-sender / ignored-pattern gates and never
     * reduces [ScanPreview.scannedMessages].
     *
     * Template invariant:
     * templateInput == matched + unmatched + ambiguous
     */
    suspend fun scan(
        messages: List<SmsMessage>,
        trackingStartDate: LocalDate?,
        mode: SmsImportMode = SmsImportMode.REGISTERED_ACCOUNTS_ONLY,
        allowOncePatternIds: Set<Long> = emptySet(),
    ): ScanPreview = withContext(Dispatchers.IO) {
        val registeredSenders = registeredSenderKeys()
        val authorizedSenders = registeredSenders
        val rawSms = messages.size

        if (mode == SmsImportMode.REGISTERED_ACCOUNTS_ONLY && authorizedSenders.isEmpty()) {
            return@withContext ScanPreview(
                mode = mode,
                configuredSenderCount = 0,
                hasRegisteredSenders = false,
                scannedMessages = rawSms,
                filterFunnel = ScanFilterFunnel(rawSms = rawSms),
            )
        }
        if (mode == SmsImportMode.DISCOVER_NEW_SENDERS) {
            val otpCount = messages.count { com.baraa.masroof.sms.BankSmsFilter.isOtpOrAuthenticationMessage(it.body) }
            val discoveries = messages.asSequence()
                .filter { sms -> !com.baraa.masroof.sms.BankSmsFilter.isOtpOrAuthenticationMessage(sms.body) }
                .filter { sms -> SenderNormalizer.normalize(sms.sender) !in authorizedSenders }
                .filter { isLikelyFinancialSender(it.sender) || com.baraa.masroof.sms.BankSmsFilter.classifyMessage(it.sender, it.body).isMatch }
                .groupBy { it.sender?.trim().orEmpty() }
                .filterKeys { it.isNotBlank() }
                .map { (sender, rows) -> ScanPreview.DiscoveredSender(sender, rows.size, rows.maxOf { it.timestamp }, null) }
                .sortedByDescending { it.latestTimestamp }
            return@withContext ScanPreview(
                mode = mode, configuredSenderCount = authorizedSenders.size, scannedMessages = rawSms,
                unregisteredSenderMessages = messages.count { SenderNormalizer.normalize(it.sender) !in authorizedSenders },
                otpOrAuthMessages = otpCount,
                discoveredSenders = discoveries,
                unregisteredSenderGroups = discoveries.map {
                    ScanPreview.SkippedSenderGroup(
                        senderDisplay = it.sender,
                        reason = ScanPreview.SkipReason.UNREGISTERED_SENDER,
                        messageCount = it.messageCount,
                        latestTimestamp = it.latestTimestamp,
                    )
                },
                filterFunnel = ScanFilterFunnel(
                    rawSms = rawSms,
                    afterOtpFilter = rawSms - otpCount,
                ),
            )
        }

        // Engine setup is lazy: a failure here must NOT wipe template classification.
        data class EngineBundle(
            val engine: RuleEngine,
            val context: RuleContext,
            val ownedAccounts: List<com.baraa.masroof.data.db.FinancialAccount>,
        )
        var engineBundle: EngineBundle? = null
        var engineSetupError: String? = null
        suspend fun ensureEngine(): EngineBundle? {
            engineBundle?.let { return it }
            return try {
                val categories = categoryRepository.getAll()
                val ownedAccounts = RoomFinancialAccountRepository(database.financialAccountDao()).getOwnedActive()
                val merchantMemory = merchantMemoryRepository.getAll()
                val identifierSnapshots = accountIdentifierRepository.getActiveSnapshots()
                val accountsBySender = senderProfileRepository?.accountsBySenderKeyMap().orEmpty()
                val engine = RuleEngineFactory.build(categories, feeCategoryId = null)
                val context = RuleContext(ownedAccounts, merchantMemory, categories, identifierSnapshots, accountsBySender)
                EngineBundle(engine, context, ownedAccounts).also { engineBundle = it }
            } catch (t: Throwable) {
                engineSetupError = t.message ?: t.javaClass.simpleName
                android.util.Log.e("SmsImport", "scan engine setup failed — template buckets still counted", t)
                null
            }
        }

        var recognized = 0; var nonFinancial = 0; var unparsed = 0
        var unregistered = 0; var otpOrAuth = 0; var duplicates = 0; var needsReview = 0; var beforeTracking = 0
        var unmatchedTemplates = 0; var ambiguousTemplates = 0; var templateMatched = 0
        var templateInput = 0; var extractionFailed = 0; var ignoredPattern = 0
        data class CoverageAccum(
            val templateId: Long,
            val displayName: String,
            val transactionType: String?,
            val canonicalSignature: String,
            val active: Boolean,
            val approved: Boolean,
            val requiredPlaceholders: List<String>,
            val optionalPlaceholders: List<String>,
            val historicalMessageCount: Int,
            var currentCandidateMessages: Int = 0,
            var successfulMatches: Int = 0,
            val failures: MutableMap<String, Int> = linkedMapOf(),
        )
        data class SenderCoverageAccum(
            val normalizedSender: String,
            val senderProfileId: Long,
            val approvedTemplatesLoaded: Int,
            var messagesEnteringMatcher: Int = 0,
            var matched: Int = 0,
            var exactMatches: Int = 0,
            var semanticMatches: Int = 0,
            var unmatched: Int = 0,
            var ambiguous: Int = 0,
        )
        data class SenderRepairDiagnostic(
            val totalPatterns: Int,
            val runtimeEligiblePatterns: Int,
            val staleApprovedPatterns: Int,
            val rebuildAttempted: Boolean,
            val rebuildSucceeded: Boolean,
            val patternsAfterReload: Int,
        )
        data class UnmatchedGroupAccum(
            var count: Int,
            val normalizedSender: String,
            val senderProfileId: Long,
            val closestTemplateId: Long?,
            val closestTemplateName: String?,
            val closestTemplateTransactionType: String?,
            val failureReason: String,
            val normalizedStructuralRepresentation: String,
            val redactedRepresentativeMessage: String,
            val matchedAnchors: List<TemplateAnchorDiagnostic>,
            val failedAnchors: List<TemplateAnchorDiagnostic>,
        )
        val templateFailureCounts = linkedMapOf<String, Int>()
        val templateCoverage = linkedMapOf<Long, CoverageAccum>()
        val senderCoverage = linkedMapOf<Long, SenderCoverageAccum>()
        val unmatchedGroups = linkedMapOf<String, UnmatchedGroupAccum>()
        var loggedFirstMatcherFailure = false
        val groups = linkedMapOf<String, MutableList<ScanPreview.PreviewItem>>()
        val items = ArrayList<ScanPreview.PreviewItem>()
        val batchTimestampsByKey = mutableMapOf<String, MutableList<Long>>()
        val skipBuckets = linkedMapOf<Pair<String, ScanPreview.SkipReason>, ScanPreview.SkipAccum>()
        val senderTemplateDiag = mutableMapOf<String, Pair<Long?, Int>>() // key → (profileId, approvedCount)
        val patternsBySender = mutableMapOf<Long, List<MessagePattern>>()
        val senderRepairDiagnostics = mutableMapOf<Long, SenderRepairDiagnostic>()
        val messagesBySenderKey = messages.groupBy {
            SenderNormalizer.normalize(it.sender).orEmpty()
        }
        fun bumpSkip(sender: String?, reason: ScanPreview.SkipReason, body: String?, timestamp: Long) {
            val key = (sender?.trim().orEmpty().ifBlank { "—" }) to reason
            val acc = skipBuckets.getOrPut(key) { ScanPreview.SkipAccum() }
            acc.count++
            if (timestamp > acc.latestTimestamp) acc.latestTimestamp = timestamp
            if (acc.redactedSample == null && !body.isNullOrBlank()) {
                safeDiagnosticSample(body)?.let { acc.redactedSample = it }
            }
        }
        fun reviewItem(
            sms: SmsMessage,
            disposition: ImportDisposition,
        ): ScanPreview.PreviewItem = ScanPreview.PreviewItem(
            smsId = sms.id,
            sender = sms.sender,
            amount = null,
            transactionType = TransactionType.OTHER_FINANCIAL,
            proposedAccountId = null,
            proposedAccountName = null,
            isDuplicate = false,
            needsReview = true,
            isBeforeTrackingStart = false,
            date = null,
            disposition = disposition,
        )
        fun exportFailureReason(match: com.baraa.masroof.sms.TemplateMatcher.MatchResult?): String {
            val failedLine = match?.failedTemplateLine.orEmpty()
            return when (match?.failureReason) {
                com.baraa.masroof.sms.TemplateMatcher.FailureReason.EMPTY_INPUT ->
                    "NO_TEMPLATE_LOADED"
                com.baraa.masroof.sms.TemplateMatcher.FailureReason.REQUIRED_LINE_MISSING ->
                    "REQUIRED_FIELD_MISSING"
                com.baraa.masroof.sms.TemplateMatcher.FailureReason.STATIC_TEXT_MISMATCH,
                com.baraa.masroof.sms.TemplateMatcher.FailureReason.LABEL_MISMATCH,
                -> "STATIC_ANCHOR_MISMATCH"
                com.baraa.masroof.sms.TemplateMatcher.FailureReason.LINE_SHAPE_MISMATCH ->
                    "EXTRA_STATIC_TEXT"
                com.baraa.masroof.sms.TemplateMatcher.FailureReason.PLACEHOLDER_VALIDATION_MISMATCH -> when {
                    "{DATE}" in failedLine || "{TIME}" in failedLine || "{DATETIME}" in failedLine ->
                        "DATE_FORMAT_MISMATCH"
                    "{CURRENCY}" in failedLine -> "CURRENCY_FORMAT_MISMATCH"
                    else -> "PLACEHOLDER_VALIDATION_FAILED"
                }
                null -> "UNKNOWN"
            }
        }

        for (sms in messages) {
            var countedInTemplateStage = false
            try {
                val senderKey = SenderNormalizer.normalize(sms.sender).orEmpty()
                if (com.baraa.masroof.sms.BankSmsFilter.isOtpOrAuthenticationMessage(sms.body)) {
                    otpOrAuth++
                    bumpSkip(sms.sender, ScanPreview.SkipReason.OTP_OR_AUTH, sms.body, sms.timestamp)
                    continue
                }
                if (senderKey !in authorizedSenders) {
                    unregistered++
                    bumpSkip(sms.sender, ScanPreview.SkipReason.UNREGISTERED_SENDER, sms.body, sms.timestamp)
                    continue
                }

                val profile = runCatching { senderProfileRepository?.findByRawSender(sms.sender) }
                    .getOrNull()
                // Authorized via institution mapping alone is not enough for template
                // discovery — require a SenderProfile so approved templates can load.
                if (profile == null) {
                    unregistered++
                    bumpSkip(sms.sender, ScanPreview.SkipReason.UNREGISTERED_SENDER, sms.body, sms.timestamp)
                    continue
                }
                val definitionPatterns = if (messagePatternRepository != null) {
                    patternsBySender[profile.id] ?: runCatching {
                        val loaded = messagePatternRepository.getForSender(profile.id)
                        val eligible = loaded.count(
                            com.baraa.masroof.sms.PatternRuntimeEligibility::isEligible,
                        )
                        val stale = loaded.count {
                            com.baraa.masroof.sms.PatternRuntimeEligibility.evaluate(it) ==
                                com.baraa.masroof.sms.PatternRuntimeEligibilityResult.STALE_NORMALIZATION
                        }
                        val repair = if (stale > 0) {
                            messagePatternRepository.rebuildStaleForSender(
                                profile.id,
                                messagesBySenderKey[senderKey].orEmpty(),
                            )
                        } else {
                            null
                        }
                        val after = repair?.patternsAfterReload ?: loaded
                        senderRepairDiagnostics[profile.id] = SenderRepairDiagnostic(
                            totalPatterns = loaded.size,
                            runtimeEligiblePatterns = eligible,
                            staleApprovedPatterns = stale,
                            rebuildAttempted = repair?.rebuildAttempted == true,
                            rebuildSucceeded = repair?.rebuildSucceeded == true,
                            patternsAfterReload = after.size,
                        )
                        after
                    }.getOrElse { emptyList() }.also {
                        patternsBySender[profile.id] = it
                    }
                } else {
                    emptyList()
                }
                if (senderKey.isNotBlank() && senderKey !in senderTemplateDiag) {
                    val approved = definitionPatterns.count(
                        com.baraa.masroof.sms.PatternRuntimeEligibility::isEligible,
                    )
                    senderTemplateDiag[senderKey] = profile.id to approved
                }

                // IGNORED patterns are an explicit pre-template exclusion (not OTP).
                if (com.baraa.masroof.sms.MessagePatternMatcher.isIgnored(sms.body, definitionPatterns)) {
                    ignoredPattern++
                    nonFinancial++
                    bumpSkip(sms.sender, ScanPreview.SkipReason.NON_FINANCIAL, sms.body, sms.timestamp)
                    continue
                }

                // Every remaining registered message MUST produce Matched / Unmatched / Ambiguous.
                templateInput++
                countedInTemplateStage = true
                val matchDiagnostics = com.baraa.masroof.sms.TemplateResolutionService.diagnose(
                    body = sms.body,
                    patterns = definitionPatterns,
                    allowOncePatternIds = allowOncePatternIds,
                )
                val senderStats = senderCoverage.getOrPut(profile.id) {
                    SenderCoverageAccum(
                        normalizedSender = senderKey,
                        senderProfileId = profile.id,
                        approvedTemplatesLoaded = matchDiagnostics.attempts.count {
                            it.eligible && it.approved
                        },
                    )
                }
                senderStats.messagesEnteringMatcher++
                matchDiagnostics.attempts
                    .filter { it.eligible && it.approved }
                    .forEach { attempt ->
                        val coverage = templateCoverage.getOrPut(attempt.templateId) {
                            CoverageAccum(
                                templateId = attempt.templateId,
                                displayName = attempt.displayName,
                                transactionType = attempt.transactionType,
                                canonicalSignature = attempt.canonicalSignature,
                                active = attempt.active,
                                approved = attempt.approved,
                                requiredPlaceholders = attempt.requiredPlaceholders,
                                optionalPlaceholders = attempt.optionalPlaceholders,
                                historicalMessageCount = attempt.historicalMessageCount,
                            )
                        }
                        coverage.currentCandidateMessages++
                        if (attempt.match?.matched == true) {
                            coverage.successfulMatches++
                        } else {
                            val reason = attempt.match?.failureReason?.name ?: "NO_APPROVED_MATCH"
                            coverage.failures[reason] = (coverage.failures[reason] ?: 0) + 1
                        }
                    }
                val outcome = runCatching {
                    com.baraa.masroof.sms.TemplateResolutionService.resolve(
                        sender = sms.sender,
                        body = sms.body,
                        smsTimestampMillis = sms.timestamp.takeIf { it > 0L },
                        patterns = definitionPatterns,
                        allowOncePatternIds = allowOncePatternIds,
                    )
                }.getOrElse {
                    android.util.Log.w("SmsImport", "template resolve failed for smsId=${sms.id}", it)
                    com.baraa.masroof.sms.TemplateResolutionResult.Unmatched(
                        com.baraa.masroof.sms.TemplateResolutionResult.Unmatched.Reason.LOOKUP_FAILED,
                    )
                }

                when (outcome) {
                    is com.baraa.masroof.sms.TemplateResolutionResult.Unmatched -> {
                        // A known sender's unseen structure is durable training
                        // material. Upsert is exact-signature idempotent and does
                        // not alter transactions, accounts, or journals.
                        unmatchedTemplates++
                        needsReview++
                        senderStats.unmatched++
                        val best = matchDiagnostics.attempts
                            .filter { it.eligible && it.approved }
                            .maxByOrNull { it.match?.score ?: -1 }
                        val failure = when (outcome.reason) {
                            com.baraa.masroof.sms.TemplateResolutionResult.Unmatched.Reason.NO_APPROVED_MATCH ->
                                if (best == null) "NO_TEMPLATE_LOADED" else exportFailureReason(best.match)
                            else -> outcome.reason.name
                        }
                        templateFailureCounts[failure] =
                            (templateFailureCounts[failure] ?: 0) + 1
                        val safeRepresentative =
                            com.baraa.masroof.diagnostics.ApprovedTemplateDiagnosticSanitizer
                                .sanitizeMessage(sms.body)
                        val match = best?.match
                        val matchedAnchors = match?.trace.orEmpty()
                            .filter { it.matched }
                            .map {
                                TemplateAnchorDiagnostic(
                                    expected = com.baraa.masroof.diagnostics
                                        .ApprovedTemplateDiagnosticSanitizer
                                        .sanitizeTemplateLine(it.templateLine),
                                    actualStructuralLine = com.baraa.masroof.diagnostics
                                        .ApprovedTemplateDiagnosticSanitizer
                                        .sanitizeTemplateLine(it.bodyLine),
                                )
                            }
                        val failedAnchors = match?.trace.orEmpty()
                            .filterNot { it.matched }
                            .takeLast(1)
                            .map {
                                TemplateAnchorDiagnostic(
                                    expected = com.baraa.masroof.diagnostics
                                        .ApprovedTemplateDiagnosticSanitizer
                                        .sanitizeTemplateLine(it.templateLine),
                                    actualStructuralLine = com.baraa.masroof.diagnostics
                                        .ApprovedTemplateDiagnosticSanitizer
                                        .sanitizeTemplateLine(it.bodyLine),
                                )
                            }
                        val groupKey = listOf(
                            profile.id,
                            matchDiagnostics.smsStructuralSignature,
                            best?.templateId ?: -1L,
                            failure,
                        ).joinToString("|")
                        val group = unmatchedGroups[groupKey]
                        if (group == null) {
                            unmatchedGroups[groupKey] = UnmatchedGroupAccum(
                                count = 1,
                                normalizedSender = senderKey,
                                senderProfileId = profile.id,
                                closestTemplateId = best?.templateId,
                                closestTemplateName = best?.displayName,
                                closestTemplateTransactionType = best?.transactionType,
                                failureReason = failure,
                                normalizedStructuralRepresentation =
                                    safeRepresentative.replace("\n", " | "),
                                redactedRepresentativeMessage = safeRepresentative,
                                matchedAnchors = matchedAnchors,
                                failedAnchors = failedAnchors,
                            )
                        } else {
                            group.count++
                        }
                        if (!loggedFirstMatcherFailure && com.baraa.masroof.BuildConfig.DEBUG) {
                            loggedFirstMatcherFailure = true
                            android.util.Log.i(
                                "ApprovedTemplateMatcher",
                                "first_failure smsId=${sms.id} normalizedSender=${com.baraa.masroof.diagnostics
                                    .ApprovedTemplateDiagnosticSanitizer.sanitizeSender(senderKey)} " +
                                    "profileId=${profile.id} " +
                                    "templateId=${best?.templateId ?: -1L} " +
                                    "canonicalKey=${com.baraa.masroof.diagnostics
                                        .ApprovedTemplateDiagnosticSanitizer
                                        .sanitizeCanonicalStructure(best?.canonicalKey)} " +
                                    "smsStructure=${safeRepresentative.replace("\n", " | ")} " +
                                    "reason=$failure " +
                                    "templateLine=${com.baraa.masroof.diagnostics
                                        .ApprovedTemplateDiagnosticSanitizer
                                        .sanitizeTemplateLine(best?.match?.failedTemplateLine)} " +
                                    "smsLine=${com.baraa.masroof.diagnostics
                                        .ApprovedTemplateDiagnosticSanitizer
                                        .sanitizeTemplateLine(best?.match?.failedBodyLine)}",
                            )
                        }
                        bumpSkip(sms.sender, ScanPreview.SkipReason.UNKNOWN_PATTERN, sms.body, sms.timestamp)
                        items += reviewItem(sms, ImportDisposition.UNMATCHED_TEMPLATE)
                    }
                    is com.baraa.masroof.sms.TemplateResolutionResult.Ambiguous -> {
                        ambiguousTemplates++
                        needsReview++
                        senderStats.ambiguous++
                        bumpSkip(sms.sender, ScanPreview.SkipReason.AMBIGUOUS_TEMPLATE, sms.body, sms.timestamp)
                        items += reviewItem(sms, ImportDisposition.AMBIGUOUS_TEMPLATE)
                    }
                    is com.baraa.masroof.sms.TemplateResolutionResult.Matched -> {
                        templateMatched++
                        if (
                            outcome.pattern.definition.status ==
                            com.baraa.masroof.data.db.MessagePatternStatus.APPROVED
                        ) {
                            senderStats.matched++
                            when (outcome.matchTier) {
                                com.baraa.masroof.sms.PatternMatchTier.EXACT_STRUCTURE ->
                                    senderStats.exactMatches++
                                com.baraa.masroof.sms.PatternMatchTier.SEMANTIC_SCHEMA ->
                                    senderStats.semanticMatches++
                                else -> Unit
                            }
                        } else {
                            senderStats.unmatched++
                        }
                        val parsed = outcome.parsed
                        val bundle = ensureEngine()
                        if (parsed.amount == null || bundle == null) {
                            extractionFailed++
                            unparsed++
                            needsReview++
                            bumpSkip(
                                sms.sender,
                                ScanPreview.SkipReason.TEMPLATE_EXTRACTION_FAILED,
                                sms.body,
                                sms.timestamp,
                            )
                            items += reviewItem(sms, ImportDisposition.TEMPLATE_EXTRACTION_FAILED)
                            continue
                        }
                        val entity = runCatching {
                            buildEntity(sms, parsed, bundle.engine, bundle.context)
                        }.getOrNull()
                        if (entity == null) {
                            extractionFailed++
                            unparsed++
                            needsReview++
                            bumpSkip(
                                sms.sender,
                                ScanPreview.SkipReason.TEMPLATE_EXTRACTION_FAILED,
                                sms.body,
                                sms.timestamp,
                            )
                            items += reviewItem(sms, ImportDisposition.TEMPLATE_EXTRACTION_FAILED)
                            continue
                        }
                        recognized++
                        val isDup = transactionRepository.existsByFingerprint(entity.uniqueFingerprint)
                        val similarExisting = entity.transactionSimilarityKey
                            ?.let { transactionRepository.findBySimilarityKey(it) }
                            .orEmpty()
                        val isPossibleDup = !isDup && NearDuplicateDetector.isPossibleDuplicate(
                            candidateTimestamp = entity.smsTimestamp,
                            candidateSimilarityKey = entity.transactionSimilarityKey,
                            existingByKey = similarExisting,
                            batchTimestampsByKey = batchTimestampsByKey,
                        )
                        val match = accountMatcher.match(
                            entity,
                            bundle.ownedAccounts,
                            accountIdentifierRepository,
                            parsed.identifierEvidence,
                        )
                        val before = trackingStartDate != null &&
                            entity.transactionDate != null &&
                            entity.transactionDate.isBefore(trackingStartDate)
                        val incompleteTwoSided = entity.financialTreatment.requiresTwoAccounts &&
                            match.account != null &&
                            !match.needsReview &&
                            match.destinationAccountCandidate == null
                        val confirmedMatch = match.account != null && !match.needsReview
                        val lowConfidence = parsed.confidence < 30
                        val disposition = ImportDispositionClassifier.classify(
                            isExactDuplicate = isDup,
                            isPossibleDuplicate = isPossibleDup,
                            isBeforeTrackingStart = before && !isDup && !isPossibleDup,
                            accountMatched = confirmedMatch && !lowConfidence,
                            needsConfirmation = incompleteTwoSided || lowConfidence,
                        )
                        val isReviewNow = disposition == ImportDisposition.NEEDS_ACCOUNT ||
                            disposition == ImportDisposition.NEEDS_CONFIRMATION ||
                            disposition == ImportDisposition.NEEDS_INSTITUTION ||
                            disposition == ImportDisposition.POSSIBLE_DUPLICATE
                        when (disposition) {
                            ImportDisposition.EXACT_DUPLICATE -> duplicates++
                            ImportDisposition.POSSIBLE_DUPLICATE -> duplicates++
                            ImportDisposition.BEFORE_TRACKING_START -> beforeTracking++
                            ImportDisposition.NEEDS_ACCOUNT,
                            ImportDisposition.NEEDS_CONFIRMATION,
                            ImportDisposition.NEEDS_INSTITUTION -> needsReview++
                            else -> Unit
                        }
                        entity.transactionSimilarityKey?.let { key ->
                            batchTimestampsByKey.getOrPut(key) { mutableListOf() }.add(entity.smsTimestamp)
                        }
                        val institutionKey = institutionResolver.resolve(
                            sender = sms.sender,
                            parsedInstitution = parserInstitution(parsed),
                            parserIdentity = parsed.parserName,
                            knownInstitutionNames = com.baraa.masroof.ledger.FinancialInstitutionResolver.WELL_KNOWN_INSTITUTIONS.toSet(),
                        ).institutionDisplayName
                        val previewAccount = match.account?.takeIf { !match.needsReview }
                        val item = ScanPreview.PreviewItem(
                            sms.id, sms.sender, entity.amount, entity.transactionType, previewAccount?.id,
                            previewAccount?.displayName, isDup || isPossibleDup, isReviewNow, before,
                            entity.transactionDate, disposition, outcome.pattern.definition.id,
                        )
                        groups.getOrPut(institutionKey) { mutableListOf() }.add(item)
                        items += item
                    }
                }
            } catch (t: Throwable) {
                runCatching {
                    android.util.Log.w("SmsImport", "scan row failed smsId=${sms.id}", t)
                }
                if (!countedInTemplateStage) {
                    templateInput++
                }
                // If we already counted matched/unmatched/ambiguous, don't double-count outcomes.
                val alreadyOutcome =
                    countedInTemplateStage &&
                        (templateMatched + unmatchedTemplates + ambiguousTemplates) >= templateInput
                if (!alreadyOutcome) {
                    unmatchedTemplates++
                    needsReview++
                    bumpSkip(sms.sender, ScanPreview.SkipReason.UNKNOWN_PATTERN, sms.body, sms.timestamp)
                    items += reviewItem(sms, ImportDisposition.UNMATCHED_TEMPLATE)
                }
            }
        }

        val funnel = ScanFilterFunnel(
            rawSms = rawSms,
            afterOtpFilter = rawSms - otpOrAuth,
            afterSenderFilter = rawSms - otpOrAuth - unregistered,
            templateInput = templateInput,
            templateMatched = templateMatched,
            unmatchedTemplate = unmatchedTemplates,
            ambiguousTemplate = ambiguousTemplates,
            extractionFailed = extractionFailed,
            ignoredPattern = ignoredPattern,
        ).let { f ->
            // Heal any residual gap so messages never disappear from the template stage.
            val gap = f.templateInput - f.templateOutcomeSum
            if (gap > 0) {
                android.util.Log.e("SmsImport", "healing template gap=$gap (invariant would fail)")
                unmatchedTemplates += gap
                needsReview += gap
                f.copy(unmatchedTemplate = unmatchedTemplates)
            } else {
                f
            }
        }
        if (!funnel.templateInvariantHolds) {
            android.util.Log.e(
                "SmsImport",
                "Template invariant still broken after heal: ${funnel.toLogMap()}",
            )
        }
        if (com.baraa.masroof.BuildConfig.DEBUG) {
            android.util.Log.i(
                "ApprovedTemplateMatcher",
                "failure_summary=$templateFailureCounts coverage=${templateCoverage.values.map {
                    "${it.templateId}:${it.transactionType}:${it.successfulMatches}/${it.currentCandidateMessages}"
                }}",
            )
            senderRepairDiagnostics.forEach { (senderProfileId, repair) ->
                val coverage = senderCoverage[senderProfileId]
                android.util.Log.d(
                    "PatternLifecycle",
                    "sender=${coverage?.normalizedSender.orEmpty()} " +
                        "totalPatterns=${repair.totalPatterns} " +
                        "runtimeEligiblePatterns=${repair.runtimeEligiblePatterns} " +
                        "staleApprovedPatterns=${repair.staleApprovedPatterns} " +
                        "rebuildAttempted=${repair.rebuildAttempted} " +
                        "rebuildSucceeded=${repair.rebuildSucceeded} " +
                        "patternsAfterReload=${repair.patternsAfterReload} " +
                        "exactMatches=${coverage?.exactMatches ?: 0} " +
                        "semanticMatches=${coverage?.semanticMatches ?: 0} " +
                        "unmatched=${coverage?.unmatched ?: 0}",
                )
            }
        }

        val patternGateMessageCount = unmatchedTemplates + ambiguousTemplates + extractionFailed
        val candidateEval = if (patternGateMessageCount > 0) {
            evaluateCandidatePatternsForScan(messages, authorizedSenders, items)
        } else {
            CandidatePatternEvaluation(0, emptyList())
        }

        val preview = ScanPreview(
            mode = mode, configuredSenderCount = authorizedSenders.size, scannedMessages = rawSms,
            recognizedTransactions = recognized, nonFinancialMessages = nonFinancial, unparsedMessages = unparsed,
            unregisteredSenderMessages = unregistered, otpOrAuthMessages = otpOrAuth,
            duplicateTransactions = duplicates, needsReviewTransactions = needsReview,
            beforeTrackingStartCount = beforeTracking,
            unmatchedTemplateMessages = unmatchedTemplates,
            ambiguousTemplateMessages = ambiguousTemplates,
            extractionFailedMessages = extractionFailed,
            candidatePatternCount = candidateEval.count,
            candidateDiagnostics = candidateEval.diagnostics,
            templateFailureCounts = templateFailureCounts.toMap(),
            approvedTemplateCoverage = templateCoverage.values.map {
                ApprovedTemplateCoverage(
                    templateId = it.templateId,
                    displayName = it.displayName,
                    transactionType = it.transactionType,
                    canonicalSignature = it.canonicalSignature,
                    active = it.active,
                    approved = it.approved,
                    requiredPlaceholders = it.requiredPlaceholders,
                    optionalPlaceholders = it.optionalPlaceholders,
                    historicalMessageCount = it.historicalMessageCount,
                    currentCandidateMessages = it.currentCandidateMessages,
                    successfulMatches = it.successfulMatches,
                    failureCounts = it.failures.toMap(),
                )
            }.sortedBy { it.templateId },
            senderTemplateCoverage = senderCoverage.values.map {
                SenderTemplateCoverage(
                    normalizedSender = it.normalizedSender,
                    senderProfileId = it.senderProfileId,
                    approvedTemplatesLoaded = it.approvedTemplatesLoaded,
                    messagesEnteringMatcher = it.messagesEnteringMatcher,
                    matched = it.matched,
                    unmatched = it.unmatched,
                    ambiguous = it.ambiguous,
                )
            }.sortedBy { it.senderProfileId },
            unmatchedTemplateGroups = unmatchedGroups.values.map {
                UnmatchedTemplateGroupDiagnostic(
                    count = it.count,
                    normalizedSender = it.normalizedSender,
                    senderProfileId = it.senderProfileId,
                    closestTemplateId = it.closestTemplateId,
                    closestTemplateName = it.closestTemplateName,
                    closestTemplateTransactionType = it.closestTemplateTransactionType,
                    failureReason = it.failureReason,
                    normalizedStructuralRepresentation = it.normalizedStructuralRepresentation,
                    redactedRepresentativeMessage = it.redactedRepresentativeMessage,
                    matchedAnchors = it.matchedAnchors,
                    failedAnchors = it.failedAnchors,
                )
            }.sortedByDescending { it.count },
            institutionGroups = groups.map { (institution, its) ->
                ScanPreview.InstitutionGroup(
                    institution, its.size,
                    its.count { it.disposition == ImportDisposition.READY },
                    its.count {
                        it.disposition == ImportDisposition.NEEDS_ACCOUNT ||
                            it.disposition == ImportDisposition.NEEDS_CONFIRMATION ||
                            it.disposition == ImportDisposition.NEEDS_INSTITUTION ||
                            it.disposition == ImportDisposition.UNMATCHED_TEMPLATE ||
                            it.disposition == ImportDisposition.AMBIGUOUS_TEMPLATE ||
                            it.disposition == ImportDisposition.TEMPLATE_EXTRACTION_FAILED
                    },
                    0,
                )
            },
            perTransaction = items,
            skippedSenders = ScanPreview.aggregateSkipped(skipBuckets),
            unregisteredSenderGroups = ScanPreview.aggregateUnregisteredSenders(skipBuckets),
            filterFunnel = funnel,
            scanError = engineSetupError?.let { "تعذر تجهيز محرك الاستخراج: $it — الرسائل بلا قالب ما زالت ظاهرة للمراجعة" },
        )
        preview
    }

    private data class CandidatePatternEvaluation(
        val count: Int,
        val diagnostics: List<CandidatePatternDiagnostic>,
    )

    /**
     * Distinct candidate patterns covering pattern-gate SMS in this scan.
     *
     * CRITICAL: pass existing saved patterns (including APPROVED) into discovery.
     * Clusters that match an APPROVED template must NOT be counted as pending.
     * Unregistered senders never appear in gate messages.
     */
    private suspend fun evaluateCandidatePatternsForScan(
        messages: List<SmsMessage>,
        authorizedSenders: Set<String>,
        items: List<ScanPreview.PreviewItem>,
    ): CandidatePatternEvaluation {
        val gateSmsIds = items.filter {
            ScanPreview.isPatternApprovalDisposition(it.disposition)
        }.map { it.smsId }.toSet()
        if (gateSmsIds.isEmpty()) return CandidatePatternEvaluation(0, emptyList())

        val gateMessages = messages.filter { it.id in gateSmsIds }
        val repo = messagePatternRepository
        val diagnostics = mutableListOf<CandidatePatternDiagnostic>()
        val pendingKeys = linkedSetOf<String>()

        gateMessages
            .groupBy { SenderNormalizer.normalize(it.sender).orEmpty() }
            .filterKeys { it.isNotBlank() && it in authorizedSenders }
            .forEach { (senderKey, smsForSender) ->
                val profile = senderProfileRepository?.findByRawSender(smsForSender.first().sender)
                    ?: return@forEach
                val existing = repo?.getForSender(profile.id)?.map { it.definition }.orEmpty()
                val approvedByKey = runtimeEligibleApprovedBySemanticKey(existing)
                val eligibleApprovedIds = approvedByKey.values.mapTo(mutableSetOf()) { it.id }

                val discovered = PatternDiscoveryService.discover(smsForSender, existing)
                    .filter { !it.looksLikeOtpOrMarketing && !it.looksLikeNonFinancial }

                for (cluster in discovered) {
                    val key = cluster.familyKey.ifBlank { cluster.canonicalKey.ifBlank { cluster.signature } }
                    val sampleBody = smsForSender.firstOrNull()?.body
                    val approvedHit = when {
                        key.isNotBlank() -> approvedByKey[key]
                        else -> null
                    } ?: existing.firstOrNull { approved ->
                        com.baraa.masroof.sms.PatternRuntimeEligibility.isEligible(approved) &&
                            !approved.templateText.isNullOrBlank() &&
                            (
                                (!cluster.templateText.isNullOrBlank() &&
                                    com.baraa.masroof.sms.TemplateMatcher.matches(
                                        approved.templateText,
                                        cluster.templateText,
                                    )) ||
                                    (!sampleBody.isNullOrBlank() &&
                                        com.baraa.masroof.sms.TemplateMatcher.matches(
                                            approved.templateText,
                                            sampleBody,
                                        ))
                                )
                    }
                    if (approvedHit != null ||
                        cluster.matchedPatternId in eligibleApprovedIds
                    ) {
                        android.util.Log.i(
                            "SmsImport",
                            "candidate_suppressed_by_approved key=$key approvedId=${approvedHit?.id ?: cluster.matchedPatternId}",
                        )
                        continue
                    }
                    if (cluster.matchedPatternStatus ==
                        com.baraa.masroof.data.db.MessagePatternStatus.IGNORED
                    ) {
                        continue
                    }
                    val identity = "${profile.id}|$key"
                    if (!pendingKeys.add(identity)) continue
                    diagnostics += CandidatePatternDiagnostic(
                        senderRaw = smsForSender.firstOrNull()?.sender,
                        senderNormalized = senderKey,
                        senderProfileId = profile.id,
                        senderRegistered = true,
                        candidatePatternId = cluster.matchedPatternId?.takeIf {
                            cluster.matchedPatternStatus ==
                                com.baraa.masroof.data.db.MessagePatternStatus.UNKNOWN
                        },
                        canonicalKey = key,
                        transactionType = cluster.transactionTypeName,
                        messageCount = cluster.messageCount,
                        approvedEquivalentId = null,
                        reason = when {
                            cluster.matchedPatternId != null &&
                                cluster.matchedPatternStatus ==
                                com.baraa.masroof.data.db.MessagePatternStatus.UNKNOWN ->
                                "existing_unknown_candidate"
                            else -> "no_approved_template_for_semantic_schema"
                        },
                    )
                }
            }


        return CandidatePatternEvaluation(diagnostics.size, diagnostics)
    }

    private suspend fun registeredSenderKeys(): Set<String> {
        // SenderProfile cross-ref (owned) ∪ trained profiles ∪ institution mapping.
        val fromProfiles = senderProfileRepository?.activeOwnedSenderKeys().orEmpty().toMutableSet()
        val ownedInstitutions = RoomFinancialAccountRepository(database.financialAccountDao()).getOwnedActive()
            .mapNotNull { it.institutionName?.trim()?.lowercase() }.toSet()
        database.senderInstitutionMappingDao().getActive()
            .filter { it.institutionName.trim().lowercase() in ownedInstitutions }
            .mapTo(fromProfiles) { SenderNormalizer.normalize(it.senderKey).orEmpty() }
        // Trained profiles without account link still authorize for pattern review import path.
        fromProfiles += senderProfileRepository?.activeProfileKeys().orEmpty()
        return fromProfiles.filter { it.isNotBlank() }.toSet()
    }

    /**
     * Step 2 — atomic commit. Mirrors [scan] but writes everything inside
     * ONE Room transaction. Returns a structured [SmsImportResult].
     */
    suspend fun commit(
        scanPreview: ScanPreview,
        trackingStartDate: LocalDate?,
        importedSms: List<SmsMessage>,
        mode: SmsImportCommitMode = SmsImportCommitMode.ALL,
        allowOncePatternIds: Set<Long> = emptySet(),
    ): SmsImportResult = withContext(Dispatchers.IO) {
        if (scanPreview.scannedMessages == 0) return@withContext SmsImportResult.Empty

        val categories = categoryRepository.getAll()
        val ownedAccounts = RoomFinancialAccountRepository(database.financialAccountDao()).getOwnedActive()
        val merchantMemory = merchantMemoryRepository.getAll()
        val identifierSnapshots = accountIdentifierRepository.getActiveSnapshots()
        val accountsBySender = senderProfileRepository?.accountsBySenderKeyMap().orEmpty()
        val engine = RuleEngineFactory.build(categories, feeCategoryId = null)
        val context = RuleContext(ownedAccounts, merchantMemory, categories, identifierSnapshots, accountsBySender)

        var imported = 0
        var linkedCount = 0
        var reviewCount = 0
        var duplicates = 0
        var preTracking = 0
        var postedCount = 0
        val affectedIds = linkedSetOf<Long>()
        val affectedSummaries = mutableMapOf<Long, SmsImportResult.AffectedAccountSummary>()
        val log = mutableListOf<SmsImportResult.TransactionImportLog>()
        val previewBySms = scanPreview.perTransaction.associateBy { it.smsId }
        val registeredSenders = registeredSenderKeys()
        val authorizedSenders = registeredSenders

        fun acceptDisposition(disposition: ImportDisposition): Boolean = when (mode) {
            SmsImportCommitMode.ALL -> true
            SmsImportCommitMode.READY_ONLY -> disposition == ImportDisposition.READY
            SmsImportCommitMode.PATTERN_CANDIDATES_ONLY ->
                disposition == ImportDisposition.UNMATCHED_TEMPLATE
            SmsImportCommitMode.REVIEW_CANDIDATES ->
                ScanPreview.isReviewDisposition(disposition) ||
                    disposition == ImportDisposition.BEFORE_TRACKING_START
            SmsImportCommitMode.MESSAGE_REVIEW_ONLY ->
                ScanPreview.isMessageReviewDisposition(disposition)
        }

        database.withTransaction {
            for (sms in importedSms) {
                if (scanPreview.mode == SmsImportMode.REGISTERED_ACCOUNTS_ONLY &&
                    SenderNormalizer.normalize(sms.sender) !in authorizedSenders
                ) {
                    continue
                }
                val previewItem = previewBySms[sms.id]
                if (previewItem == null) continue
                if (!acceptDisposition(previewItem.disposition)) continue
                if (previewItem.disposition == ImportDisposition.UNMATCHED_TEMPLATE) {
                    val profile = senderProfileRepository?.findByRawSender(sms.sender)
                    if (profile != null && messagePatternRepository != null) {
                        val built = com.baraa.masroof.sms.MessageTemplateEngine.buildFromSms(sms.body)
                        messagePatternRepository.ensureUnknown(
                            senderProfileId = profile.id,
                            signature = built.signature,
                            friendlyName = built.displayName,
                            templateText = built.templateText,
                            body = sms.body,
                        )
                    }
                    continue
                }
                when (previewItem.disposition) {
                    ImportDisposition.EXACT_DUPLICATE -> {
                        duplicates++
                        continue
                    }
                    ImportDisposition.UNREGISTERED_SENDER,
                    ImportDisposition.IGNORED,
                    ImportDisposition.UNPARSED,
                    ImportDisposition.AMBIGUOUS_TEMPLATE,
                    ImportDisposition.TEMPLATE_EXTRACTION_FAILED,
                    -> continue
                    else -> Unit
                }
                val profile = senderProfileRepository?.findByRawSender(sms.sender)
                val definitionPatterns = if (profile != null && messagePatternRepository != null) {
                    messagePatternRepository.getForSender(profile.id)
                } else {
                    emptyList()
                }
                if (profile == null) continue
                if (com.baraa.masroof.sms.MessagePatternMatcher.isIgnored(sms.body, definitionPatterns)) continue
                val outcome = com.baraa.masroof.sms.TemplateResolutionService.resolve(
                    sender = sms.sender,
                    body = sms.body,
                    smsTimestampMillis = sms.timestamp.takeIf { it > 0L },
                    patterns = definitionPatterns,
                    allowOncePatternIds = allowOncePatternIds,
                )
                val parsed = when (outcome) {
                    is com.baraa.masroof.sms.TemplateResolutionResult.Matched -> {
                        // A template edited between preview and commit must not
                        // silently change the imported transaction.
                        if (outcome.pattern.definition.id != previewItem.patternRevisionId) {
                            reviewCount++
                            continue
                        }
                        outcome.parsed
                    }
                    is com.baraa.masroof.sms.TemplateResolutionResult.Unmatched -> {
                        continue
                    }
                    is com.baraa.masroof.sms.TemplateResolutionResult.Ambiguous -> continue
                }
                if (parsed.amount == null) continue
                val entity = buildEntity(sms, parsed, engine, context) ?: continue
                if (previewItem.disposition == ImportDisposition.POSSIBLE_DUPLICATE) {
                    val marked = entity.copy(
                        needsReview = true,
                        userConfirmed = false,
                        exclusionReason = "محتمل تكرار لعملية موجودة",
                        postingStatus = com.baraa.masroof.ledger.TransactionPostingStatus.NEEDS_REVIEW,
                        accountLinkNeedsReview = true,
                        updatedAt = nowProvider(),
                    )
                    val id = transactionRepository.insert(marked)
                    if (id > 0L) {
                        rememberSmsBody(id, sms.body)
                        imported++
                        reviewCount++
                        log += SmsImportResult.TransactionImportLog(
                            smsId = sms.id, sender = null, amount = entity.amount,
                            transactionType = entity.transactionType.name,
                            linkedAccountId = null, journalEntryId = null,
                            debitAccountId = null, creditAccountId = null,
                            postingStatus = "POSSIBLE_DUPLICATE",
                            includedInCalculatedBalance = false,
                        )
                    } else {
                        duplicates++
                    }
                    continue
                }
                if (previewItem.isBeforeTrackingStart) {
                    val marked = entity.copy(
                        needsReview = true, userConfirmed = false,
                        exclusionReason = "عملية قبل تاريخ بداية المتابعة",
                        postingStatus = com.baraa.masroof.ledger.TransactionPostingStatus.NEEDS_REVIEW,
                        updatedAt = nowProvider(),
                    )
                    val id = transactionRepository.insert(marked)
                    if (id > 0L) {
                        rememberSmsBody(id, sms.body)
                        preTracking++
                        imported++
                        reviewCount++
                        log += SmsImportResult.TransactionImportLog(
                            smsId = sms.id, sender = null, amount = entity.amount,
                            transactionType = entity.transactionType.name,
                            linkedAccountId = null, journalEntryId = null,
                            debitAccountId = null, creditAccountId = null,
                            postingStatus = "BEFORE_TRACKING_START",
                            includedInCalculatedBalance = false,
                        )
                    } else {
                        duplicates++
                    }
                    continue
                }
                // Credit-limit notices / declined IGNORED rows: store as VOIDED, never journal.
                if (entity.financialTreatment == FinancialTreatment.IGNORED ||
                    entity.transactionType == TransactionType.NON_FINANCIAL
                ) {
                    val voided = entity.copy(
                        financialTreatment = FinancialTreatment.IGNORED,
                        needsReview = false,
                        userConfirmed = true,
                        postingStatus = com.baraa.masroof.ledger.TransactionPostingStatus.VOIDED,
                        exclusionReason = entity.exclusionReason
                            ?: "تغيير حد ائتماني أو إشعار غير مالي — مستبعد من الأرصدة",
                        updatedAt = nowProvider(),
                    )
                    val id = transactionRepository.insert(voided)
                    if (id > 0L) {
                        rememberSmsBody(id, sms.body)
                        imported++
                        val match = accountMatcher.match(
                            voided,
                            ownedAccounts,
                            accountIdentifierRepository,
                            parsed.identifierEvidence,
                        )
                        val card = match.account?.takeIf {
                            it.accountType == com.baraa.masroof.transaction.AccountType.CREDIT_CARD
                        }
                        val limit = entity.amount
                        if (card != null && limit != null &&
                            (entity.transactionType == TransactionType.NON_FINANCIAL ||
                                entity.exclusionReason?.contains("حد") == true ||
                                parsed.transactionType == TransactionType.NON_FINANCIAL)
                        ) {
                            runCatching {
                                com.baraa.masroof.ledger.CreditLimitUpdater.applyToAccount(
                                    account = card,
                                    newLimit = limit,
                                    dao = database.financialAccountDao(),
                                    now = nowProvider,
                                )
                            }
                        }
                        log += SmsImportResult.TransactionImportLog(
                            smsId = sms.id, sender = null, amount = entity.amount,
                            transactionType = entity.transactionType.name,
                            linkedAccountId = card?.id,
                            journalEntryId = null,
                            debitAccountId = null, creditAccountId = null,
                            postingStatus = "IGNORED",
                            includedInCalculatedBalance = false,
                        )
                    } else {
                        duplicates++
                    }
                    continue
                }
                val match = accountMatcher.match(entity, ownedAccounts, accountIdentifierRepository, parsed.identifierEvidence)
                val twoSidedOk = !entity.financialTreatment.requiresTwoAccounts ||
                    match.destinationAccountCandidate != null ||
                    entity.financialTreatment == FinancialTreatment.CASH_WITHDRAWAL
                val hasLinkedAccount = match.account != null && !match.needsReview && twoSidedOk
                val linked = entity.withAccountMatch(match)
                val txId = transactionRepository.insert(linked)
                if (txId <= 0L) {
                    duplicates++
                    continue
                }
                rememberSmsBody(txId, sms.body)
                imported++

                // Per spec: NEEDS_REVIEW transactions must NOT affect balance
                // until the user confirms them. Skip journal creation entirely.
                if (!hasLinkedAccount) {
                    reviewCount++
                    log += SmsImportResult.TransactionImportLog(
                        smsId = sms.id, sender = null, amount = linked.amount,
                        transactionType = linked.transactionType.name,
                        linkedAccountId = null, journalEntryId = null,
                        debitAccountId = null, creditAccountId = null,
                        postingStatus = "NEEDS_REVIEW",
                        includedInCalculatedBalance = false,
                    )
                    continue
                }

                // Room returns the generated identity separately. A journal
                // references transactions by FK, so never generate from id=0.
                val persistedLinked = linked.copy(id = txId)
                val source = ownedAccounts.firstOrNull { it.id == persistedLinked.sourceAccountId }
                val destination = ownedAccounts.firstOrNull { it.id == persistedLinked.destinationAccountId }
                    ?: persistedLinked.destinationAccountId?.let { id ->
                        database.financialAccountDao().getById(id)?.toDomain()
                    }
                val draft = journalGenerationService.generate(persistedLinked, source, destination)

                if (draft != null) {
                    val postedDraft = draft.copy(postingStatus = JournalPostingStatus.POSTED)
                    val journalId = ledgerRepository.create(postedDraft)
                    if (journalId > 0L) {
                        transactionRepository.update(
                            persistedLinked.copy(
                                linkedJournalEntryId = journalId,
                                postingStatus = com.baraa.masroof.ledger.TransactionPostingStatus.POSTED,
                                updatedAt = nowProvider(),
                            ),
                        )
                        val postings = database.journalDao().getPostingsFor(journalId)
                        linkedCount++
                        // Count this transaction as POSTED only when the journal
                        // has at least one DEBIT and one CREDIT posting in the
                        // matching currencies, to keep the structural invariant.
                        val currencies = postings.map { it.currency }.toSet()
                        val currencyBalanced = currencies.all { cur ->
                            val debits = postings.filter { it.postingSide == PostingSide.DEBIT && it.currency == cur }.sumOf { it.amount }
                            val credits = postings.filter { it.postingSide == PostingSide.CREDIT && it.currency == cur }.sumOf { it.amount }
                            debits.compareTo(credits) == 0
                        }
                        if (currencyBalanced) {
                            postedCount++
                            affectedIds.addAll(listOfNotNull(linked.sourceAccountId, linked.destinationAccountId).distinct())
                        }
                        log += SmsImportResult.TransactionImportLog(
                            smsId = sms.id, sender = null, amount = linked.amount,
                            transactionType = linked.transactionType.name,
                            linkedAccountId = linked.sourceAccountId ?: linked.destinationAccountId,
                            journalEntryId = journalId,
                            debitAccountId = postings.firstOrNull { it.postingSide == PostingSide.DEBIT }?.accountId,
                            creditAccountId = postings.firstOrNull { it.postingSide == PostingSide.CREDIT }?.accountId,
                            postingStatus = "POSTED",
                            includedInCalculatedBalance = true,
                        )
                    } else {
                        reviewCount++
                        log += SmsImportResult.TransactionImportLog(
                            smsId = sms.id, sender = null, amount = linked.amount,
                            transactionType = linked.transactionType.name,
                            linkedAccountId = null, journalEntryId = null,
                            debitAccountId = null, creditAccountId = null,
                            postingStatus = "JOURNAL_FAILED",
                            includedInCalculatedBalance = false,
                        )
                    }
                } else {
                    reviewCount++
                    log += SmsImportResult.TransactionImportLog(
                        smsId = sms.id, sender = null, amount = linked.amount,
                        transactionType = linked.transactionType.name,
                        linkedAccountId = linked.sourceAccountId ?: linked.destinationAccountId,
                        journalEntryId = null,
                        debitAccountId = null, creditAccountId = null,
                        postingStatus = "NO_DRAFT",
                        includedInCalculatedBalance = false,
                    )
                }
            }

            if (affectedIds.isNotEmpty()) {
                val freshJournals = database.journalDao().getAllForRecalculation()
                val activeAccounts = database.financialAccountDao().getActive()
                val summary: Map<Long, AccountSummary> = AccountBalanceCalculator.calculateMany(activeAccounts, freshJournals, zoneId, nowProvider)
                for ((id, s) in summary) {
                    if (id in affectedIds) {
                        affectedSummaries[id] = SmsImportResult.AffectedAccountSummary(
                            accountId = s.accountId,
                            accountName = s.accountDisplayLabel,
                            openingBalance = s.openingBalance,
                            openingBalanceDate = s.openingBalanceDate,
                            totalCredits = s.totalCredits,
                            totalDebits = s.totalDebits,
                            calculatedBalance = s.calculatedBalance,
                            lastUpdatedAt = s.lastRecalculationAt,
                            accountNature = s.accountNature,
                            moneyIn = s.moneyIn,
                            moneyOut = s.moneyOut,
                        )
                    }
                }
            }
        }

        SmsImportResult(
            scannedMessages = scanPreview.scannedMessages,
            recognizedTransactions = scanPreview.recognizedTransactions,
            readyTransactions = scanPreview.readyCount,
            importedTransactions = imported,
            linkedTransactions = linkedCount,
            postedTransactions = postedCount,
            needsReviewTransactions = reviewCount,
            duplicateTransactions = duplicates,
            unparsedMessages = scanPreview.unparsedMessages,
            nonFinancialMessages = scanPreview.nonFinancialMessages,
            beforeTrackingStartCount = preTracking,
            updatedAccountIds = affectedIds.toList(),
            affectedAccounts = affectedSummaries.values.sortedBy { it.accountName },
            perTransactionLog = log,
            trackingStartDateHint = trackingStartDate,
            importedAt = nowProvider(),
        )
    }

    /** Parser identity is an institution hint only; it never chooses an account. */
    private fun parserInstitution(parsed: ParsedTransaction): String? = when (parsed.parserName.lowercase()) {
        "alrajhi", "al rajhi" -> "مصرف الراجحي"
        "aljazira", "al jazira" -> "بنك الجزيرة"
        "alahli", "snb", "saudi national bank" -> "البنك الأهلي السعودي"
        "d360" -> "D360"
        "stc", "stc bank" -> "STC Bank"
        else -> null
    }

    /** Account placement is treatment-specific; category is not account evidence. */
    private suspend fun TransactionEntity.withAccountMatch(match: AccountMatcher.Match): TransactionEntity {
        // Sender-only / ambiguous proposals must not sticky-link an account id —
        // otherwise later accounts from the same bank cannot rematch these rows.
        if (match.needsReview || match.account == null) {
            return copy(
                sourceAccountId = null,
                destinationAccountId = null,
                accountLinkSource = com.baraa.masroof.ledger.AccountLinkSource.UNLINKED,
                accountLinkConfidence = match.confidence,
                accountLinkNeedsReview = true,
                needsReview = true,
                postingStatus = com.baraa.masroof.ledger.TransactionPostingStatus.NEEDS_REVIEW,
            )
        }
        val accountId = match.account.id
        val sourceId = when (financialTreatment) {
            FinancialTreatment.EXPENSE, FinancialTreatment.BANK_FEE, FinancialTreatment.CASH_WITHDRAWAL,
            FinancialTreatment.INTERNAL_TRANSFER, FinancialTreatment.CREDIT_CARD_PAYMENT, FinancialTreatment.INVESTMENT -> accountId
            else -> null
        }
        val destinationId = when (financialTreatment) {
            FinancialTreatment.INCOME, FinancialTreatment.REFUND -> accountId
            FinancialTreatment.INTERNAL_TRANSFER, FinancialTreatment.CREDIT_CARD_PAYMENT, FinancialTreatment.INVESTMENT ->
                match.destinationAccountCandidate?.id
            else -> null
        }
        return copy(
            sourceAccountId = sourceId,
            destinationAccountId = destinationId,
            accountLinkSource = match.source,
            accountLinkConfidence = match.confidence,
            accountLinkNeedsReview = false,
            needsReview = needsReview,
            postingStatus = com.baraa.masroof.ledger.TransactionPostingStatus.NEEDS_REVIEW,
        )
    }

    private fun isLikelyFinancialSender(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val s = sender.lowercase()
        return s.contains("bank") || s.contains("بنك") || s.length <= 6
    }

    /**
     * Single-message entry point used by the SMS_RECEIVED receiver. This
     * MUST call the canonical [scan] + [commit] pipeline so the manual
     * import flow and the automatic import flow share one code path.
     */
    suspend fun processIncoming(
        messages: List<SmsMessage>,
        trackingStartDate: LocalDate? = null,
    ): SmsImportResult {
        if (messages.isEmpty()) return SmsImportResult.Empty
        val preview = scan(messages, trackingStartDate)
        return commit(preview, trackingStartDate, messages)
    }

    private fun buildEntity(
        sms: SmsMessage,
        p: ParsedTransaction,
        engine: RuleEngine,
        context: RuleContext,
    ): TransactionEntity? {
        val amount = p.amount ?: return null
        val timestamp = nowProvider()
        val fingerprint = TransactionFingerprint.compute(
            sender = sms.sender,
            smsTimestamp = sms.timestamp,
            amount = amount,
            currency = p.currency,
            type = p.transactionType,
            merchant = p.merchant,
            lastFour = p.accountOrCardLastFourDigits,
        )
        val similarityKey = TransactionFingerprint.generateSimilarityKey(
            sender = sms.sender,
            amount = amount,
            currency = p.currency,
            type = p.transactionType,
            merchant = p.merchant,
            lastFour = p.accountOrCardLastFourDigits,
            date = p.transactionDate,
            time = p.transactionTime,
        )
        val merchantKey = MerchantNormalizer.normalize(p.merchant)
        val input = com.baraa.masroof.rules.RuleInput(
            sender = sms.sender,
            body = sms.body,
            amount = amount,
            currency = p.currency,
            type = p.transactionType,
            status = p.status,
            date = p.transactionDate,
            time = p.transactionTime,
            normalizedMerchantKey = merchantKey,
            parsed = p,
        )
        val verdict = engine.classify(input, context)
        val audited = if (verdict.financialTreatment == FinancialTreatment.PENDING_REVIEW) {
            val audit = LocalTreatmentAuditor.audit(
                type = p.transactionType,
                body = sms.body,
                currentTreatment = FinancialTreatment.PENDING_REVIEW,
                hasConfirmedTwoOwnedSides = false,
            )
            // Apply single-sided auto treatments; leave two-sided / unclear for review.
            if (audit.autoApply && !audit.treatment.requiresTwoAccounts) {
                verdict.copy(
                    financialTreatment = audit.treatment,
                    confidence = maxOf(verdict.confidence, audit.confidence),
                    reason = audit.reasonAr,
                    excludeFromSpending = audit.treatment != FinancialTreatment.EXPENSE &&
                        audit.treatment != FinancialTreatment.BANK_FEE,
                )
            } else {
                verdict
            }
        } else {
            verdict
        }
        val dateSource = when {
            p.transactionDate != null && p.parsingNotes.any { it.startsWith("date from message body") } ->
                com.baraa.masroof.data.db.DateSource.FROM_BODY
            p.transactionDate != null -> com.baraa.masroof.data.db.DateSource.FROM_SMS_METADATA
            else -> com.baraa.masroof.data.db.DateSource.UNKNOWN
        }
        return TransactionEntity(
            id = 0,
            uniqueFingerprint = fingerprint,
            smsTimestamp = sms.timestamp,
            originalSender = sms.sender,
            transactionType = p.transactionType,
            amount = amount,
            currency = p.currency,
            merchantOrBeneficiary = p.merchant,
            accountOrCardLastFourDigits = p.accountOrCardLastFourDigits,
            transactionDate = p.transactionDate,
            transactionTime = p.transactionTime,
            status = p.status,
            confidence = p.confidence,
            parsingNotes = p.parsingNotes,
            dateSource = dateSource,
            createdAt = timestamp,
            updatedAt = timestamp,
            transactionSimilarityKey = similarityKey,
            financialTreatment = audited.financialTreatment,
            categoryId = audited.categoryId,
            categorySource = audited.source,
            categoryConfidence = audited.confidence,
            needsReview = audited.financialTreatment == FinancialTreatment.PENDING_REVIEW,
            userConfirmed = false,
            exclusionReason = if (audited.excludeFromSpending) audited.reason else null,
        )
    }
}
