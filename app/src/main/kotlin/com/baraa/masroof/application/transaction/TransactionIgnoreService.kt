package com.baraa.masroof.application.transaction

import com.baraa.masroof.domain.model.ReviewKind
import com.baraa.masroof.domain.model.ReviewResolutionKind
import com.baraa.masroof.domain.model.ReviewStatus
import com.baraa.masroof.domain.model.isUserIgnored
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.sms.time.InstantClock

sealed interface IgnoreResult {
    data object Success : IgnoreResult

    data class Rejected(val reason: String) : IgnoreResult
}

/**
 * Removes a single-SMS [FinancialTransaction] and records a durable USER_NON_FINANCIAL
 * resolution so reconciliation does not recreate it.
 */
class TransactionIgnoreService(
    private val financialTransactionRepository: FinancialTransactionRepository,
    private val reviewRepository: ReviewRepository,
    private val clock: InstantClock,
) {
    suspend fun ignore(transactionId: String): IgnoreResult {
        financialTransactionRepository.getById(transactionId)
            ?: return IgnoreResult.Rejected("transaction_not_found")

        val rawSmsIds = financialTransactionRepository.listRawSmsIds(transactionId)
        if (rawSmsIds.size != 1) {
            return IgnoreResult.Rejected("paired_transaction_not_supported")
        }
        val rawSmsId = rawSmsIds.single()

        if (!financialTransactionRepository.deleteIfExclusiveRawSmsLink(rawSmsId)) {
            return IgnoreResult.Rejected("delete_failed")
        }

        val now = clock.now()
        val existing = reviewRepository.findByRawSmsId(rawSmsId)
        if (existing?.isUserIgnored() == true) {
            return IgnoreResult.Success
        }

        if (existing == null || existing.status == ReviewStatus.REQUIRED) {
            if (existing == null) {
                reviewRepository.upsertRequired(
                    rawSmsId = rawSmsId,
                    kind = ReviewKind.NEEDS_REVIEW,
                    reasons = listOf("user_ignored_transaction"),
                    now = now,
                )
            }
            reviewRepository.markResolved(
                id = existing?.id ?: com.baraa.masroof.domain.ids.ReviewIdFactory.fromRawSmsId(rawSmsId),
                resolutionKind = ReviewResolutionKind.USER_NON_FINANCIAL,
                resolvedAt = now,
                resolvedTransactionId = null,
            ) ?: return IgnoreResult.Rejected("review_resolution_failed")
            return IgnoreResult.Success
        }

        reviewRepository.markResolved(
            id = existing.id,
            resolutionKind = ReviewResolutionKind.USER_NON_FINANCIAL,
            resolvedAt = now,
            resolvedTransactionId = null,
        ) ?: return IgnoreResult.Rejected("review_resolution_failed")
        return IgnoreResult.Success
    }
}
