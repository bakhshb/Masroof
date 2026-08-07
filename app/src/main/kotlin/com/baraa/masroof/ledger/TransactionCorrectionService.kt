package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.TransactionRepository

/** Posts an equal-and-opposite journal for a posted entry (audit trail). */
fun interface JournalReverser {
    suspend fun reverse(journalId: Long): Long
}

/**
 * Reopens a posted (or already-reversed) transaction for user correction.
 * Posted journals stay immutable: [JournalReverser.reverse] posts an
 * equal-and-opposite journal, then the transaction returns to NEEDS_REVIEW
 * so [TransactionLinkingService.applyUserLink] can create a new journal.
 */
class TransactionCorrectionService(
    private val transactions: TransactionRepository,
    private val journalReverser: JournalReverser,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /**
     * Reverse the linked posted journal (if any) and reset the transaction
     * for review. Does not invent a replacement journal — the user confirms
     * via the review dialog.
     */
    suspend fun reopenForCorrection(transaction: TransactionEntity): TransactionEntity {
        require(
            transaction.postingStatus == TransactionPostingStatus.POSTED ||
                transaction.postingStatus == TransactionPostingStatus.REVERSED,
        ) { "correction_requires_posted_or_reversed" }

        val journalId = transaction.linkedJournalEntryId
        if (journalId != null && transaction.postingStatus == TransactionPostingStatus.POSTED) {
            journalReverser.reverse(journalId)
        }

        val reopened = transaction.copy(
            linkedJournalEntryId = null,
            postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
            needsReview = true,
            userConfirmed = false,
            accountLinkNeedsReview = true,
            accountLinkSource = AccountLinkSource.UNLINKED,
            accountLinkConfidence = 0,
            exclusionReason = "تصحيح بعد الترحيل — أعد التصنيف والربط",
            updatedAt = now(),
        )
        transactions.update(reopened)
        return reopened
    }
}
