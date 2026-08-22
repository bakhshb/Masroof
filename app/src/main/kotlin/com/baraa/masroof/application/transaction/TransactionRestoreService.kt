package com.baraa.masroof.application.transaction

import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.ReviewResolutionKind
import com.baraa.masroof.domain.model.ReviewStatus
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.sms.time.InstantClock

sealed interface RestoreResult {
    data class Success(val transaction: FinancialTransaction) : RestoreResult

    data class Rejected(val reason: String) : RestoreResult
}

/**
 * Restores a user-ignored SMS as a financial transaction with an optional type override.
 */
class TransactionRestoreService(
    private val reviewRepository: ReviewRepository,
    private val financialTransactionRepository: FinancialTransactionRepository,
    private val reconciliation: TransactionReconciliationService,
    private val reclassification: TransactionReclassificationService,
    private val clock: InstantClock,
) {
    suspend fun listIgnoredRawSmsIds(): List<String> =
        reviewRepository.listIgnored().map { it.rawSmsId }

    suspend fun restore(
        rawSmsId: String,
        newType: FinancialTransactionType? = null,
    ): RestoreResult {
        val review = reviewRepository.findByRawSmsId(rawSmsId)
            ?: return RestoreResult.Rejected("review_not_found")
        if (review.status != ReviewStatus.RESOLVED ||
            review.resolutionKind != ReviewResolutionKind.USER_NON_FINANCIAL
        ) {
            return RestoreResult.Rejected("not_ignored")
        }

        reviewRepository.markResolved(
            id = review.id,
            resolutionKind = ReviewResolutionKind.USER_FINANCIAL_TYPE,
            resolvedAt = clock.now(),
            resolvedTransactionId = null,
        ) ?: return RestoreResult.Rejected("review_clear_failed")

        reconciliation.reconcileStoredEvents()
        val tx = financialTransactionRepository.findByRawSmsId(rawSmsId)
        if (tx == null) {
            rollbackToIgnored(review.id, rawSmsId)
            return RestoreResult.Rejected("reconcile_failed")
        }

        if (newType == null || newType == tx.type) {
            return RestoreResult.Success(tx)
        }

        return when (val result = reclassification.reclassify(tx.id, newType)) {
            is ReclassificationResult.Success -> RestoreResult.Success(result.transaction)
            is ReclassificationResult.Rejected -> {
                rollbackToIgnored(review.id, rawSmsId)
                RestoreResult.Rejected(result.reason)
            }
        }
    }

    suspend fun restoreWithReclassify(
        rawSmsId: String,
        newType: FinancialTransactionType,
    ): RestoreResult = restore(rawSmsId, newType)

    private suspend fun rollbackToIgnored(reviewId: String, rawSmsId: String) {
        financialTransactionRepository.deleteIfExclusiveRawSmsLink(rawSmsId)
        reviewRepository.markResolved(
            id = reviewId,
            resolutionKind = ReviewResolutionKind.USER_NON_FINANCIAL,
            resolvedAt = clock.now(),
            resolvedTransactionId = null,
        )
    }
}
