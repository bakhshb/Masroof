package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.transaction.BankParserRegistry
import com.baraa.masroof.transaction.ParsedTransaction
import com.baraa.masroof.transaction.TransactionFingerprint
import java.math.BigDecimal

/**
 * Two-phase SMS import with **two-level duplicate detection**.
 *
 * Phase 1 — [preview]:
 *  - reads each SMS
 *  - parses via [BankParserRegistry]
 *  - computes both an **exact fingerprint** and a **similarity key**
 *  - queries the DB for collisions and labels each item as
 *    [ImportItemStatus.NEW] / [ImportItemStatus.EXACT_DUPLICATE] /
 *    [ImportItemStatus.POSSIBLE_DUPLICATE]
 *  - does **not** write to the DB
 *
 * Phase 2 — [commit]:
 *  - inserts only items whose user decision is INSERT_ANYWAY
 *  - exact duplicates are auto-skipped (they never reach the DB layer)
 *  - the DB layer's [androidx.room.OnConflictStrategy.IGNORE] on the
 *    unique-fingerprint index is a final defense against race-condition
 *    duplicates.
 *
 * Read-only on the SMS provider.
 */
class TransactionImportService(
    private val repository: TransactionRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * Conservative time window for the similarity-key check. Two SMS for
     * the same purchase arriving more than 10 minutes apart are not
     * considered duplicates by default — the user can re-import them.
     */
    companion object {
        const val DUPLICATE_WINDOW_MILLIS: Long = 10L * 60L * 1000L
    }

    suspend fun preview(messages: List<SmsMessage>): PreviewResult {
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

            val entity = buildEntity(sms, parsedTxn)
            if (entity == null) {
                unparseable++
                continue
            }

            // -- Level 1: exact fingerprint collision --
            if (repository.existsByFingerprint(entity.uniqueFingerprint)) {
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
            val candidate = repository.findBySimilarityKey(entity.transactionSimilarityKey ?: "")
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

    /**
     * Insert the items the user has approved. EXACT_DUPLICATE items are
     * always skipped; POSSIBLE_DUPLICATE items are inserted only if the
     * user changed the decision to INSERT_ANYWAY; NEW items are always
     * inserted.
     */
    suspend fun commit(preview: PreviewResult): ImportSummary {
        val toInsert = preview.items
            .filter { it.decision == DuplicateDecision.INSERT_ANYWAY }
            .mapNotNull { it.preparedEntity }
        val insertedIds = repository.insertAll(toInsert)
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

    /** Result of [preview] — counts + the per-item list. */
    data class PreviewResult(
        val preview: ImportPreview,
        val items: List<ImportPreviewItem>,
    )

    // -- Helpers --------------------------------------------------------------

    private fun isUseful(p: ParsedTransaction): Boolean =
        p.amount != null && p.confidence >= 30

    private fun buildEntity(sms: SmsMessage, p: ParsedTransaction): TransactionEntity? {
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
        val dateSource = when {
            p.transactionDate != null && p.parsingNotes.any { it.startsWith("date from message body") } ->
                DateSource.FROM_BODY
            p.transactionDate != null ->
                DateSource.FROM_SMS_METADATA
            else ->
                DateSource.UNKNOWN
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
        )
    }

    /**
     * Returns a short excerpt of the SMS body for UI display. Truncates to
     * a small number of characters and never includes the full body — the
     * original message stays in [SmsMessage.body] for the next parse, but
     * the preview does not echo it into the UI by default.
     */
    private fun excerpt(body: String?): String? {
        if (body == null) return null
        val max = 80
        return if (body.length <= max) body else body.substring(0, max) + "…"
    }
}
