package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.FakeTransactionRepository
import com.baraa.masroof.data.repository.IdentifierForm
import com.baraa.masroof.data.repository.RoomFinancialAccountRepository
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class HistoricalAccountRelinkServiceTest {

    private fun accountEntity(id: Long) = FinancialAccountEntity(
        id = id,
        displayName = "A$id",
        institutionName = "Bank",
        accountType = AccountType.BANK_ACCOUNT,
        accountNature = AccountNature.ASSET,
        currency = Currency.SAR,
        openingBalance = BigDecimal.ZERO,
        openingBalanceDate = 0L,
        includeInNetWorth = true,
        includeInLiquidity = true,
        isOwnedByUser = true,
        systemAccountKey = null,
        isActive = true,
        notes = null,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun tx(
        id: Long,
        lastFour: String?,
        posting: TransactionPostingStatus = TransactionPostingStatus.NEEDS_REVIEW,
        journalId: Long? = null,
        linkSource: AccountLinkSource = AccountLinkSource.UNLINKED
    ) = TransactionEntity(
        id = id,
        uniqueFingerprint = "fp$id",
        smsTimestamp = id,
        originalSender = "bank",
        transactionType = TransactionType.PURCHASE,
        amount = BigDecimal.ONE,
        currency = Currency.SAR,
        merchantOrBeneficiary = null,
        accountOrCardLastFourDigits = lastFour,
        transactionDate = null,
        transactionTime = null,
        status = TransactionStatus.COMPLETED,
        confidence = 90,
        parsingNotes = emptyList(),
        dateSource = DateSource.FROM_BODY,
        createdAt = id,
        updatedAt = id,
        financialTreatment = FinancialTreatment.EXPENSE,
        needsReview = true,
        accountLinkSource = linkSource,
        accountLinkNeedsReview = true,
        postingStatus = posting,
        linkedJournalEntryId = journalId
    )

    @Test
    fun relinkUpdatesUnpostedAndSkipsPosted() = runBlocking {
        val accountDao = FakeAccountDao(listOf(accountEntity(1)))
        val identifierRepo = AccountIdentifierRepository(FakeIdentifierDao(), accountDao)
        identifierRepo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "A", "7271"))
        val transactions = FakeTransactionRepository()
        transactions.insert(tx(1, "7271"))
        transactions.insert(
            tx(
                id = 2,
                lastFour = "7271",
                posting = TransactionPostingStatus.POSTED,
                journalId = 99L,
                linkSource = AccountLinkSource.USER
            )
        )
        val financialRepo = RoomFinancialAccountRepository(accountDao)
        val service = HistoricalAccountRelinkService(transactions, financialRepo, identifierRepo)
        val result = service.relinkUnposted()
        assertEquals(1, result.updated)
        assertEquals(1, result.skippedPosted)
        assertEquals(1L, transactions.getById(1)?.sourceAccountId)
        assertEquals(TransactionPostingStatus.NEEDS_REVIEW, transactions.getById(1)?.postingStatus)
        assertNull(transactions.getById(2)?.sourceAccountId)
        assertEquals(TransactionPostingStatus.POSTED, transactions.getById(2)?.postingStatus)
        assertTrue(result.linkedConfirmed >= 1)
    }

    @Test
    fun dryRunDoesNotPersist() = runBlocking {
        val accountDao = FakeAccountDao(listOf(accountEntity(1)))
        val identifierRepo = AccountIdentifierRepository(FakeIdentifierDao(), accountDao)
        identifierRepo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "A", "7271"))
        val transactions = FakeTransactionRepository()
        transactions.insert(tx(1, "7271"))
        val service = HistoricalAccountRelinkService(
            transactions,
            RoomFinancialAccountRepository(accountDao),
            identifierRepo
        )
        val result = service.relinkUnposted(dryRun = true)
        assertEquals(1, result.updated)
        assertNull(transactions.getById(1)?.sourceAccountId)
    }

    @Test
    fun rematchesWrongTentativeLinkWhenLastFourNowUnique() = runBlocking {
        val accountDao = FakeAccountDao(listOf(accountEntity(1), accountEntity(2)))
        val identifierRepo = AccountIdentifierRepository(FakeIdentifierDao(), accountDao)
        identifierRepo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "A", "7271"))
        identifierRepo.addOrUpdate(2, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "B", "9999"))
        val transactions = FakeTransactionRepository()
        val proposed = tx(1, "7271", linkSource = AccountLinkSource.LAST_FOUR_MATCH).copy(
            sourceAccountId = 2L,
            accountLinkNeedsReview = true,
            needsReview = true,
        )
        transactions.insert(proposed)
        val service = HistoricalAccountRelinkService(
            transactions,
            RoomFinancialAccountRepository(accountDao),
            identifierRepo,
        )
        val result = service.relinkUnposted()
        assertEquals(1, result.updated)
        assertEquals(1L, transactions.getById(1)?.sourceAccountId)
        assertEquals(false, transactions.getById(1)?.accountLinkNeedsReview)
    }

    @Test
    fun neverTouchesUserConfirmedLinks() = runBlocking {
        val accountDao = FakeAccountDao(listOf(accountEntity(1)))
        val identifierRepo = AccountIdentifierRepository(FakeIdentifierDao(), accountDao)
        identifierRepo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "A", "7271"))
        val transactions = FakeTransactionRepository()
        transactions.insert(
            tx(1, "7271", linkSource = AccountLinkSource.USER).copy(
                sourceAccountId = null,
                accountLinkNeedsReview = false,
                needsReview = false,
            ),
        )
        val service = HistoricalAccountRelinkService(
            transactions,
            RoomFinancialAccountRepository(accountDao),
            identifierRepo,
        )
        val result = service.relinkUnposted()
        assertEquals(0, result.updated)
        assertNull(transactions.getById(1)?.sourceAccountId)
    }

    @Test
    fun secondAccountIdentifierRelinksPreviouslyUnlinkedRows() = runBlocking {
        val accountDao = FakeAccountDao(listOf(accountEntity(1), accountEntity(2)))
        val identifierRepo = AccountIdentifierRepository(FakeIdentifierDao(), accountDao)
        identifierRepo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.SENDER_ALIAS, "bank", "bank"))
        identifierRepo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "A", "1111"))
        // Account 2 not yet identified when tx was imported unlinked
        val transactions = FakeTransactionRepository()
        transactions.insert(tx(1, "2222", linkSource = AccountLinkSource.UNLINKED))
        identifierRepo.addOrUpdate(2, IdentifierForm(AccountIdentifierType.SENDER_ALIAS, "bank", "bank"))
        identifierRepo.addOrUpdate(2, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "B", "2222"))
        val service = HistoricalAccountRelinkService(
            transactions,
            RoomFinancialAccountRepository(accountDao),
            identifierRepo,
        )
        val result = service.relinkUnposted()
        assertEquals(1, result.updated)
        assertEquals(2L, transactions.getById(1)?.sourceAccountId)
        assertTrue(result.linkedConfirmed >= 1)
    }

    @Test
    fun pendingReviewPurchaseReclassifiedToExpenseAndLinked() = runBlocking {
        val accountDao = FakeAccountDao(listOf(accountEntity(1)))
        val identifierRepo = AccountIdentifierRepository(FakeIdentifierDao(), accountDao)
        identifierRepo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "A", "7271"))
        val transactions = FakeTransactionRepository()
        transactions.insert(
            tx(1, "7271").copy(
                financialTreatment = FinancialTreatment.PENDING_REVIEW,
                transactionType = TransactionType.PURCHASE,
            ),
        )
        val service = HistoricalAccountRelinkService(
            transactions,
            RoomFinancialAccountRepository(accountDao),
            identifierRepo,
        )
        val result = service.relinkUnposted()
        assertEquals(1, result.updated)
        val row = transactions.getById(1)!!
        assertEquals(FinancialTreatment.EXPENSE, row.financialTreatment)
        assertEquals(1L, row.sourceAccountId)
        assertEquals(false, row.accountLinkNeedsReview)
        assertEquals(false, row.needsReview)
        assertTrue(result.linkedConfirmed >= 1)
    }

    @Test
    fun pendingReviewTransferOutBecomesExpenseWithoutPostingWhenNoJournalService() = runBlocking {
        val accountDao = FakeAccountDao(listOf(accountEntity(1)))
        val identifierRepo = AccountIdentifierRepository(FakeIdentifierDao(), accountDao)
        identifierRepo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "A", "7271"))
        val transactions = FakeTransactionRepository()
        transactions.insert(
            tx(1, "7271").copy(
                financialTreatment = FinancialTreatment.PENDING_REVIEW,
                transactionType = TransactionType.TRANSFER_OUT,
            ),
        )
        val service = HistoricalAccountRelinkService(
            transactions,
            RoomFinancialAccountRepository(accountDao),
            identifierRepo,
        )
        val result = service.relinkUnposted()
        val row = transactions.getById(1)!!
        assertEquals(FinancialTreatment.EXPENSE, row.financialTreatment)
        assertEquals(1L, row.sourceAccountId)
        assertNull(row.linkedJournalEntryId)
        assertEquals(0, result.posted)
    }

    @Test
    fun stuckNeedsReviewLastFourMatchIsRelinkCandidate() = runBlocking {
        val accountDao = FakeAccountDao(listOf(accountEntity(1)))
        val identifierRepo = AccountIdentifierRepository(FakeIdentifierDao(), accountDao)
        identifierRepo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "A", "7271"))
        val transactions = FakeTransactionRepository()
        transactions.insert(
            tx(1, "7271", linkSource = AccountLinkSource.LAST_FOUR_MATCH).copy(
                sourceAccountId = null,
                accountLinkNeedsReview = false,
                needsReview = true,
                financialTreatment = FinancialTreatment.PENDING_REVIEW,
            ),
        )
        val service = HistoricalAccountRelinkService(
            transactions,
            RoomFinancialAccountRepository(accountDao),
            identifierRepo,
        )
        val result = service.relinkUnposted()
        assertEquals(1, result.updated)
        assertEquals(FinancialTreatment.EXPENSE, transactions.getById(1)?.financialTreatment)
        assertEquals(1L, transactions.getById(1)?.sourceAccountId)
    }

    @Test
    fun resolveTreatmentMapsPurchasePendingToExpense() {
        val pending = tx(1, "7271").copy(financialTreatment = FinancialTreatment.PENDING_REVIEW)
        assertEquals(FinancialTreatment.EXPENSE, HistoricalAccountRelinkService.resolveTreatment(pending))
        val transferOut = pending.copy(transactionType = TransactionType.TRANSFER_OUT)
        assertEquals(FinancialTreatment.EXPENSE, HistoricalAccountRelinkService.resolveTreatment(transferOut))
        val transferIn = pending.copy(transactionType = TransactionType.TRANSFER_IN)
        assertEquals(FinancialTreatment.INCOME, HistoricalAccountRelinkService.resolveTreatment(transferIn))
    }
}
