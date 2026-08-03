package com.baraa.masroof.rules

import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Centralized review-state transitions: a confirmed transaction clears
 * `needsReview` and is included in spending totals. A low-confidence
 * auto-classification sets `needsReview = true` and is excluded.
 */
class ReviewStateAndSpendingTest {

    private fun makeTxn(
        id: Long = 1L,
        treatment: FinancialTreatment = FinancialTreatment.EXPENSE,
        amount: BigDecimal = BigDecimal("100.00"),
        needsReview: Boolean = true,
        userConfirmed: Boolean = false,
    ): TransactionEntity = TransactionEntity(
        id = id,
        uniqueFingerprint = "fp-$id",
        smsTimestamp = 1_700_000_000_000L,
        originalSender = "Test",
        transactionType = TransactionType.PURCHASE,
        amount = amount,
        currency = Currency.SAR,
        merchantOrBeneficiary = "Test",
        accountOrCardLastFourDigits = null,
        transactionDate = LocalDate.of(2024, 1, 15),
        transactionTime = LocalTime.of(14, 30),
        status = TransactionStatus.COMPLETED,
        confidence = 80,
        parsingNotes = emptyList(),
        dateSource = DateSource.FROM_BODY,
        createdAt = 0L,
        updatedAt = 0L,
        transactionSimilarityKey = "sk-$id",
        financialTreatment = treatment,
        categoryId = null,
        categorySource = CategorySource.RULE,
        categoryConfidence = 80,
        needsReview = needsReview,
        userConfirmed = userConfirmed,
        exclusionReason = null,
    )

    @Test
    fun confirmClearsNeedsReview() {
        val t = makeTxn(needsReview = true, userConfirmed = false)
        val confirmed = ReviewStateMachine.confirm(t)
        assertTrue(confirmed.userConfirmed)
        assertFalse(confirmed.needsReview)
        assertEquals(CategorySource.USER, confirmed.categorySource)
    }

    @Test
    fun markForReviewSetsFlag() {
        val t = makeTxn(needsReview = false, userConfirmed = true)
        val updated = ReviewStateMachine.markForReview(t, "low confidence")
        assertTrue(updated.needsReview)
        assertFalse(updated.userConfirmed)
        assertEquals("low confidence", updated.exclusionReason)
    }

    @Test
    fun forceTreatmentSetsUserConfirmed() {
        val t = makeTxn(treatment = FinancialTreatment.EXPENSE)
        val updated = ReviewStateMachine.forceTreatment(t, FinancialTreatment.IGNORED)
        assertEquals(FinancialTreatment.IGNORED, updated.financialTreatment)
        assertTrue(updated.userConfirmed)
        assertFalse(updated.needsReview)
    }

    @Test
    fun manuallyConfirmedTransactionIsIncludedInSpendingTotals() {
        // The transaction was auto-classified as needsReview=true. The user
        // then confirmed it (so userConfirmed=true). The spending calculator
        // must include it.
        val txns = listOf(
            makeTxn(id = 1, treatment = FinancialTreatment.EXPENSE, amount = BigDecimal("100"), needsReview = true, userConfirmed = true),
            makeTxn(id = 2, treatment = FinancialTreatment.EXPENSE, amount = BigDecimal("50"), needsReview = true, userConfirmed = false),
        )
        val b = SpendingCalculator.calculate(txns)
        // 100 (confirmed) is included; 50 (not confirmed) is excluded.
        assertEquals(BigDecimal("100.00"), b.grossExpenses)
    }

    @Test
    fun pendingTransactionDoesNotInflateConfirmedSpending() {
        val txns = listOf(
            makeTxn(id = 1, treatment = FinancialTreatment.EXPENSE, amount = BigDecimal("100"), needsReview = true, userConfirmed = false),
        )
        val b = SpendingCalculator.calculate(txns)
        assertEquals(BigDecimal("0.00"), b.grossExpenses)
        assertEquals(1, b.transactionsRequiringReview)
    }
}
