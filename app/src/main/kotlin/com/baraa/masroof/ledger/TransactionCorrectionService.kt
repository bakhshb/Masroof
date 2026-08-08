package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.TransactionRepository

/** Posts an equal-and-opposite journal for a posted entry (audit trail). */
fun interface JournalReverser {
    suspend fun reverse(journalId: Long): Long
}

sealed class CorrectionResult {
    data class Success(val transaction: TransactionEntity) : CorrectionResult()
    data class ValidationError(val code: String, val messageAr: String) : CorrectionResult()
    data class Failure(val messageAr: String, val cause: Throwable? = null) : CorrectionResult()
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
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) {
    /**
     * Reverse the linked posted journal (if any) and reset the transaction
     * for review. Does not invent a replacement journal — the user confirms
     * via the review dialog.
     */
    suspend fun reopenForCorrection(transaction: TransactionEntity): CorrectionResult {
        if (transaction.id <= 0L) {
            return CorrectionResult.ValidationError(
                code = "missing_transaction_id",
                messageAr = "معرّف العملية غير صالح",
            )
        }
        val fresh = transactions.getById(transaction.id)
            ?: return CorrectionResult.ValidationError(
                code = "transaction_deleted",
                messageAr = "هذه العملية لم تعد موجودة",
            )
        if (
            fresh.postingStatus != TransactionPostingStatus.POSTED &&
            fresh.postingStatus != TransactionPostingStatus.REVERSED
        ) {
            return CorrectionResult.ValidationError(
                code = "correction_requires_posted_or_reversed",
                messageAr = "التصحيح متاح فقط للعمليات المرحّلة — استخدم شاشة المراجعة للعمليات غير المرحّلة",
            )
        }

        return try {
            val journalId = fresh.linkedJournalEntryId
            if (journalId != null && fresh.postingStatus == TransactionPostingStatus.POSTED) {
                journalReverser.reverse(journalId)
            }

            val reopened = fresh.copy(
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
            CorrectionResult.Success(reopened)
        } catch (iae: IllegalArgumentException) {
            onError("reopenForCorrection require for tx=${transaction.id}", iae)
            CorrectionResult.ValidationError(
                code = iae.message.orEmpty().ifBlank { "correction_failed" },
                messageAr = "تعذّر فتح التصحيح: ${iae.message ?: "تحقق من حالة القيد"}",
            )
        } catch (t: Throwable) {
            onError("reopenForCorrection failed for tx=${transaction.id}", t)
            CorrectionResult.Failure(
                messageAr = "تعذّر فتح التصحيح: ${t.message ?: t.javaClass.simpleName}",
                cause = t,
            )
        }
    }
}
