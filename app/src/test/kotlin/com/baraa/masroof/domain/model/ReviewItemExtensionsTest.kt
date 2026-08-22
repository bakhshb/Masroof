package com.baraa.masroof.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReviewItemExtensionsTest {
    @Test
    fun isUserIgnored_trueForResolvedNonFinancial() {
        val review = review(
            status = ReviewStatus.RESOLVED,
            resolutionKind = ReviewResolutionKind.USER_NON_FINANCIAL,
        )

        assertTrue(review.isUserIgnored())
    }

    @Test
    fun isUserIgnored_falseForOtherResolutionKinds() {
        val review = review(
            status = ReviewStatus.RESOLVED,
            resolutionKind = ReviewResolutionKind.USER_CORRECTION,
        )

        assertFalse(review.isUserIgnored())
    }

    private fun review(
        status: ReviewStatus,
        resolutionKind: ReviewResolutionKind?,
    ): ReviewItem =
        ReviewItem(
            id = "review-1",
            rawSmsId = "sms-1",
            kind = ReviewKind.NEEDS_REVIEW,
            status = status,
            reasons = listOf("user_ignored_transaction"),
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
            resolvedAt = Instant.parse("2026-08-02T00:00:00Z"),
            resolutionKind = resolutionKind,
            resolvedTransactionId = null,
        )
}
