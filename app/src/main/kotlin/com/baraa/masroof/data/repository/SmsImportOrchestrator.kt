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
import com.baraa.masroof.transaction.BankParserRegistry
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

enum class ImportDisposition {
    READY, NEEDS_ACCOUNT, NEEDS_CONFIRMATION, NEEDS_INSTITUTION, UNPARSED,
    EXACT_DUPLICATE, POSSIBLE_DUPLICATE, BEFORE_TRACKING_START, UNREGISTERED_SENDER, IGNORED,
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
    val institutionGroups: List<InstitutionGroup> = emptyList(),
    val perTransaction: List<PreviewItem> = emptyList(),
    val discoveredSenders: List<DiscoveredSender> = emptyList(),
    /** Aggregated skips (no SMS bodies) for «رسائل لم تُستورد». */
    val skippedSenders: List<SkippedSenderGroup> = emptyList(),
) {
    data class DiscoveredSender(val sender: String, val messageCount: Int, val latestTimestamp: Long, val likelyInstitution: String?)

    enum class SkipReason {
        UNREGISTERED_SENDER,
        NO_AMOUNT,
        NON_FINANCIAL,
        OTP_OR_AUTH,
        UNKNOWN_PATTERN,
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

    /** Exclusive count of messages that need user review after import. */
    val reviewDispositionCount: Int
        get() = perTransaction.count {
            it.disposition == ImportDisposition.NEEDS_ACCOUNT ||
                it.disposition == ImportDisposition.NEEDS_CONFIRMATION ||
                it.disposition == ImportDisposition.NEEDS_INSTITUTION
        }

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
    )

    companion object {
        const val MAX_SKIPPED_GROUPS = 10

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
    /** Persist SMS body locally for on-device link assist. Never logs the body. */
    private suspend fun rememberSmsBody(transactionId: Long, body: String?) {
        if (transactionId <= 0L) return
        runCatching { smsBodyRepository?.save(transactionId, body) }
    }
    /**
     * Step 1 — read-only scan. Returns a [ScanPreview] describing what
     * the eventual commit would do.
     */
    suspend fun scan(
        messages: List<SmsMessage>,
        trackingStartDate: LocalDate?,
        mode: SmsImportMode = SmsImportMode.REGISTERED_ACCOUNTS_ONLY,
    ): ScanPreview = withContext(Dispatchers.IO) {
        val registeredSenders = registeredSenderKeys()
        val authorizedSenders = registeredSenders

        if (mode == SmsImportMode.REGISTERED_ACCOUNTS_ONLY && authorizedSenders.isEmpty()) {
            return@withContext ScanPreview(mode = mode, configuredSenderCount = 0, hasRegisteredSenders = false)
        }
        if (mode == SmsImportMode.DISCOVER_NEW_SENDERS) {
            val discoveries = messages.asSequence()
                .filter { sms -> !com.baraa.masroof.sms.BankSmsFilter.isOtpOrAuthenticationMessage(sms.body) }
                .filter { sms -> SenderNormalizer.normalize(sms.sender) !in authorizedSenders }
                .filter { isLikelyFinancialSender(it.sender) || com.baraa.masroof.sms.BankSmsFilter.classifyMessage(it.sender, it.body).isMatch }
                .groupBy { it.sender?.trim().orEmpty() }
                .filterKeys { it.isNotBlank() }
                .map { (sender, rows) -> ScanPreview.DiscoveredSender(sender, rows.size, rows.maxOf { it.timestamp }, null) }
                .sortedByDescending { it.latestTimestamp }
            return@withContext ScanPreview(
                mode = mode, configuredSenderCount = authorizedSenders.size, scannedMessages = messages.size,
                unregisteredSenderMessages = messages.count { SenderNormalizer.normalize(it.sender) !in authorizedSenders },
                discoveredSenders = discoveries,
            )
        }

        val categories = categoryRepository.getAll()
        val ownedAccounts = RoomFinancialAccountRepository(database.financialAccountDao()).getOwnedActive()
        val merchantMemory = merchantMemoryRepository.getAll()
        val identifierSnapshots = accountIdentifierRepository.getActiveSnapshots()
        val accountsBySender = senderProfileRepository?.accountsBySenderKeyMap().orEmpty()
        val engine = RuleEngineFactory.build(categories, feeCategoryId = null)
        val context = RuleContext(ownedAccounts, merchantMemory, categories, identifierSnapshots, accountsBySender)
        var scanned = 0; var recognized = 0; var nonFinancial = 0; var unparsed = 0
        var unregistered = 0; var otpOrAuth = 0; var duplicates = 0; var needsReview = 0; var beforeTracking = 0
        val groups = linkedMapOf<String, MutableList<ScanPreview.PreviewItem>>()
        val items = ArrayList<ScanPreview.PreviewItem>()
        val batchTimestampsByKey = mutableMapOf<String, MutableList<Long>>()
        val skipBuckets = linkedMapOf<Pair<String, ScanPreview.SkipReason>, ScanPreview.SkipAccum>()
        fun bumpSkip(sender: String?, reason: ScanPreview.SkipReason, body: String?, timestamp: Long) {
            val key = (sender?.trim().orEmpty().ifBlank { "—" }) to reason
            val acc = skipBuckets.getOrPut(key) { ScanPreview.SkipAccum() }
            acc.count++
            if (timestamp > acc.latestTimestamp) acc.latestTimestamp = timestamp
            if (acc.redactedSample == null && !body.isNullOrBlank()) {
                val redacted = com.baraa.masroof.diagnostics.TextSanitizer.sanitize(body)
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(240)
                if (redacted.isNotBlank()) acc.redactedSample = redacted
            }
        }

        for (sms in messages) {
            scanned++
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
            val profile = senderProfileRepository?.findByRawSender(sms.sender)
            val definitionPatterns = if (profile != null && messagePatternRepository != null) {
                messagePatternRepository.getForSender(profile.id)
            } else {
                emptyList()
            }
            val hasDefinitionPatterns = definitionPatterns.any {
                it.definition.status == com.baraa.masroof.data.db.MessagePatternStatus.APPROVED ||
                    it.definition.status == com.baraa.masroof.data.db.MessagePatternStatus.DEPRECATED ||
                    it.definition.status == com.baraa.masroof.data.db.MessagePatternStatus.IGNORED
            }

            if (hasDefinitionPatterns) {
                if (com.baraa.masroof.sms.MessagePatternMatcher.isIgnored(sms.body, definitionPatterns)) {
                    otpOrAuth++
                    bumpSkip(sms.sender, ScanPreview.SkipReason.OTP_OR_AUTH, sms.body, sms.timestamp)
                    continue
                }
                val defMatch = com.baraa.masroof.sms.MessagePatternMatcher.match(sms.body, definitionPatterns)
                if (defMatch == null) {
                    if (profile != null && messagePatternRepository != null) {
                        val signature = com.baraa.masroof.sms.SmsStructureNormalizer.signatureFromBody(sms.body)
                        messagePatternRepository.ensureUnknown(
                            senderProfileId = profile.id,
                            signature = signature,
                            friendlyName = com.baraa.masroof.sms.SmsStructureNormalizer.friendlyNameHint(sms.body),
                        )
                    }
                    bumpSkip(sms.sender, ScanPreview.SkipReason.UNKNOWN_PATTERN, sms.body, sms.timestamp)
                    continue
                }
                if (defMatch.pattern.definition.status == com.baraa.masroof.data.db.MessagePatternStatus.IGNORED) {
                    otpOrAuth++
                    bumpSkip(sms.sender, ScanPreview.SkipReason.OTP_OR_AUTH, sms.body, sms.timestamp)
                    continue
                }
            }

            var parsed = BankParserRegistry.parse(sms.sender, sms.body, sms.timestamp.takeIf { it > 0L })
            if (hasDefinitionPatterns) {
                val defMatch = com.baraa.masroof.sms.MessagePatternMatcher.match(sms.body, definitionPatterns)
                if (defMatch != null &&
                    defMatch.pattern.definition.status != com.baraa.masroof.data.db.MessagePatternStatus.IGNORED
                ) {
                    val extracted = com.baraa.masroof.sms.PatternFieldExtractor.extract(sms.body, defMatch.pattern)
                    parsed = com.baraa.masroof.sms.PatternFieldExtractor.toParsedTransaction(extracted, parsed)
                }
            }
            if (parsed.amount == null) {
                if (isLikelyFinancialSender(sms.sender) || hasDefinitionPatterns) {
                    unparsed++
                    bumpSkip(sms.sender, ScanPreview.SkipReason.NO_AMOUNT, sms.body, sms.timestamp)
                } else {
                    nonFinancial++
                    bumpSkip(sms.sender, ScanPreview.SkipReason.NON_FINANCIAL, sms.body, sms.timestamp)
                }
                continue
            }
            val entity = buildEntity(sms, parsed, engine, context)
            if (entity == null) {
                unparsed++
                bumpSkip(sms.sender, ScanPreview.SkipReason.NO_AMOUNT, sms.body, sms.timestamp)
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
            val match = accountMatcher.match(entity, ownedAccounts, accountIdentifierRepository, parsed.identifierEvidence)
            val before = trackingStartDate != null && entity.transactionDate != null && entity.transactionDate.isBefore(trackingStartDate)
            val incompleteTwoSided = entity.financialTreatment.requiresTwoAccounts &&
                match.account != null &&
                !match.needsReview &&
                match.destinationAccountCandidate == null
            // Confirmed last-four only: sender-only proposals are not "matched" for READY.
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
                ImportDisposition.POSSIBLE_DUPLICATE -> duplicates++ // surface in duplicate bucket for UI counts
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
                sender = sms.sender, parsedInstitution = parserInstitution(parsed), parserIdentity = parsed.parserName,
                knownInstitutionNames = com.baraa.masroof.ledger.FinancialInstitutionResolver.WELL_KNOWN_INSTITUTIONS.toSet(),
            ).institutionDisplayName
            val previewAccount = match.account?.takeIf { !match.needsReview }
            val item = ScanPreview.PreviewItem(
                sms.id, sms.sender, entity.amount, entity.transactionType, previewAccount?.id,
                previewAccount?.displayName, isDup || isPossibleDup, isReviewNow, before, entity.transactionDate, disposition,
            )
            groups.getOrPut(institutionKey) { mutableListOf() }.add(item)
            items += item
        }
        ScanPreview(
            mode = mode, configuredSenderCount = authorizedSenders.size, scannedMessages = scanned,
            recognizedTransactions = recognized, nonFinancialMessages = nonFinancial, unparsedMessages = unparsed,
            unregisteredSenderMessages = unregistered, otpOrAuthMessages = otpOrAuth,
            duplicateTransactions = duplicates, needsReviewTransactions = needsReview,
            beforeTrackingStartCount = beforeTracking,
            institutionGroups = groups.map { (institution, its) ->
                ScanPreview.InstitutionGroup(
                    institution, its.size,
                    its.count { it.disposition == ImportDisposition.READY },
                    its.count {
                        it.disposition == ImportDisposition.NEEDS_ACCOUNT ||
                            it.disposition == ImportDisposition.NEEDS_CONFIRMATION ||
                            it.disposition == ImportDisposition.NEEDS_INSTITUTION
                    },
                    0,
                )
            },
            perTransaction = items,
            skippedSenders = ScanPreview.aggregateSkipped(skipBuckets),
        )
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

        database.withTransaction {
            for (sms in importedSms) {
                if (scanPreview.mode == SmsImportMode.REGISTERED_ACCOUNTS_ONLY &&
                    SenderNormalizer.normalize(sms.sender) !in authorizedSenders
                ) {
                    continue
                }
                val previewItem = previewBySms[sms.id]
                if (previewItem == null) continue
                when (previewItem.disposition) {
                    ImportDisposition.EXACT_DUPLICATE -> {
                        duplicates++
                        continue
                    }
                    ImportDisposition.UNREGISTERED_SENDER, ImportDisposition.IGNORED, ImportDisposition.UNPARSED -> continue
                    else -> Unit
                }
                val profile = senderProfileRepository?.findByRawSender(sms.sender)
                val definitionPatterns = if (profile != null && messagePatternRepository != null) {
                    messagePatternRepository.getForSender(profile.id)
                } else {
                    emptyList()
                }
                val hasDefinitionPatterns = definitionPatterns.any {
                    it.definition.status == com.baraa.masroof.data.db.MessagePatternStatus.APPROVED ||
                        it.definition.status == com.baraa.masroof.data.db.MessagePatternStatus.DEPRECATED
                }
                if (hasDefinitionPatterns) {
                    val defMatch = com.baraa.masroof.sms.MessagePatternMatcher.match(sms.body, definitionPatterns)
                    if (defMatch == null) continue
                }
                var parsed: ParsedTransaction = BankParserRegistry.parse(sms.sender, sms.body, sms.timestamp.takeIf { it > 0L })
                if (hasDefinitionPatterns) {
                    val defMatch = com.baraa.masroof.sms.MessagePatternMatcher.match(sms.body, definitionPatterns)
                    if (defMatch != null) {
                        val extracted = com.baraa.masroof.sms.PatternFieldExtractor.extract(sms.body, defMatch.pattern)
                        parsed = com.baraa.masroof.sms.PatternFieldExtractor.toParsedTransaction(extracted, parsed)
                    }
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
                    entity.transactionType == TransactionType.CREDIT_LIMIT_CHANGE
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
                            (entity.transactionType == TransactionType.CREDIT_LIMIT_CHANGE ||
                                entity.exclusionReason?.contains("حد") == true ||
                                parsed.transactionType == TransactionType.CREDIT_LIMIT_CHANGE)
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
        if (preview.recognizedTransactions == 0) return SmsImportResult(
            scannedMessages = preview.scannedMessages,
            recognizedTransactions = 0,
            unparsedMessages = preview.unparsedMessages,
            nonFinancialMessages = preview.nonFinancialMessages,
            trackingStartDateHint = trackingStartDate,
            importedAt = nowProvider(),
        )
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
