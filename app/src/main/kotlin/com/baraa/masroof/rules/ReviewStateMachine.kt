package com.baraa.masroof.rules

import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.FinancialTreatment

/**
 * Single source of truth for the `userConfirmed` / `needsReview` /
 * `categorySource` / `categoryId` / `financialTreatment` field
 * transitions.
 *
 * The rule engine writes `needsReview = true` when it cannot confidently
 * classify a transaction. The user (via the review UI) writes
 * `userConfirmed = true` when they accept the result. The spending
 * calculator includes `userConfirmed` transactions even if `needsReview`
 * is still true.
 *
 * Use these helpers to avoid inconsistent state when saving edits.
 */
object ReviewStateMachine {

    /**
     * Mark a transaction as reviewed and accepted by the user. The
     * transaction is included in spending totals regardless of the
     * engine's `needsReview` setting.
     */
    fun confirm(
        entity: TransactionEntity,
        financialTreatment: FinancialTreatment = entity.financialTreatment,
        categoryId: Long? = entity.categoryId,
        categorySource: CategorySource = CategorySource.USER,
    ): TransactionEntity = entity.copy(
        userConfirmed = true,
        needsReview = false,
        financialTreatment = financialTreatment,
        categoryId = categoryId,
        categorySource = categorySource,
    )

    /**
     * Force a financial treatment. Used by the review actions
     * (e.g. "اعتبارها تحويلًا داخليًا" → INTERNAL_TRANSFER).
     */
    fun forceTreatment(
        entity: TransactionEntity,
        treatment: FinancialTreatment,
    ): TransactionEntity = entity.copy(
        userConfirmed = true,
        needsReview = false,
        financialTreatment = treatment,
        categorySource = CategorySource.USER,
    )

    /**
     * Mark a transaction as needing user review without changing the
     * classification. The rule engine uses this when no rule matched.
     */
    fun markForReview(
        entity: TransactionEntity,
        reason: String,
    ): TransactionEntity = entity.copy(
        needsReview = true,
        userConfirmed = false,
        exclusionReason = if (entity.exclusionReason == null) reason else entity.exclusionReason,
    )
}
