package com.baraa.masroof.application.review

import com.baraa.masroof.application.review.ReviewDetailLoader.ReviewSummary

interface ReviewListDataSource {
    suspend fun loadPendingSummaries(): List<ReviewSummary>

    suspend fun loadIgnoredSummaries(): List<ReviewSummary>
}

class ReviewDetailListDataSource(
    private val detailLoader: ReviewDetailLoader,
) : ReviewListDataSource {
    override suspend fun loadPendingSummaries(): List<ReviewSummary> =
        detailLoader.loadSummaries()

    override suspend fun loadIgnoredSummaries(): List<ReviewSummary> =
        detailLoader.loadIgnoredSummaries()
}
