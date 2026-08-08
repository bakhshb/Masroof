package com.baraa.masroof.ledger

import androidx.room.withTransaction
import com.baraa.masroof.data.db.JournalDao
import com.baraa.masroof.data.db.JournalEntryEntity
import com.baraa.masroof.data.db.LedgerPostingEntity
import com.baraa.masroof.data.db.MasroofDatabase
import com.baraa.masroof.data.db.TransactionDao
import com.baraa.masroof.data.db.TransactionEntity
import java.time.LocalDate
import java.time.LocalTime

/**
 * Room-transaction boundary for journals. It is deliberately the only class
 * that persists generated journals or changes a journal to POSTED.
 */
class LedgerRepository(
    private val database: MasroofDatabase,
    private val journalDao: JournalDao = database.journalDao(),
    private val transactionDao: TransactionDao = database.transactionDao(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun create(draft: JournalDraft): Long = database.withTransaction {
        val requireBalanced = draft.postingStatus == JournalPostingStatus.POSTED
        JournalValidator.validate(draft, requireBalanced).also {
            require(it.valid) { it.reason.orEmpty() }
        }
        if (draft.generatedBy == JournalGeneratedBy.IMPORT_RULE) draft.sourceTransactionId?.let { transactionId ->
            val active = journalDao.getForTransaction(transactionId).firstOrNull {
                it.generatedBy == JournalGeneratedBy.IMPORT_RULE &&
                    it.postingStatus !in setOf(JournalPostingStatus.REVERSED, JournalPostingStatus.VOIDED)
            }
            require(active == null) { "active_journal_exists" }
        }
        val timestamp = now()
        val id = journalDao.insertJournal(
            JournalEntryEntity(
                sourceTransactionId = draft.sourceTransactionId,
                journalType = draft.journalType,
                postingStatus = draft.postingStatus,
                effectiveDate = draft.effectiveDate,
                effectiveTime = draft.effectiveTime,
                descriptionCode = draft.descriptionCode,
                createdAt = timestamp,
                updatedAt = timestamp,
                reversalOfJournalId = draft.reversalOfJournalId,
                notes = draft.notes,
                generatedBy = draft.generatedBy,
                generationVersion = draft.generationVersion,
            ),
        )
        journalDao.insertPostings(draft.postings.map {
            LedgerPostingEntity(
                journalEntryId = id,
                accountId = it.accountId,
                postingSide = it.postingSide,
                amount = it.amount,
                currency = it.currency,
                memoCode = it.memoCode,
                createdAt = timestamp,
            )
        })
        id
    }

    /** Atomically validates, posts, and links the source transaction. */
    suspend fun post(journalId: Long): LedgerValidation = database.withTransaction {
        val aggregate = journalDao.getWithPostings(journalId)
            ?: return@withTransaction LedgerValidation.invalid("journal_missing")
        if (aggregate.journal.postingStatus == JournalPostingStatus.POSTED) {
            return@withTransaction LedgerValidation.invalid("already_posted")
        }
        if (aggregate.journal.postingStatus in setOf(JournalPostingStatus.REVERSED, JournalPostingStatus.VOIDED)) {
            return@withTransaction LedgerValidation.invalid("journal_not_postable")
        }
        val draft = aggregate.toDraft()
        val validation = JournalValidator.validate(draft, requireBalanced = true)
        if (!validation.valid) return@withTransaction validation
        val accounts = aggregate.postings.map { database.financialAccountDao().getById(it.accountId) }
        if (accounts.any { it == null || !it.isActive }) return@withTransaction LedgerValidation.invalid("inactive_account")
        aggregate.journal.sourceTransactionId?.let { transactionId ->
            val transaction = transactionDao.getById(transactionId)
                ?: return@withTransaction LedgerValidation.invalid("transaction_missing")
            if (transaction.postingStatus == TransactionPostingStatus.POSTED) {
                return@withTransaction LedgerValidation.invalid("transaction_already_posted")
            }
            transactionDao.update(transaction.copy(
                linkedJournalEntryId = journalId,
                postingStatus = TransactionPostingStatus.POSTED,
                accountLinkNeedsReview = false,
                updatedAt = now(),
            ))
        }
        journalDao.updateJournal(aggregate.journal.copy(
            postingStatus = JournalPostingStatus.POSTED,
            updatedAt = now(),
        ))
        LedgerValidation.valid()
    }

    /** Replace a non-posted generated journal without affecting balances. */
    suspend fun regenerateDraft(transactionId: Long, replacement: JournalDraft): Long = database.withTransaction {
        require(replacement.sourceTransactionId == transactionId) { "source_transaction_mismatch" }
        val existing = journalDao.getForTransaction(transactionId)
        require(existing.none { it.postingStatus == JournalPostingStatus.POSTED }) { "posted_journal_requires_correction" }
        existing.forEach { journalDao.deleteJournal(it.id) }
        create(replacement)
    }

    /**
     * Deletes non-posted journals for a transaction after a failed link attempt.
     * Never touches POSTED / REVERSED / VOIDED entries.
     */
    suspend fun discardUnpostedDrafts(transactionId: Long) = database.withTransaction {
        journalDao.getForTransaction(transactionId)
            .filter {
                it.postingStatus == JournalPostingStatus.NEEDS_REVIEW ||
                    it.postingStatus == JournalPostingStatus.DRAFT
            }
            .forEach { journalDao.deleteJournal(it.id) }
    }

    /** Reverse a posted journal then create a reviewable corrected replacement. */
    suspend fun correctPosted(journalId: Long, replacement: JournalDraft): Long = database.withTransaction {
        val original = requireNotNull(journalDao.getWithPostings(journalId)) { "journal_missing" }
        require(original.journal.postingStatus == JournalPostingStatus.POSTED) { "journal_not_posted" }
        require(replacement.sourceTransactionId == original.journal.sourceTransactionId) { "source_transaction_mismatch" }
        reverse(journalId)
        create(replacement.copy(
            postingStatus = JournalPostingStatus.NEEDS_REVIEW,
            generatedBy = JournalGeneratedBy.USER,
            generationVersion = original.journal.generationVersion + 1,
        ))
    }

    /** A posted journal is immutable: create its equal-and-opposite reversal. */
    suspend fun reverse(journalId: Long): Long = database.withTransaction {
        val original = requireNotNull(journalDao.getWithPostings(journalId)) { "journal_missing" }
        require(original.journal.postingStatus == JournalPostingStatus.POSTED) { "journal_not_posted" }
        val reversedPostings = original.postings.map {
            PostingDraft(
                accountId = it.accountId,
                postingSide = if (it.postingSide == PostingSide.DEBIT) PostingSide.CREDIT else PostingSide.DEBIT,
                amount = it.amount,
                currency = it.currency,
                memoCode = "reversal",
            )
        }
        val id = create(
            JournalDraft(
                sourceTransactionId = null,
                journalType = JournalType.REVERSAL,
                postingStatus = JournalPostingStatus.POSTED,
                effectiveDate = original.journal.effectiveDate,
                effectiveTime = original.journal.effectiveTime,
                descriptionCode = "reversal",
                generatedBy = JournalGeneratedBy.USER,
                reversalOfJournalId = original.journal.id,
                postings = reversedPostings,
            ),
        )
        // Keep the original POSTED and post an equal/opposite journal. This
        // preserves the audit trail and makes their combined balance effect zero.
        original.journal.sourceTransactionId?.let { id ->
            transactionDao.getById(id)?.let { transactionDao.update(it.copy(postingStatus = TransactionPostingStatus.REVERSED, updatedAt = now())) }
        }
        id
    }

    suspend fun postedAsOf(date: LocalDate, time: LocalTime): List<com.baraa.masroof.data.db.JournalWithPostings> =
        journalDao.postedAsOf(date, time).mapNotNull { journalDao.getWithPostings(it.id) }

    private fun com.baraa.masroof.data.db.JournalWithPostings.toDraft() = JournalDraft(
        sourceTransactionId = journal.sourceTransactionId,
        journalType = journal.journalType,
        postingStatus = journal.postingStatus,
        effectiveDate = journal.effectiveDate,
        effectiveTime = journal.effectiveTime,
        descriptionCode = journal.descriptionCode,
        notes = journal.notes,
        generatedBy = journal.generatedBy,
        generationVersion = journal.generationVersion,
        reversalOfJournalId = journal.reversalOfJournalId,
        postings = postings.map { PostingDraft(it.accountId, it.postingSide, it.amount, it.currency, it.memoCode) },
    )
}
