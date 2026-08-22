package com.baraa.masroof.application.transaction

import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.ReviewItem
import com.baraa.masroof.domain.model.ReviewKind
import com.baraa.masroof.domain.model.ReviewResolutionKind
import com.baraa.masroof.domain.model.ReviewStatus
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.FinancialTransactionSaveResult
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.presentation.settings.SettingsViewModelTestSupport
import com.baraa.masroof.sms.time.InstantClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TransactionRestoreServiceTest {
    @Test
    fun restore_rollsBackReviewWhenReconcileFails() = runBlocking {
        val review = ReviewItem(
            id = "review-1",
            rawSmsId = "sms-1",
            kind = ReviewKind.NEEDS_REVIEW,
            reasons = listOf("user_ignored_transaction"),
            status = ReviewStatus.RESOLVED,
            resolutionKind = ReviewResolutionKind.USER_NON_FINANCIAL,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
            resolvedAt = Instant.parse("2026-08-01T00:00:00Z"),
            resolvedTransactionId = null,
        )
        val reviewRepo = TrackingReviewRepository(review)
        val service = TransactionRestoreService(
            reviewRepository = reviewRepo,
            financialTransactionRepository = SettingsViewModelTestSupport.emptyFinancialTransactionRepository(),
            reconciliation = TransactionReconciliationService(
                parsedEventRepository = SettingsViewModelTestSupport.emptyParsedEventRepository(),
                rawSmsRepository = SettingsViewModelTestSupport.emptyRawSmsRepository(),
                financialTransactionRepository = SettingsViewModelTestSupport.emptyFinancialTransactionRepository(),
                ownershipResolver = com.baraa.masroof.domain.ownership.OwnershipResolver(
                    accountRegistry = SettingsViewModelTestSupport.emptyAccountRegistry(),
                    cardRegistry = SettingsViewModelTestSupport.emptyCardRegistry(),
                ),
            ),
            reclassification = TransactionReclassificationService(
                financialTransactionRepository = SettingsViewModelTestSupport.emptyFinancialTransactionRepository(),
                effectiveParsedEventProvider = com.baraa.masroof.application.review.EffectiveParsedEventProvider(
                    parsedEventRepository = SettingsViewModelTestSupport.emptyParsedEventRepository(),
                    userCorrectionRepository = object : com.baraa.masroof.domain.repository.UserCorrectionRepository {
                        override suspend fun save(correction: com.baraa.masroof.domain.model.UserCorrection) = Unit
                        override suspend fun latestForRawSmsId(rawSmsId: String) = null
                        override suspend fun listForRawSmsId(rawSmsId: String): List<com.baraa.masroof.domain.model.UserCorrection> =
                            emptyList()
                    },
                ),
                ownershipResolver = com.baraa.masroof.domain.ownership.OwnershipResolver(
                    accountRegistry = SettingsViewModelTestSupport.emptyAccountRegistry(),
                    cardRegistry = SettingsViewModelTestSupport.emptyCardRegistry(),
                ),
            ),
            clock = InstantClock { Instant.parse("2026-08-02T00:00:00Z") },
        )

        val result = service.restore("sms-1")

        assertTrue(result is RestoreResult.Rejected)
        assertEquals(ReviewResolutionKind.USER_NON_FINANCIAL, reviewRepo.lastResolutionKind)
    }

    private class TrackingReviewRepository(
        private var review: ReviewItem,
    ) : ReviewRepository {
        var lastResolutionKind: ReviewResolutionKind? = review.resolutionKind

        override suspend fun getById(id: String): ReviewItem? = review.takeIf { it.id == id }

        override suspend fun findByRawSmsId(rawSmsId: String): ReviewItem? =
            review.takeIf { it.rawSmsId == rawSmsId }

        override suspend fun listRequired(): List<ReviewItem> = emptyList()

        override suspend fun listAll(): List<ReviewItem> = listOf(review)

        override suspend fun upsertRequired(
            rawSmsId: String,
            kind: ReviewKind,
            reasons: List<String>,
            now: Instant,
        ): ReviewItem = error("unused")

        override suspend fun markResolved(
            id: String,
            resolutionKind: ReviewResolutionKind,
            resolvedAt: Instant,
            resolvedTransactionId: String?,
        ): ReviewItem? {
            lastResolutionKind = resolutionKind
            review = review.copy(
                resolutionKind = resolutionKind,
                resolvedAt = resolvedAt,
                resolvedTransactionId = resolvedTransactionId,
            )
            return review
        }
    }
}
