package com.baraa.masroof.domain.rules

import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.TransferOwnershipType

/**
 * Outcome of applying domain financial rules to a [ClassificationContext].
 */
sealed interface ClassificationResult {
    val impact: FinancialImpact
    val reasons: List<String>
    val transferOwnership: TransferOwnershipType?

    data class Classified(
        val transactionType: FinancialTransactionType,
        override val transferOwnership: TransferOwnershipType?,
        override val impact: FinancialImpact,
        override val reasons: List<String>,
    ) : ClassificationResult

    /**
     * Ownership or meaning is insufficient for a safe automatic classification.
     */
    data class NeedsReview(
        val tentativeType: FinancialTransactionType?,
        override val transferOwnership: TransferOwnershipType?,
        override val impact: FinancialImpact,
        override val reasons: List<String>,
    ) : ClassificationResult
}
