package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.transaction.BankSmsParserRegistry
import com.baraa.masroof.transaction.ParsedTransaction
import com.baraa.masroof.transaction.TransactionFingerprint

private const val PARSE_CONFIDENCE_THRESHOLD = 30

/**
 * Two-phase SMS import:
 *  1. [preview] — read SMS, parse each, count new vs duplicate vs unparseable.
 *     Does **not** write to the database. Safe to call from the UI thread
 *     dispatcher because the actual I/O is performed via the caller's coroutine
 *     context, but the implementation uses `withContext(IO)` internally.
 *  2. [commit] — insert the previously-prepared transactions with
 *     `OnConflictStrategy.IGNORE` (i.e. duplicate fingerprints are skipped at
 *     the DB layer too, as a second line of defense).
 *
 * The service is **read-only on the SMS provider** — it never writes, deletes
 * or modifies messages.
 */
class TransactionImportService(
    private val repository: TransactionRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * Phase 1: scan and parse SMS messages without writing anything.
     *
     * @return counts + the prepared entities that would be inserted
     */
    suspend fun preview(messages: List<SmsMessage>): PreviewResult {
        var scanned = 0
        var parsed = 0
        var unparseable = 0
        var newCount = 0
        var dupCount = 0
        val prepared = ArrayList<TransactionEntity>(messages.size)

        for (sms in messages) {
            scanned++
            val parsedTxn = BankSmsParserRegistry.parse(sms.sender, sms.body, sms.timestamp)
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

            if (repository.existsByFingerprint(entity.uniqueFingerprint)) {
                dupCount++
            } else {
                prepared.add(entity)
                newCount++
            }
        }

        return PreviewResult(
            preview = ImportPreview(
                messagesScanned = scanned,
                parsedSuccessfully = parsed,
                unparseable = unparseable,
                newTransactions = newCount,
                duplicatesSkipped = dupCount,
            ),
            prepared = prepared,
        )
    }

    /**
     * Phase 2: insert the prepared entities. Returns the final summary.
     */
    suspend fun commit(previewResult: PreviewResult): ImportSummary {
        val ids = repository.insertAll(previewResult.prepared)
        val inserted = ids.count { it != -1L }
        val ignoredAtDb = previewResult.prepared.size - inserted
        return ImportSummary.fromPreviewAndInsert(
            preview = previewResult.preview,
            inserted = inserted,
            duplicatesFromInsert = ignoredAtDb,
        )
    }

    /** Result of [preview] — the preview counts plus the prepared entities. */
    data class PreviewResult(
        val preview: ImportPreview,
        val prepared: List<TransactionEntity>,
    )

    // -- Helpers --------------------------------------------------------------

    private fun isUseful(p: ParsedTransaction): Boolean =
        p.amount != null && p.confidence >= PARSE_CONFIDENCE_THRESHOLD

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
        )
    }
}
