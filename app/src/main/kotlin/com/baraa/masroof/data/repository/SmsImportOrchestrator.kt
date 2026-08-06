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
    val duplicateTransactions: Int = 0,
    val needsReviewTransactions: Int = 0,
    val beforeTrackingStartCount: Int = 0,
    val institutionGroups: List<InstitutionGroup> = emptyList(),
    val perTransaction: List<PreviewItem> = emptyList(),
    val discoveredSenders: List<DiscoveredSender> = emptyList(),
) {
    data class DiscoveredSender(val sender: String, val messageCount: Int, val latestTimestamp: Long, val likelyInstitution: String?)
    /**
     * Recognized transactions that the user can commit now. This is the
     * exclusive subset — recognized − (needsReview ∪ duplicate ∪
     * beforeTrackingStart) — and never double-counts.
     */
    val readyCount: Int
        get() = (recognizedTransactions - needsReviewTransactions - duplicateTransactions - beforeTrackingStartCount)
            .coerceAtLeast(0)

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
    )
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
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
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
        if (mode == SmsImportMode.REGISTERED_ACCOUNTS_ONLY && registeredSenders.isEmpty()) {
            return@withContext ScanPreview(mode = mode, configuredSenderCount = 0, hasRegisteredSenders = false)
        }
        if (mode == SmsImportMode.DISCOVER_NEW_SENDERS) {
            val discoveries = messages.asSequence()
                .filter { sms -> SenderNormalizer.normalize(sms.sender) !in registeredSenders }
                .filter { isLikelyFinancialSender(it.sender) || com.baraa.masroof.sms.BankSmsFilter.classifyMessage(it.sender, it.body).isMatch }
                .groupBy { it.sender?.trim().orEmpty() }
                .filterKeys { it.isNotBlank() }
                .map { (sender, rows) -> ScanPreview.DiscoveredSender(sender, rows.size, rows.maxOf { it.timestamp }, null) }
                .sortedByDescending { it.latestTimestamp }
            return@withContext ScanPreview(
                mode = mode, configuredSenderCount = registeredSenders.size, scannedMessages = messages.size,
                unregisteredSenderMessages = messages.count { SenderNormalizer.normalize(it.sender) !in registeredSenders },
                discoveredSenders = discoveries,
            )
        }

        val categories = categoryRepository.getAll()
        val ownedAccounts = RoomFinancialAccountRepository(database.financialAccountDao()).getOwnedActive()
        val merchantMemory = merchantMemoryRepository.getAll()
        val engine = RuleEngineFactory.build(categories, feeCategoryId = null)
        val context = RuleContext(ownedAccounts, merchantMemory, categories)
        var scanned = 0; var recognized = 0; var nonFinancial = 0; var unparsed = 0
        var unregistered = 0; var duplicates = 0; var needsReview = 0; var beforeTracking = 0
        val groups = linkedMapOf<String, MutableList<String>>(); val items = ArrayList<ScanPreview.PreviewItem>()

        for (sms in messages) {
            scanned++
            // This is intentionally before BankParserRegistry: unrelated bank messages
            // must not consume parsing work or become review/duplicate candidates.
            if (SenderNormalizer.normalize(sms.sender) !in registeredSenders) { unregistered++; continue }
            val parsed = BankParserRegistry.parse(sms.sender, sms.body, sms.timestamp.takeIf { it > 0L })
            if (parsed.amount == null || parsed.confidence < 30) {
                if (isLikelyFinancialSender(sms.sender)) unparsed++ else nonFinancial++
                continue
            }
            val entity = buildEntity(sms, parsed, engine, context)
            if (entity == null) { unparsed++; continue }
            recognized++
            val isDup = transactionRepository.existsByFingerprint(entity.uniqueFingerprint)
            val match = accountMatcher.match(entity, ownedAccounts, accountIdentifierRepository)
            val isReviewNow = match.account == null || match.needsReview
            val before = trackingStartDate != null && entity.transactionDate != null && entity.transactionDate.isBefore(trackingStartDate)
            if (isDup) duplicates++; if (isReviewNow) needsReview++; if (before) beforeTracking++
            val institutionKey = institutionResolver.resolve(
                sender = sms.sender, parsedInstitution = parserInstitution(parsed), parserIdentity = parsed.parserName,
                knownInstitutionNames = com.baraa.masroof.ledger.FinancialInstitutionResolver.WELL_KNOWN_INSTITUTIONS.toSet(),
            ).institutionDisplayName
            groups.getOrPut(institutionKey) { mutableListOf() }.add(entity.uniqueFingerprint)
            items += ScanPreview.PreviewItem(sms.id, sms.sender, entity.amount, entity.transactionType, match.account?.id,
                match.account?.displayName, isDup, isReviewNow, before, entity.transactionDate)
        }
        ScanPreview(
            mode = mode, configuredSenderCount = registeredSenders.size, scannedMessages = scanned,
            recognizedTransactions = recognized, nonFinancialMessages = nonFinancial, unparsedMessages = unparsed,
            unregisteredSenderMessages = unregistered, duplicateTransactions = duplicates, needsReviewTransactions = needsReview,
            beforeTrackingStartCount = beforeTracking,
            institutionGroups = groups.map { (institution, _) ->
                val its = items.filter { it.sender?.let(SenderNormalizer::normalize) in registeredSenders &&
                    institutionResolver.resolve(sender = it.sender).institutionDisplayName == institution }
                ScanPreview.InstitutionGroup(institution, its.size,
                    its.count { !it.isDuplicate && !it.isBeforeTrackingStart && !it.needsReview },
                    its.count { !it.isDuplicate && !it.isBeforeTrackingStart && it.needsReview }, 0)
            }, perTransaction = items,
        )
    }

    private suspend fun registeredSenderKeys(): Set<String> {
        val direct = accountIdentifierRepository.activeOwnedSenderAliases().toMutableSet()
        val ownedInstitutions = RoomFinancialAccountRepository(database.financialAccountDao()).getOwnedActive()
            .mapNotNull { it.institutionName?.trim()?.lowercase() }.toSet()
        database.senderInstitutionMappingDao().getActive()
            .filter { it.institutionName.trim().lowercase() in ownedInstitutions }
            .mapTo(direct) { SenderNormalizer.normalize(it.senderKey).orEmpty() }
        return direct.filter { it.isNotBlank() }.toSet()
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
        val engine = RuleEngineFactory.build(categories, feeCategoryId = null)
        val context = RuleContext(ownedAccounts, merchantMemory, categories)

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

        database.withTransaction {
            for (sms in importedSms) {
                if (scanPreview.mode == SmsImportMode.REGISTERED_ACCOUNTS_ONLY && SenderNormalizer.normalize(sms.sender) !in registeredSenders) continue
                val previewItem = previewBySms[sms.id]
                if (previewItem == null || previewItem.isDuplicate) {
                    if (previewItem?.isDuplicate == true) duplicates++
                    continue
                }
                val parsed: ParsedTransaction = BankParserRegistry.parse(sms.sender, sms.body, sms.timestamp.takeIf { it > 0L })
                if (parsed.amount == null || parsed.confidence < 30) continue
                val entity = buildEntity(sms, parsed, engine, context) ?: continue
                if (previewItem.isBeforeTrackingStart) {
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
                val match = accountMatcher.match(entity, ownedAccounts, accountIdentifierRepository)
                val hasLinkedAccount = match.account != null && !match.needsReview
                val linked = entity.withAccountMatch(match)
                val txId = transactionRepository.insert(linked)
                if (txId <= 0L) {
                    duplicates++
                    continue
                }
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
                        )
                    }
                }
            }
        }

        SmsImportResult(
            scannedMessages = scanPreview.scannedMessages,
            recognizedTransactions = scanPreview.recognizedTransactions,
            readyTransactions = imported,
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
    private fun TransactionEntity.withAccountMatch(match: AccountMatcher.Match): TransactionEntity {
        val accountId = match.account?.id
        val sourceId = when (financialTreatment) {
            FinancialTreatment.EXPENSE, FinancialTreatment.BANK_FEE, FinancialTreatment.CASH_WITHDRAWAL,
            FinancialTreatment.INTERNAL_TRANSFER, FinancialTreatment.CREDIT_CARD_PAYMENT, FinancialTreatment.INVESTMENT -> accountId
            else -> null
        }
        val destinationId = when (financialTreatment) {
            FinancialTreatment.INCOME, FinancialTreatment.REFUND -> accountId
            FinancialTreatment.INTERNAL_TRANSFER, FinancialTreatment.CREDIT_CARD_PAYMENT, FinancialTreatment.INVESTMENT -> match.destinationAccountCandidate?.id
            else -> null
        }
        return copy(
            sourceAccountId = sourceId,
            destinationAccountId = destinationId,
            accountLinkSource = match.source,
            accountLinkConfidence = match.confidence,
            accountLinkNeedsReview = match.needsReview,
            needsReview = needsReview || accountId == null || match.needsReview,
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
