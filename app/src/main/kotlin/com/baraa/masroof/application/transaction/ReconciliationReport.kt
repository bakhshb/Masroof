package com.baraa.masroof.application.transaction

import com.baraa.masroof.domain.model.ReviewKind

/**
 * Detailed P8 reconciliation output for P9 review-queue updates.
 */
data class ReconciliationReport(
    val summary: ReconciliationSummary,
    val reviewCandidates: List<ReconciliationReviewCandidate>,
    val settledRawSmsIds: Set<String>,
)

data class ReconciliationReviewCandidate(
    val rawSmsId: String,
    val kind: ReviewKind,
    val reasons: List<String>,
)
