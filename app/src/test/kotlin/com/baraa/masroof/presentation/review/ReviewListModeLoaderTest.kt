package com.baraa.masroof.presentation.review

import com.baraa.masroof.application.review.ReviewDetailLoader
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.ReviewItem
import com.baraa.masroof.domain.model.ReviewKind
import com.baraa.masroof.domain.model.ReviewResolutionKind
import com.baraa.masroof.domain.model.ReviewStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ReviewListModeLoaderTest {
    @Test
    fun summariesForListMode_ignored_usesIgnoredLoader() = runTest {
        val pending = listOf(summary("pending"))
        val ignored = listOf(summary("ignored"))

        val loaded = summariesForListMode(
            mode = ReviewListMode.IGNORED,
            loadPending = { pending },
            loadIgnored = { ignored },
        )

        assertEquals(ignored, loaded)
    }

    @Test
    fun summariesForListMode_pending_usesPendingLoader() = runTest {
        val pending = listOf(summary("pending"))
        val ignored = listOf(summary("ignored"))

        val loaded = summariesForListMode(
            mode = ReviewListMode.PENDING,
            loadPending = { pending },
            loadIgnored = { ignored },
        )

        assertEquals(pending, loaded)
    }

    private fun summary(id: String): ReviewDetailLoader.ReviewSummary =
        ReviewDetailLoader.ReviewSummary(
            review = ReviewItem(
                id = id,
                rawSmsId = "sms-$id",
                kind = ReviewKind.NEEDS_REVIEW,
                status = ReviewStatus.RESOLVED,
                reasons = listOf("user_ignored_transaction"),
                createdAt = Instant.parse("2026-08-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
                resolvedAt = Instant.parse("2026-08-02T00:00:00Z"),
                resolutionKind = ReviewResolutionKind.USER_NON_FINANCIAL,
                resolvedTransactionId = null,
            ),
            title = "Sample",
            amount = Money.of("10.00", Currency.SAR),
            messageFamily = null,
            receivedAt = Instant.parse("2026-08-01T00:00:00Z"),
            body = "body",
        )
}
