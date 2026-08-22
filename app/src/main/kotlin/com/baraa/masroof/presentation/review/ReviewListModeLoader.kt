package com.baraa.masroof.presentation.review

import com.baraa.masroof.application.review.ReviewDetailLoader

internal suspend fun summariesForListMode(
    mode: ReviewListMode,
    loadPending: suspend () -> List<ReviewDetailLoader.ReviewSummary>,
    loadIgnored: suspend () -> List<ReviewDetailLoader.ReviewSummary>,
): List<ReviewDetailLoader.ReviewSummary> =
    when (mode) {
        ReviewListMode.PENDING -> loadPending()
        ReviewListMode.IGNORED -> loadIgnored()
    }
