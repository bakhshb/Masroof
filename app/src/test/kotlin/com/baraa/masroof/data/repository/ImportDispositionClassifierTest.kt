package com.baraa.masroof.data.repository

import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ImportDispositionClassifierTest {

    @Test
    fun dispositionsAreMutuallyExclusiveByPriority() {
        assertEquals(
            ImportDisposition.UNREGISTERED_SENDER,
            ImportDispositionClassifier.classify(isUnregisteredSender = true, isExactDuplicate = true),
        )
        assertEquals(
            ImportDisposition.EXACT_DUPLICATE,
            ImportDispositionClassifier.classify(isExactDuplicate = true, isBeforeTrackingStart = true, needsConfirmation = true),
        )
        assertEquals(
            ImportDisposition.BEFORE_TRACKING_START,
            ImportDispositionClassifier.classify(isBeforeTrackingStart = true, accountMatched = true),
        )
        assertEquals(
            ImportDisposition.NEEDS_ACCOUNT,
            ImportDispositionClassifier.classify(accountMatched = false),
        )
        assertEquals(
            ImportDisposition.NEEDS_CONFIRMATION,
            ImportDispositionClassifier.classify(accountMatched = true, needsConfirmation = true),
        )
        assertEquals(
            ImportDisposition.READY,
            ImportDispositionClassifier.classify(accountMatched = true, needsConfirmation = false),
        )
    }

    @Test
    fun readyCountUsesExclusiveDispositionsWhenItemsPresent() {
        val items = listOf(
            preview(ImportDisposition.READY),
            preview(ImportDisposition.READY),
            preview(ImportDisposition.NEEDS_ACCOUNT),
            preview(ImportDisposition.EXACT_DUPLICATE),
            preview(ImportDisposition.BEFORE_TRACKING_START),
            preview(ImportDisposition.NEEDS_CONFIRMATION),
        )
        val preview = ScanPreview(
            recognizedTransactions = 6,
            needsReviewTransactions = 99,
            duplicateTransactions = 99,
            beforeTrackingStartCount = 99,
            perTransaction = items,
        )
        assertEquals(2, preview.readyCount)
        assertEquals(2, preview.reviewDispositionCount)
        assertTrue(preview.readyCount + preview.reviewDispositionCount <= items.size)
    }

    @Test
    fun possibleDuplicateHasPriorityOverNeedsAccount() {
        assertEquals(
            ImportDisposition.POSSIBLE_DUPLICATE,
            ImportDispositionClassifier.classify(
                isPossibleDuplicate = true,
                accountMatched = false,
            ),
        )
    }

    @Test
    fun nearDuplicateDetectorUsesTimeWindow() {
        val existing = listOf(
            com.baraa.masroof.data.db.TransactionEntity(
                id = 1,
                uniqueFingerprint = "a",
                smsTimestamp = 1_000_000L,
                originalSender = "bank",
                transactionType = TransactionType.PURCHASE,
                amount = BigDecimal.ONE,
                currency = com.baraa.masroof.transaction.Currency.SAR,
                merchantOrBeneficiary = "x",
                accountOrCardLastFourDigits = null,
                transactionDate = LocalDate.now(),
                transactionTime = null,
                status = com.baraa.masroof.transaction.TransactionStatus.COMPLETED,
                confidence = 80,
                parsingNotes = emptyList(),
                dateSource = com.baraa.masroof.data.db.DateSource.FROM_BODY,
                createdAt = 0,
                updatedAt = 0,
                transactionSimilarityKey = "same-key",
            ),
        )
        assertTrue(
            NearDuplicateDetector.isPossibleDuplicate(
                candidateTimestamp = 1_000_000L + NearDuplicateDetector.DUPLICATE_WINDOW_MILLIS / 2,
                candidateSimilarityKey = "same-key",
                existingByKey = existing,
            ),
        )
        assertTrue(
            !NearDuplicateDetector.isPossibleDuplicate(
                candidateTimestamp = 1_000_000L + NearDuplicateDetector.DUPLICATE_WINDOW_MILLIS + 1,
                candidateSimilarityKey = "same-key",
                existingByKey = existing,
            ),
        )
    }

    private fun preview(disposition: ImportDisposition) = ScanPreview.PreviewItem(
        smsId = 1L,
        sender = "bank",
        amount = BigDecimal.ONE,
        transactionType = TransactionType.PURCHASE,
        proposedAccountId = null,
        proposedAccountName = null,
        isDuplicate = disposition == ImportDisposition.EXACT_DUPLICATE || disposition == ImportDisposition.POSSIBLE_DUPLICATE,
        needsReview = disposition == ImportDisposition.NEEDS_ACCOUNT || disposition == ImportDisposition.NEEDS_CONFIRMATION,
        isBeforeTrackingStart = disposition == ImportDisposition.BEFORE_TRACKING_START,
        date = LocalDate.now(),
        disposition = disposition,
    )
}
