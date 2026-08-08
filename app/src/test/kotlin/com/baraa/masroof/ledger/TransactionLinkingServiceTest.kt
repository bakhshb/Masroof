package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.FakeTransactionRepository
import com.baraa.masroof.data.repository.IdentifierAddResult
import com.baraa.masroof.data.repository.toDomain
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Regression coverage for the manual classify + account-link flow that previously
 * crashed the review screen (uncaught require / active_journal_exists).
 */
class TransactionLinkingServiceTest {

    private class FakeJournalLinkWriter(
        var postValid: Boolean = true,
        var throwOnReplace: Throwable? = null,
        var throwOnPost: Throwable? = null,
    ) : JournalLinkWriter {
        var replaceCount = 0
        var postCount = 0
        var discardCount = 0
        private var nextId = 500L
        val drafts = mutableListOf<JournalDraft>()
        val discardedTxIds = mutableListOf<Long>()

        override suspend fun replaceDraft(transactionId: Long, draft: JournalDraft): Long {
            throwOnReplace?.let { throw it }
            replaceCount++
            drafts.add(draft)
            return nextId++
        }

        override suspend fun post(journalId: Long): LedgerValidation {
            throwOnPost?.let { throw it }
            postCount++
            return if (postValid) LedgerValidation.valid() else LedgerValidation.invalid("unbalanced")
        }

        override suspend fun discardUnpostedDrafts(transactionId: Long) {
            discardCount++
            discardedTxIds += transactionId
            drafts.removeAll { it.sourceTransactionId == transactionId }
        }
    }

    private fun accountEntity(
        id: Long,
        type: AccountType = AccountType.BANK_ACCOUNT,
        name: String = "A$id",
    ) = FinancialAccountEntity(
        id = id,
        displayName = name,
        institutionName = "Bank",
        accountType = type,
        accountNature = AccountNature.defaultNatureFor(type),
        currency = Currency.SAR,
        openingBalance = BigDecimal.ZERO,
        openingBalanceDate = 0L,
        includeInNetWorth = true,
        includeInLiquidity = type != AccountType.CREDIT_CARD,
        isOwnedByUser = true,
        systemAccountKey = null,
        isActive = true,
        notes = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun account(id: Long, type: AccountType = AccountType.BANK_ACCOUNT): FinancialAccount =
        accountEntity(id, type).toDomain()

    private fun baseTx(
        id: Long = 0,
        last4: String? = "3001",
        treatment: FinancialTreatment = FinancialTreatment.PENDING_REVIEW,
        posting: TransactionPostingStatus = TransactionPostingStatus.NEEDS_REVIEW,
        fingerprint: String = "fp-$id-${System.nanoTime()}",
    ) = TransactionEntity(
        id = id,
        uniqueFingerprint = fingerprint,
        smsTimestamp = 1L,
        originalSender = "Jazira Bank",
        transactionType = TransactionType.TRANSFER_OUT,
        amount = BigDecimal("1789.00"),
        currency = Currency.SAR,
        merchantOrBeneficiary = "ولاء",
        accountOrCardLastFourDigits = last4,
        transactionDate = LocalDate.of(2026, 7, 27),
        transactionTime = null,
        status = TransactionStatus.COMPLETED,
        confidence = 90,
        parsingNotes = emptyList(),
        dateSource = DateSource.FROM_BODY,
        createdAt = 1L,
        updatedAt = 1L,
        financialTreatment = treatment,
        needsReview = true,
        userConfirmed = false,
        postingStatus = posting,
        accountLinkNeedsReview = true,
    )

    private fun harness(
        accounts: List<FinancialAccountEntity> = listOf(accountEntity(1), accountEntity(2, AccountType.CREDIT_CARD)),
        journals: FakeJournalLinkWriter = FakeJournalLinkWriter(),
        seedTx: TransactionEntity = baseTx(),
    ): Quadruple {
        val txRepo = FakeTransactionRepository()
        val id = runBlocking { txRepo.insert(seedTx) }
        val stored = runBlocking { txRepo.getById(id)!! }
        val accountDao = FakeAccountDao(accounts)
        val idRepo = AccountIdentifierRepository(FakeIdentifierDao(), accountDao)
        val generator = JournalGenerationService(systemAccounts = { 900L + it.ordinal })
        val service = TransactionLinkingService(
            transactions = txRepo,
            journals = journals,
            generator = generator,
            identifierRepository = idRepo,
            now = { 42L },
        )
        return Quadruple(service, txRepo, idRepo, journals, stored, accounts.map { it.toDomain() })
    }

    private data class Quadruple(
        val service: TransactionLinkingService,
        val txRepo: FakeTransactionRepository,
        val idRepo: AccountIdentifierRepository,
        val journals: FakeJournalLinkWriter,
        val tx: TransactionEntity,
        val accounts: List<FinancialAccount>,
    )

    @Test
    fun manualClassificationAndValidAccountLinkSucceeds() = runBlocking {
        val h = harness()
        val result = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertTrue(result is LinkApplyResult.Success)
        val success = result as LinkApplyResult.Success
        assertEquals(TransactionPostingStatus.POSTED, success.transaction.postingStatus)
        assertFalse(success.transaction.needsReview)
        assertEquals(1L, success.transaction.sourceAccountId)
        assertEquals(1, h.journals.replaceCount)
        assertEquals(1, h.journals.postCount)
        assertNull(h.txRepo.getById(h.tx.id)!!.let { t ->
            if (t.postingStatus == TransactionPostingStatus.NEEDS_REVIEW) t else null
        })
    }

    @Test
    fun accountHasNoIdentifierYet_linkWithoutSavingIdentifier() = runBlocking {
        val h = harness()
        assertTrue(h.idRepo.getForAccount(1L).isEmpty())
        val result = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            identifierToAdd = null,
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertTrue(result is LinkApplyResult.Success)
        assertTrue(h.idRepo.getForAccount(1L).isEmpty())
    }

    @Test
    fun userLinksAndSavesIdentifier() = runBlocking {
        val h = harness()
        val result = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            identifierToAdd = IdentifierCandidate(
                identifierType = AccountIdentifierType.ACCOUNT_LAST4,
                normalizedLastFour = "3001",
                transactionRole = IdentifierTransactionRole.SOURCE,
                sourceField = "خصمت من حساب",
                confidence = 90,
            ),
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertTrue(result is LinkApplyResult.Success)
        val ids = h.idRepo.getForAccount(1L)
        assertEquals(1, ids.size)
        assertEquals("3001", ids.single().normalizedValue)
        assertEquals(AccountIdentifierType.ACCOUNT_LAST4, ids.single().identifierType)
        assertEquals(IdentifierAddResult.Added, (result as LinkApplyResult.Success).identifierOutcome?.result)
    }

    @Test
    fun identifierAlreadyBelongsToAnotherAccount_surfacesConflictWarningNotCrash() = runBlocking {
        val accounts = listOf(accountEntity(1), accountEntity(3, name = "Other"))
        val h = harness(accounts = accounts)
        h.idRepo.addOrUpdate(
            accountId = 3L,
            form = com.baraa.masroof.data.repository.IdentifierForm(
                identifierType = AccountIdentifierType.ACCOUNT_LAST4,
                displayLabel = "حساب",
                rawValue = "3001",
            ),
        )
        val result = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            identifierToAdd = IdentifierCandidate(
                identifierType = AccountIdentifierType.ACCOUNT_LAST4,
                normalizedLastFour = "3001",
                transactionRole = IdentifierTransactionRole.SOURCE,
                sourceField = "خصمت من حساب",
                confidence = 90,
            ),
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertTrue(result is LinkApplyResult.Success)
        val success = result as LinkApplyResult.Success
        assertEquals(IdentifierAddResult.AddedWithConflict, success.identifierOutcome?.result)
        assertNotNull(success.identifierOutcome?.message)
        assertTrue(success.identifierOutcome!!.message!!.contains("حساب آخر"))
    }

    @Test
    fun incompatibleAccountType_validationError() = runBlocking {
        val h = harness()
        val result = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = 1L, // bank, not credit card
            accounts = h.accounts,
            financialTreatment = FinancialTreatment.CREDIT_CARD_PAYMENT,
        )
        // same account first fails before type check when ids equal
        assertTrue(result is LinkApplyResult.ValidationError)
        assertEquals("same_accounts", (result as LinkApplyResult.ValidationError).code)

        val result2 = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = 1L, // wait - need bank as dest that's not credit
            accounts = listOf(account(1), account(2, AccountType.BANK_ACCOUNT)),
            financialTreatment = FinancialTreatment.CREDIT_CARD_PAYMENT,
        )
        // same ids again
        assertTrue(result2 is LinkApplyResult.ValidationError)

        val result3 = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = 2L,
            accounts = listOf(account(1), account(2, AccountType.BANK_ACCOUNT)),
            financialTreatment = FinancialTreatment.CREDIT_CARD_PAYMENT,
        )
        assertTrue(result3 is LinkApplyResult.ValidationError)
        assertEquals("incompatible_account_type", (result3 as LinkApplyResult.ValidationError).code)
    }

    @Test
    fun missingOrStaleTransactionId_validationError() = runBlocking {
        val h = harness()
        val missingId = h.tx.copy(id = 0)
        val r1 = h.service.applyUserLink(
            transaction = missingId,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertEquals("missing_transaction_id", (r1 as LinkApplyResult.ValidationError).code)

        val stale = h.tx.copy(id = 99999)
        val r2 = h.service.applyUserLink(
            transaction = stale,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertEquals("transaction_deleted", (r2 as LinkApplyResult.ValidationError).code)
    }

    @Test
    fun accountDeletedBeforeSave_validationError() = runBlocking {
        val h = harness()
        val result = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 77L,
            destinationAccountId = null,
            accounts = h.accounts, // 77 not present
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertEquals("source_account_missing", (result as LinkApplyResult.ValidationError).code)
        assertEquals(0, h.journals.replaceCount)
    }

    @Test
    fun postedJournalTransaction_immutableValidation() = runBlocking {
        val posted = baseTx(posting = TransactionPostingStatus.POSTED, treatment = FinancialTreatment.EXPENSE)
        val h = harness(seedTx = posted)
        val result = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertEquals("posted_immutable", (result as LinkApplyResult.ValidationError).code)
        assertEquals(0, h.journals.replaceCount)
    }

    @Test
    fun roomConstraintFailure_returnsFailureNotCrash() = runBlocking {
        val journals = FakeJournalLinkWriter(
            throwOnReplace = RuntimeException("UNIQUE constraint failed: journals"),
        )
        val h = harness(journals = journals)
        val before = h.txRepo.getById(h.tx.id)!!
        val result = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertTrue(result is LinkApplyResult.Failure)
        assertNotNull((result as LinkApplyResult.Failure).cause)
        // Rolled back to pre-save snapshot.
        val after = h.txRepo.getById(h.tx.id)!!
        assertEquals(before.postingStatus, after.postingStatus)
        assertEquals(before.financialTreatment, after.financialTreatment)
        assertNull(after.sourceAccountId)
        assertEquals(1, journals.discardCount)
    }

    @Test
    fun postFailure_restoresTransactionAndDiscardsDraft() = runBlocking {
        val journals = FakeJournalLinkWriter(postValid = false)
        val h = harness(journals = journals)
        val before = h.txRepo.getById(h.tx.id)!!
        val result = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertEquals("journal_not_posted", (result as LinkApplyResult.ValidationError).code)
        val after = h.txRepo.getById(h.tx.id)!!
        assertEquals(before.financialTreatment, after.financialTreatment)
        assertNull(after.sourceAccountId)
        assertEquals(TransactionPostingStatus.NEEDS_REVIEW, after.postingStatus)
        assertEquals(1, journals.discardCount)
    }

    @Test
    fun journalNotGenerated_doesNotMutateTransaction() = runBlocking {
        val h = harness()
        val before = h.txRepo.getById(h.tx.id)!!
        // Two-sided treatment without generating a draft when accounts incomplete —
        // CREDIT_CARD_PAYMENT already validated both accounts; use INVESTMENT with
        // only source by forcing generator null via pending amount? Use empty amount path:
        val noAmountId = h.txRepo.insert(baseTx(fingerprint = "no-amt").copy(amount = null))
        val noAmount = h.txRepo.getById(noAmountId)!!
        val result = h.service.applyUserLink(
            transaction = noAmount,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertEquals("journal_not_generated", (result as LinkApplyResult.ValidationError).code)
        assertEquals(0, h.journals.replaceCount)
        assertEquals(before.financialTreatment, h.txRepo.getById(h.tx.id)!!.financialTreatment)
        assertEquals(FinancialTreatment.PENDING_REVIEW, h.txRepo.getById(noAmountId)!!.financialTreatment)
        assertNull(h.txRepo.getById(noAmountId)!!.sourceAccountId)
    }

    @Test
    fun identifierRejected_doesNotMutateTransactionOrCreateJournal() = runBlocking {
        val h = harness()
        val before = h.txRepo.getById(h.tx.id)!!
        val result = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            identifierToAdd = IdentifierCandidate(
                identifierType = AccountIdentifierType.CREDIT_CARD_LAST4,
                normalizedLastFour = "3001",
                transactionRole = IdentifierTransactionRole.SOURCE,
                sourceField = "test",
                confidence = 90,
            ),
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertEquals("identifier_rejected", (result as LinkApplyResult.ValidationError).code)
        assertEquals(0, h.journals.replaceCount)
        assertEquals(before.financialTreatment, h.txRepo.getById(h.tx.id)!!.financialTreatment)
        assertNull(h.txRepo.getById(h.tx.id)!!.sourceAccountId)
    }

    @Test
    fun inFlightGuard_rejectsOverlappingSave() = runBlocking {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val journals = object : JournalLinkWriter {
            override suspend fun replaceDraft(transactionId: Long, draft: JournalDraft): Long {
                gate.await()
                return 1L
            }
            override suspend fun post(journalId: Long) = LedgerValidation.valid()
            override suspend fun discardUnpostedDrafts(transactionId: Long) = Unit
        }
        val h = harness(journals = FakeJournalLinkWriter())
        // Rebuild service with blocking writer
        val blocking = TransactionLinkingService(
            transactions = h.txRepo,
            journals = journals,
            generator = JournalGenerationService(systemAccounts = { 900L + it.ordinal }),
            identifierRepository = h.idRepo,
            now = { 42L },
        )
        val first = async {
            blocking.applyUserLink(
                transaction = h.tx,
                sourceAccountId = 1L,
                destinationAccountId = null,
                accounts = h.accounts,
                financialTreatment = FinancialTreatment.EXPENSE,
            )
        }
        // Wait until first is in-flight (replaceDraft suspended on gate).
        delay(50)
        val second = blocking.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertEquals("save_in_progress", (second as LinkApplyResult.ValidationError).code)
        gate.complete(Unit)
        assertTrue(first.await() is LinkApplyResult.Success)
    }

    @Test
    fun repeatedTapSafe_secondCallReplacesDraft() = runBlocking {
        val journals = FakeJournalLinkWriter()
        val h = harness(journals = journals)
        val first = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertTrue(first is LinkApplyResult.Success)

        // Simulate UI allowing a second call before list refresh (stale NEEDS_REVIEW view).
        // Fresh getById sees POSTED → immutable, not a crash.
        val second = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertEquals("posted_immutable", (second as LinkApplyResult.ValidationError).code)
        assertEquals(1, journals.replaceCount)
    }

    @Test
    fun concurrentReplaceThrowsActiveJournal_mapsToValidation() = runBlocking {
        val journals = FakeJournalLinkWriter(
            throwOnReplace = IllegalArgumentException("active_journal_exists"),
        )
        val h = harness(journals = journals)
        val result = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        assertTrue(result is LinkApplyResult.ValidationError)
        assertEquals("active_journal_exists", (result as LinkApplyResult.ValidationError).code)
    }

    @Test
    fun saveSucceedsAndReviewedItemLeavesNeedsReview() = runBlocking {
        val h = harness()
        val before = h.txRepo.getAllNewestFirst().filter {
            it.postingStatus == TransactionPostingStatus.NEEDS_REVIEW || it.needsReview
        }
        assertEquals(1, before.size)
        h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        val after = h.txRepo.getAllNewestFirst().filter {
            it.postingStatus == TransactionPostingStatus.NEEDS_REVIEW ||
                (it.needsReview && it.postingStatus != TransactionPostingStatus.POSTED)
        }
        assertTrue(after.none { it.id == h.tx.id })
        assertEquals(TransactionPostingStatus.POSTED, h.txRepo.getById(h.tx.id)!!.postingStatus)
    }

    @Test
    fun outgoingTransferUsesExpenseTreatmentWithSourceAccount3001() = runBlocking {
        val h = harness(seedTx = baseTx(last4 = "3001", treatment = FinancialTreatment.PENDING_REVIEW))
        val result = h.service.applyUserLink(
            transaction = h.tx,
            sourceAccountId = 1L,
            destinationAccountId = null,
            accounts = h.accounts,
            financialTreatment = FinancialTreatment.EXPENSE,
            identifierToAdd = IdentifierCandidate(
                identifierType = AccountIdentifierType.ACCOUNT_LAST4,
                normalizedLastFour = "3001",
                transactionRole = IdentifierTransactionRole.SOURCE,
                sourceField = "خصمت من حساب",
                confidence = 95,
            ),
        )
        assertTrue(result is LinkApplyResult.Success)
        assertEquals("3001", h.idRepo.getForAccount(1L).single().normalizedValue)
        // Destination IBAN 6810 must never have been saved.
        assertTrue(h.idRepo.getForAccount(1L).none { it.normalizedValue == "6810" })
    }
}
