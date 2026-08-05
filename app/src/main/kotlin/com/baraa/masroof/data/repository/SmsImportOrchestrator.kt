package com.baraa.masroof.data.repository

import androidx.room.withTransaction
import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.data.db.MasroofDatabase
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.AccountBalanceCalculator
import com.baraa.masroof.ledger.AccountMatcher
import com.baraa.masroof.ledger.AccountSummary
import com.baraa.masroof.ledger.JournalGenerationService
import com.baraa.masroof.ledger.LedgerRepository
import com.baraa.masroof.ledger.InstitutionResolution
import com.baraa.masroof.ledger.JournalGeneratedBy
import com.baraa.masroof.ledger.JournalPostingStatus
import com.baraa.masroof.ledger.PostingSide
import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleEngine
import com.baraa.masroof.rules.RuleEngineFactory
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.transaction.BankParserRegistry
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.MerchantNormalizer
import com.baraa.masroof.transaction.ParsedTransaction
import com.baraa.masroof.transaction.TransactionFingerprint
import com.baraa.masroof.transaction.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/**
 * Structured result of a single SMS import operation. Every field has
 * semantic meaning — the UI must NEVER show "تم ربط العمليات" unless
 * [linkedTransactionsCount] includes transactions whose journal+posting
 * changes are committed to Room AND whose account summary was
 * recalculated.
 */
data class SmsImportResult(
    val scannedSmsCount: Int = 0,
    val recognizedFinancialSmsCount: Int = 0,
    val importedTransactionsCount: Int = 0,
    val linkedTransactionsCount: Int = 0,
    val needsReviewCount: Int = 0,
    val duplicateCount: Int = 0,
    val unparsedCount: Int = 0,
    val beforeTrackingStartCount: Int = 0,
    val affectedAccountIds: List<Long> = emptyList(),
    val affectedAccounts: List<AffectedAccountSummary> = emptyList(),
    val perTransactionLog: List<TransactionImportLog> = emptyList(),
    val permissionMissing: Boolean = false,
    val permissionMessage: String? = null,
) {
    data class AffectedAccountSummary(
        val accountId: Long,
        val accountName: String,
        val openingBalance: BigDecimal,
        val totalCredits: BigDecimal,
        val totalDebits: BigDecimal,
        val calculatedBalance: BigDecimal,
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

    val isSuccess: Boolean get() = recognizedFinancialSmsCount > 0 && importedTransactionsCount > 0

    companion object {
        val Empty = SmsImportResult()
        fun permissionMissing(message: String) = Empty.copy(permissionMissing = true, permissionMessage = message)
    }
}

/**
 * Atomic, single-Room-transaction SMS importer.
 *
 * Inside one `database.withTransaction { ... }` block, in this exact order:
 *   1. Insert imported SMS rows (no-op placeholder when persistence disabled).
 *   2. Insert or update parsed transactions (matched on fingerprint).
 *   3. For each new transaction: auto-link to an account (typed
 *      identifier OR learned rule OR typed sender).
 *   4. Generate the journal draft and POST it in the same transaction.
 *   5. Update the transaction posting status to POSTED.
 *   6. Recompute the affected accounts' balances and persist nothing
 *      extra — balances are computed from journals+opening on the fly; no
 *      broken cached values can outlive a restart.
 */
class SmsImportOrchestrator(
    private val database: MasroofDatabase,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val merchantMemoryRepository: MerchantMemoryRepository,
    private val accountIdentifierRepository: AccountIdentifierRepository,
    private val accountMatcher: AccountMatcher,
    private val journalGenerationService: JournalGenerationService,
    private val ledgerRepository: LedgerRepository,
    private val systemAccounts: suspend (com.baraa.masroof.ledger.SystemAccountKey) -> Long,
    private val institutionResolver: com.baraa.masroof.ledger.FinancialInstitutionResolver,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    /**
     * Atomic commit. [permissionGranted] lets callers short-circuit if the
     * user denied the SMS permission; we never scan without permission.
     */
    suspend fun import(
        messages: List<SmsMessage>,
        trackingStartDate: LocalDate?,
        permissionGranted: Boolean,
    ): SmsImportResult = withContext(Dispatchers.IO) {
        if (!permissionGranted) {
            return@withContext SmsImportResult.permissionMissing("تعذر فحص الرسائل لأن إذن قراءة الرسائل غير ممنوح.")
        }
        if (messages.isEmpty()) return@withContext SmsImportResult.Empty

        val categories = categoryRepository.getAll()
        val ownedAccounts = RoomFinancialAccountRepository(database.financialAccountDao()).getOwnedActive()
        val merchantMemory = merchantMemoryRepository.getAll()
        val engine = RuleEngineFactory.build(categories, feeCategoryId = null)
        val context = RuleContext(ownedAccounts, merchantMemory, categories)

        var scanned = 0
        var financial = 0
        var imported = 0
        var linkedCount = 0
        var review = 0
        var duplicates = 0
        var unparsed = 0
        var preTracking = 0
        val affectedIds = linkedSetOf<Long>()
        val affectedSummaries = mutableMapOf<Long, SmsImportResult.AffectedAccountSummary>()
        val log = mutableListOf<SmsImportResult.TransactionImportLog>()

        database.withTransaction {
            for (sms in messages) {
                scanned++
                val parsed: ParsedTransaction = BankParserRegistry.parse(sms.sender, sms.body, sms.timestamp.takeIf { it > 0L })
                if (parsed.amount == null || parsed.confidence < 30) {
                    unparsed++
                    log += SmsImportResult.TransactionImportLog(
                        smsId = sms.id,
                        sender = sms.sender,
                        amount = parsed.amount,
                        transactionType = parsed.transactionType.name,
                        linkedAccountId = null,
                        journalEntryId = null,
                        debitAccountId = null,
                        creditAccountId = null,
                        postingStatus = "UNPARSED",
                        includedInCalculatedBalance = false,
                    )
                    continue
                }
                financial++
                val entity = buildEntity(sms, parsed, engine, context)
                if (entity == null) {
                    unparsed++
                    continue
                }
                if (parsed.status != TransactionStatus.COMPLETED) {
                    log += SmsImportResult.TransactionImportLog(
                        smsId = sms.id, sender = sms.sender, amount = parsed.amount,
                        transactionType = entity.transactionType.name,
                        linkedAccountId = null, journalEntryId = null,
                        debitAccountId = null, creditAccountId = null,
                        postingStatus = "NON_COMPLETED", includedInCalculatedBalance = false,
                    )
                    continue
                }
                if (trackingStartDate != null && entity.transactionDate != null && entity.transactionDate.isBefore(trackingStartDate)) {
                    val marked = entity.copy(
                        needsReview = true, userConfirmed = false,
                        exclusionReason = "عملية قبل تاريخ بداية المتابعة",
                        postingStatus = com.baraa.masroof.ledger.TransactionPostingStatus.NEEDS_REVIEW,
                        updatedAt = nowProvider(),
                    )
                    val id = transactionRepository.insert(marked)
                    if (id > 0L) {
                        preTracking++
                        imported++
                        review++
                        log += SmsImportResult.TransactionImportLog(
                            smsId = sms.id, sender = sms.sender, amount = entity.amount,
                            transactionType = entity.transactionType.name,
                            linkedAccountId = null, journalEntryId = null,
                            debitAccountId = null, creditAccountId = null,
                            postingStatus = "BEFORE_TRACKING_START", includedInCalculatedBalance = false,
                        )
                    } else {
                        duplicates++
                    }
                    continue
                }
                val match = accountMatcher.match(entity, ownedAccounts, accountIdentifierRepository)
                val persist = entity.copy(
                    sourceAccountId = if (match.destinationAccountCandidate != null) null else match.account?.id,
                    destinationAccountId = match.destinationAccountCandidate?.id ?: match.account?.id,
                    accountLinkSource = match.source,
                    accountLinkConfidence = match.confidence,
                    accountLinkNeedsReview = match.needsReview,
                    needsReview = match.account == null,
                )
                val txId = transactionRepository.insert(persist)
                if (txId <= 0L) {
                    duplicates++
                    continue
                }
                imported++
                if (match.account != null) {
                    linkedCount++
                    affectedIds.add(match.account.id)
                    if (match.destinationAccountCandidate != null) {
                        affectedIds.add(match.destinationAccountCandidate.id)
                    }
                } else {
                    review++
                }

                val source = ownedAccounts.firstOrNull { it.id == persist.sourceAccountId }
                val destination = ownedAccounts.firstOrNull { it.id == persist.destinationAccountId }
                val draft = journalGenerationService.generate(persist, source, destination)
                if (draft != null) {
                    val postedDraft = draft.copy(postingStatus = JournalPostingStatus.POSTED)
                    val journalId = ledgerRepository.create(postedDraft)
                    if (journalId > 0L) {
                        transactionRepository.update(
                            persist.copy(
                                id = txId,
                                linkedJournalEntryId = journalId,
                                postingStatus = com.baraa.masroof.ledger.TransactionPostingStatus.POSTED,
                                updatedAt = nowProvider(),
                            ),
                        )
                        val postings = database.journalDao().getPostingsFor(journalId)
                        log += SmsImportResult.TransactionImportLog(
                            smsId = sms.id, sender = sms.sender, amount = persist.amount,
                            transactionType = persist.transactionType.name,
                            linkedAccountId = persist.sourceAccountId ?: persist.destinationAccountId,
                            journalEntryId = journalId,
                            debitAccountId = postings.firstOrNull { it.postingSide == PostingSide.DEBIT }?.accountId,
                            creditAccountId = postings.firstOrNull { it.postingSide == PostingSide.CREDIT }?.accountId,
                            postingStatus = "POSTED",
                            includedInCalculatedBalance = true,
                        )
                    } else {
                        log += SmsImportResult.TransactionImportLog(
                            smsId = sms.id, sender = sms.sender, amount = persist.amount,
                            transactionType = persist.transactionType.name,
                            linkedAccountId = null, journalEntryId = null,
                            debitAccountId = null, creditAccountId = null,
                            postingStatus = "JOURNAL_FAILED",
                            includedInCalculatedBalance = false,
                        )
                    }
                } else {
                    log += SmsImportResult.TransactionImportLog(
                        smsId = sms.id, sender = sms.sender, amount = persist.amount,
                        transactionType = persist.transactionType.name,
                        linkedAccountId = persist.sourceAccountId ?: persist.destinationAccountId,
                        journalEntryId = null,
                        debitAccountId = null, creditAccountId = null,
                        postingStatus = "NO_DRAFT",
                        includedInCalculatedBalance = false,
                    )
                }
            }

            if (affectedIds.isNotEmpty()) {
                val freshJournals = database.journalDao().getAllForRecalculation()
                val accounts = database.financialAccountDao().getActive()
                val summary: Map<Long, AccountSummary> = AccountBalanceCalculator.calculateMany(accounts, freshJournals, zoneId, nowProvider)
                for ((id, s) in summary) {
                    if (id in affectedIds) {
                        affectedSummaries[id] = SmsImportResult.AffectedAccountSummary(
                            accountId = s.accountId,
                            accountName = s.accountDisplayLabel,
                            openingBalance = s.openingBalance,
                            totalCredits = s.totalCredits,
                            totalDebits = s.totalDebits,
                            calculatedBalance = s.calculatedBalance,
                        )
                    }
                }
            }
        }

        SmsImportResult(
            scannedSmsCount = scanned,
            recognizedFinancialSmsCount = financial,
            importedTransactionsCount = imported,
            linkedTransactionsCount = linkedCount,
            needsReviewCount = review,
            duplicateCount = duplicates,
            unparsedCount = unparsed,
            beforeTrackingStartCount = preTracking,
            affectedAccountIds = affectedIds.toList(),
            affectedAccounts = affectedSummaries.values.sortedBy { it.accountName },
            perTransactionLog = log,
        )
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
            financialTreatment = verdict.financialTreatment,
            categoryId = verdict.categoryId,
            categorySource = verdict.source,
            categoryConfidence = verdict.confidence,
            needsReview = verdict.financialTreatment == FinancialTreatment.PENDING_REVIEW,
            userConfirmed = false,
            exclusionReason = if (verdict.excludeFromSpending) verdict.reason else null,
        )
    }
}

/**
 * Pure extension on [AccountMatcher.Match] so the orchestrator can ask for
 * a destination-side candidate without changing the existing matcher
 * signature.
 */
val OrgMarker_Repeated: Boolean = false
