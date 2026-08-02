package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.MerchantMemory
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleEngine
import com.baraa.masroof.rules.RuleEngineFactory
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.transaction.BankParserRegistry
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.MerchantNormalizer
import com.baraa.masroof.transaction.ParsedTransaction
import com.baraa.masroof.transaction.TransactionFingerprint
import java.math.BigDecimal

/**
 * Two-phase SMS import with **two-level duplicate detection** and full
 * financial-rule classification.
 *
 * Phase 1 — [preview]:
 *  1. Parse each SMS via [BankParserRegistry].
 *  2. Check for exact-fingerprint duplicates and similarity-key
 *     near-duplicates.
 *  3. For every non-duplicate, run the rule engine to determine
 *     financial treatment + category + confidence.
 *  4. Surface the per-item preview to the user for review of possible
 *     duplicates.
 *
 * Phase 2 — [commit]:
 *  - Insert only items the user has approved.
 *  - DB-layer [androidx.room.OnConflictStrategy.IGNORE] is the final
 *    defense against race-condition duplicates.
 *
 * Read-only on the SMS provider.
 */
class TransactionImportService(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val merchantMemoryRepository: MerchantMemoryRepository,
    private val financialAccountRepository: FinancialAccountRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    companion object {
        const val DUPLICATE_WINDOW_MILLIS: Long = 10L * 60L * 1000L
    }

    suspend fun preview(messages: List<SmsMessage>): PreviewResult {
        val categories = categoryRepository.getAll()
        val feeCategoryId = categories.firstOrNull { it.nameAr == "رسوم بنكية" }?.id
        val ownedAccounts = financialAccountRepository.getOwnedActive()
        val merchantMemory = merchantMemoryRepository.getAll()
        val engine = RuleEngineFactory.build(categories, feeCategoryId)
        val context = RuleContext(
            ownedAccounts = ownedAccounts,
            merchantMemories = merchantMemory,
            categories = categories,
        )

        var scanned = 0
        var parsed = 0
        var unparseable = 0
        val items = ArrayList<ImportPreviewItem>(messages.size)

        for ((index, sms) in messages.withIndex()) {
            scanned++
            val parsedTxn = BankParserRegistry.parse(sms.sender, sms.body, sms.timestamp)
            if (!isUseful(parsedTxn)) {
                unparseable++
                continue
            }
            parsed++

            val built = buildEntityAndClassify(sms, parsedTxn, engine, context)
            if (built == null) {
                unparseable++
                continue
            }
            val entity = built.first
            val ruleVerdict = built.second

            // -- Level 1: exact fingerprint collision --
            if (transactionRepository.existsByFingerprint(entity.uniqueFingerprint)) {
                items.add(
                    ImportPreviewItem(
                        smsIndex = index,
                        sender = sms.sender,
                        bodyExcerpt = excerpt(sms.body),
                        amount = parsedTxn.amount,
                        currency = parsedTxn.currency,
                        transactionType = parsedTxn.transactionType,
                        merchant = parsedTxn.merchant,
                        smsTimestamp = sms.timestamp,
                        preparedEntity = entity,
                        status = ImportItemStatus.EXACT_DUPLICATE,
                    )
                )
                continue
            }

            // -- Level 2: similarity-key collision within window --
            val candidate = transactionRepository.findBySimilarityKey(entity.transactionSimilarityKey ?: "")
                .firstOrNull { existing ->
                    Math.abs(existing.smsTimestamp - sms.timestamp) <= DUPLICATE_WINDOW_MILLIS
                }
            if (candidate != null) {
                items.add(
                    ImportPreviewItem(
                        smsIndex = index,
                        sender = sms.sender,
                        bodyExcerpt = excerpt(sms.body),
                        amount = parsedTxn.amount,
                        currency = parsedTxn.currency,
                        transactionType = parsedTxn.transactionType,
                        merchant = parsedTxn.merchant,
                        smsTimestamp = sms.timestamp,
                        preparedEntity = entity,
                        status = ImportItemStatus.POSSIBLE_DUPLICATE,
                        collidingWith = candidate,
                    )
                )
                continue
            }

            // -- Genuinely new --
            items.add(
                ImportPreviewItem(
                    smsIndex = index,
                    sender = sms.sender,
                    bodyExcerpt = excerpt(sms.body),
                    amount = parsedTxn.amount,
                    currency = parsedTxn.currency,
                    transactionType = parsedTxn.transactionType,
                    merchant = parsedTxn.merchant,
                    smsTimestamp = sms.timestamp,
                    preparedEntity = entity,
                    status = ImportItemStatus.NEW,
                )
            )
        }

        val preview = ImportPreview(
            messagesScanned = scanned,
            parsedSuccessfully = parsed,
            unparseable = unparseable,
            newTransactions = items.count { it.status == ImportItemStatus.NEW },
            exactDuplicates = items.count { it.status == ImportItemStatus.EXACT_DUPLICATE },
            possibleDuplicates = items.count { it.status == ImportItemStatus.POSSIBLE_DUPLICATE },
        )
        return PreviewResult(preview, items)
    }

    suspend fun commit(preview: PreviewResult): ImportSummary {
        val toInsert = preview.items
            .filter { it.decision == DuplicateDecision.INSERT_ANYWAY }
            .mapNotNull { it.preparedEntity }
        val insertedIds = transactionRepository.insertAll(toInsert)
        val inserted = insertedIds.count { it != -1L }
        val ignoredAtDb = toInsert.size - inserted

        val exactDupSkipped = preview.items.count { it.status == ImportItemStatus.EXACT_DUPLICATE }
        val possibleDupInserted = preview.items.count {
            it.status == ImportItemStatus.POSSIBLE_DUPLICATE &&
                it.decision == DuplicateDecision.INSERT_ANYWAY
        }
        val possibleDupSkipped = preview.items.count {
            it.status == ImportItemStatus.POSSIBLE_DUPLICATE &&
                it.decision == DuplicateDecision.SKIP
        }

        return ImportSummary.fromCounts(
            messagesScanned = preview.preview.messagesScanned,
            parsedSuccessfully = preview.preview.parsedSuccessfully,
            unparseable = preview.preview.unparseable,
            inserted = inserted,
            exactDuplicatesSkipped = exactDupSkipped,
            possibleDuplicatesSkipped = possibleDupSkipped + ignoredAtDb,
            possibleDuplicatesInserted = possibleDupInserted,
        )
    }

    data class PreviewResult(
        val preview: ImportPreview,
        val items: List<ImportPreviewItem>,
    )

    // -- Helpers --------------------------------------------------------------

    private fun isUseful(p: ParsedTransaction): Boolean =
        p.amount != null && p.confidence >= 30

    private fun buildEntityAndClassify(
        sms: SmsMessage,
        p: ParsedTransaction,
        engine: RuleEngine,
        context: RuleContext,
    ): Pair<TransactionEntity, RuleEngine.Verdict>? {
        val amount = p.amount ?: return null
        val timestamp = now()

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

        // Run the rule engine.
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
                DateSource.FROM_BODY
            p.transactionDate != null ->
                DateSource.FROM_SMS_METADATA
            else ->
                DateSource.UNKNOWN
        }

        val entity = TransactionEntity(
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
        return entity to verdict
    }

    private fun excerpt(body: String?): String? {
        if (body == null) return null
        val max = 80
        return if (body.length <= max) body else body.substring(0, max) + "…"
    }
}
