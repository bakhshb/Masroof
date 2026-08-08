package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.FakeTransactionRepository
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class TransactionCorrectionServiceTest {

    private fun baseTx(fingerprint: String = "fp-1"): TransactionEntity = TransactionEntity(
        id = 0,
        uniqueFingerprint = fingerprint,
        smsTimestamp = 1L,
        originalSender = "Bank",
        transactionType = TransactionType.PURCHASE,
        amount = BigDecimal("25.00"),
        currency = Currency.SAR,
        merchantOrBeneficiary = "Shop",
        accountOrCardLastFourDigits = "1234",
        transactionDate = LocalDate.of(2026, 8, 1),
        transactionTime = null,
        status = TransactionStatus.COMPLETED,
        confidence = 90,
        parsingNotes = emptyList(),
        dateSource = DateSource.FROM_BODY,
        createdAt = 1L,
        updatedAt = 1L,
        transactionSimilarityKey = "sim-$fingerprint",
        financialTreatment = FinancialTreatment.EXPENSE,
        categoryId = null,
        categorySource = CategorySource.UNCLASSIFIED,
        needsReview = false,
        userConfirmed = true,
        exclusionReason = null,
        sourceAccountId = 1L,
        destinationAccountId = null,
        linkedJournalEntryId = 100L,
        accountLinkSource = AccountLinkSource.USER,
        accountLinkConfidence = 100,
        accountLinkNeedsReview = false,
        postingStatus = TransactionPostingStatus.POSTED,
    )

    @Test
    fun reopenReversesPostedJournalAndResetsReviewFlags() = runBlocking {
        val repo = FakeTransactionRepository()
        val assignedId = repo.insert(baseTx("fp-a"))
        val withId = repo.getById(assignedId)!!.copy(
            linkedJournalEntryId = 100L,
            postingStatus = TransactionPostingStatus.POSTED,
            userConfirmed = true,
            needsReview = false,
            accountLinkNeedsReview = false,
            accountLinkSource = AccountLinkSource.USER,
        )
        repo.update(withId)

        var reversedId: Long? = null
        val service = TransactionCorrectionService(
            transactions = repo,
            journalReverser = JournalReverser { id ->
                reversedId = id
                999L
            },
            now = { 42L },
        )

        val result = service.reopenForCorrection(withId)
        assertTrue(result is CorrectionResult.Success)
        val reopened = (result as CorrectionResult.Success).transaction
        assertEquals(100L, reversedId)
        assertNull(reopened.linkedJournalEntryId)
        assertEquals(TransactionPostingStatus.NEEDS_REVIEW, reopened.postingStatus)
        assertTrue(reopened.needsReview)
        assertFalse(reopened.userConfirmed)
        assertTrue(reopened.accountLinkNeedsReview)
        assertEquals(AccountLinkSource.UNLINKED, reopened.accountLinkSource)
        assertEquals(42L, reopened.updatedAt)

        val persisted = repo.getById(withId.id)!!
        assertEquals(TransactionPostingStatus.NEEDS_REVIEW, persisted.postingStatus)
        assertNull(persisted.linkedJournalEntryId)
    }

    @Test
    fun reopenAlreadyReversedSkipsReverseCall() = runBlocking {
        val repo = FakeTransactionRepository()
        val assignedId = repo.insert(
            baseTx("fp-b").copy(postingStatus = TransactionPostingStatus.REVERSED),
        )
        val withId = repo.getById(assignedId)!!.copy(
            postingStatus = TransactionPostingStatus.REVERSED,
            linkedJournalEntryId = 55L,
        )
        repo.update(withId)

        var reverseCalls = 0
        val service = TransactionCorrectionService(
            transactions = repo,
            journalReverser = JournalReverser {
                reverseCalls++
                1L
            },
        )
        val result = service.reopenForCorrection(withId)
        assertTrue(result is CorrectionResult.Success)
        assertEquals(0, reverseCalls)
        assertEquals(
            TransactionPostingStatus.NEEDS_REVIEW,
            (result as CorrectionResult.Success).transaction.postingStatus,
        )
        assertNull(result.transaction.linkedJournalEntryId)
    }

    @Test
    fun rejectsUnpostedTransactionWithoutThrowing() = runBlocking {
        val repo = FakeTransactionRepository()
        val id = repo.insert(baseTx("fp-unposted").copy(postingStatus = TransactionPostingStatus.NEEDS_REVIEW))
        val tx = repo.getById(id)!!
        val service = TransactionCorrectionService(repo, JournalReverser { it })
        val result = service.reopenForCorrection(tx)
        assertTrue(result is CorrectionResult.ValidationError)
        assertEquals(
            "correction_requires_posted_or_reversed",
            (result as CorrectionResult.ValidationError).code,
        )
    }

    @Test
    fun reverseFailureReturnsFailureNotCrash() = runBlocking {
        val repo = FakeTransactionRepository()
        val assignedId = repo.insert(baseTx("fp-fail"))
        val withId = repo.getById(assignedId)!!.copy(
            postingStatus = TransactionPostingStatus.POSTED,
            linkedJournalEntryId = 7L,
        )
        repo.update(withId)
        val service = TransactionCorrectionService(
            transactions = repo,
            journalReverser = JournalReverser { error("journal_missing") },
        )
        val result = service.reopenForCorrection(withId)
        assertTrue(result is CorrectionResult.Failure)
        assertEquals(TransactionPostingStatus.POSTED, repo.getById(withId.id)!!.postingStatus)
    }

    @Test
    fun deletedTransactionReturnsValidationError() = runBlocking {
        val repo = FakeTransactionRepository()
        val service = TransactionCorrectionService(repo, JournalReverser { it })
        val result = service.reopenForCorrection(
            baseTx().copy(id = 404, postingStatus = TransactionPostingStatus.POSTED),
        )
        assertEquals("transaction_deleted", (result as CorrectionResult.ValidationError).code)
    }
}
