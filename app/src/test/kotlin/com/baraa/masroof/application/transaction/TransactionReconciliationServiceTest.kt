package com.baraa.masroof.application.transaction

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.data.repository.RoomFinancialTransactionRepository
import com.baraa.masroof.data.repository.RoomParsedEventRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.ids.TransactionIdFactory
import com.baraa.masroof.domain.matching.TransactionMatcher
import com.baraa.masroof.domain.matching.TransferMatchCandidate
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.PurchaseChannel
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.ownership.OwnershipResolver
import com.baraa.masroof.domain.repository.NoOpLoanRegistryRepository
import com.baraa.masroof.domain.ownership.RegistryIdentity
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.sms.hash.SmsBodyHasher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TransactionReconciliationServiceTest {

    private lateinit var db: MasroofDatabase
    private lateinit var rawRepo: RoomRawSmsRepository
    private lateinit var parsedRepo: RoomParsedEventRepository
    private lateinit var ftRepo: RoomFinancialTransactionRepository
    private lateinit var accounts: RoomAccountRegistryRepository
    private lateinit var cards: RoomCardRegistryRepository
    private lateinit var confirmation: OwnershipConfirmationService
    private lateinit var reconciliation: TransactionReconciliationService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        rawRepo = RoomRawSmsRepository(db.rawSmsDao())
        parsedRepo = RoomParsedEventRepository(db.parsedEventDao())
        ftRepo = RoomFinancialTransactionRepository(db.financialTransactionDao(), db.parsedEventDao())
        accounts = RoomAccountRegistryRepository.from(db)
        cards = RoomCardRegistryRepository.from(db)
        confirmation = OwnershipConfirmationService(accounts, cards)
        reconciliation = TransactionReconciliationService(
            parsedEventRepository = parsedRepo,
            rawSmsRepository = rawRepo,
            financialTransactionRepository = ftRepo,
            ownershipResolver = OwnershipResolver(accounts, cards, NoOpLoanRegistryRepository),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun staleOtpLinkedTransaction_isRemovedOnReconcile() = runBlocking {
        confirmation.confirmCardOwned(CardReference(Bank.BANK_ALJAZIRA, "7271"))
        val otpBody =
            """
            One Time Password for Online Purchase
            Code: 8811
            For: SAUDI ELECTRICITY COMPANY
            Amount: SAR 438.5
            Date: 2026-08-12 07:49
            """.trimIndent()
        persistEvent(
            smsId = "sms-otp-dup",
            body = otpBody,
            event = event(
                id = "pe-otp-dup",
                rawSmsId = "sms-otp-dup",
                family = MessageFamily.OTP,
                amount = null,
            ),
        )
        val staleTx = com.baraa.masroof.domain.model.FinancialTransaction(
            id = TransactionIdFactory.fromRawSmsIds(listOf("sms-otp-dup")),
            type = FinancialTransactionType.EXPENSE,
            amount = money("438.50"),
            occurredAt = Instant.parse("2026-08-12T04:49:00Z"),
            sourceContainerId = FinancialContainerIdFactory.cardId(Bank.BANK_ALJAZIRA, "7271"),
            destinationContainerId = null,
            merchant = null,
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = listOf("pe-otp-dup"),
        )
        ftRepo.save(staleTx, listOf("sms-otp-dup"))
        assertEquals(1, ftRepo.listAll().size)

        val summary = reconciliation.reconcileStoredEvents()
        assertEquals(0, summary.assembledSingle)
        assertEquals(1, summary.ignored)
        assertTrue(ftRepo.listAll().isEmpty())
        assertFalse(ftRepo.isRawSmsLinked("sms-otp-dup"))
    }

    @Test
    fun purchase_assemblesExpense() = runBlocking {
        persistEvent(
            smsId = "sms-buy",
            event = event(
                id = "pe-buy",
                rawSmsId = "sms-buy",
                family = MessageFamily.PURCHASE,
                amount = money("51.99"),
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
                channel = PurchaseChannel.ONLINE,
                merchant = "Keeta",
            ),
        )
        confirmation.confirmCardOwned(CardReference(Bank.BANK_ALJAZIRA, "7271"))
        val summary = reconciliation.reconcileStoredEvents()
        assertEquals(1, summary.assembledSingle)
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.EXPENSE, tx.type)
        assertEquals(money("51.99"), tx.amount)
        assertEquals(FinancialContainerIdFactory.cardId(Bank.BANK_ALJAZIRA, "7271"), tx.sourceContainerId)
        assertNull(tx.categoryId)
    }

    @Test
    fun cardPayment_isCreditCardPayment_notExpense() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmCardOwned(CardReference(Bank.BANK_ALJAZIRA, "7271"))
        persistEvent(
            smsId = "sms-ccp",
            event = event(
                id = "pe-ccp",
                rawSmsId = "sms-ccp",
                family = MessageFamily.CARD_PAYMENT,
                amount = money("200.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
            ),
        )
        reconciliation.reconcileStoredEvents()
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.CREDIT_CARD_PAYMENT, tx.type)
        assertNotEquals(FinancialTransactionType.EXPENSE, tx.type)
        assertEquals(FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"), tx.sourceContainerId)
        assertEquals(FinancialContainerIdFactory.cardId(Bank.BANK_ALJAZIRA, "7271"), tx.destinationContainerId)
        // Registry entry exists; classification did not require inventing CardType.
        assertNotNull(cards.get(CardReference(Bank.BANK_ALJAZIRA, "7271")))
    }

    @Test
    fun purchasePlusCardPayment_onlyOneExpense() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmCardOwned(CardReference(Bank.BANK_ALJAZIRA, "7271"))
        persistEvent(
            smsId = "sms-a",
            event = event(
                id = "pe-a",
                rawSmsId = "sms-a",
                family = MessageFamily.PURCHASE,
                amount = money("51.99"),
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
                channel = PurchaseChannel.ONLINE,
            ),
        )
        persistEvent(
            smsId = "sms-b",
            event = event(
                id = "pe-b",
                rawSmsId = "sms-b",
                family = MessageFamily.CARD_PAYMENT,
                amount = money("51.99"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
            ),
            at = Instant.parse("2026-08-02T12:00:00Z"),
        )
        reconciliation.reconcileStoredEvents()
        val txs = ftRepo.listAll()
        assertEquals(2, txs.size)
        assertEquals(1, txs.count { it.type == FinancialTransactionType.EXPENSE })
        assertEquals(1, txs.count { it.type == FinancialTransactionType.CREDIT_CARD_PAYMENT })
    }

    @Test
    fun refund_notIncome_usesDestinationContainerId() = runBlocking {
        persistEvent(
            smsId = "sms-rf",
            event = event(
                id = "pe-rf",
                rawSmsId = "sms-rf",
                family = MessageFamily.REFUND,
                amount = money("10.00"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
            ),
        )
        reconciliation.reconcileStoredEvents()
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.REFUND, tx.type)
        assertNotEquals(FinancialTransactionType.INCOME, tx.type)
        assertNull(tx.sourceContainerId)
        assertEquals(FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"), tx.destinationContainerId)
    }

    @Test
    fun cardOnlyRefund_usesCardDestinationContainerId() = runBlocking {
        persistEvent(
            smsId = "sms-rf-card",
            event = event(
                id = "pe-rf-card",
                rawSmsId = "sms-rf-card",
                family = MessageFamily.REFUND,
                amount = money("12.00"),
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
            ),
        )
        reconciliation.reconcileStoredEvents()
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.REFUND, tx.type)
        assertNull(tx.sourceContainerId)
        assertEquals(FinancialContainerIdFactory.cardId(Bank.BANK_ALJAZIRA, "7271"), tx.destinationContainerId)
    }

    @Test
    fun withdrawal_notExpense() = runBlocking {
        persistEvent(
            smsId = "sms-wd",
            event = event(
                id = "pe-wd",
                rawSmsId = "sms-wd",
                family = MessageFamily.WITHDRAWAL,
                amount = money("100.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
            ),
        )
        reconciliation.reconcileStoredEvents()
        assertEquals(FinancialTransactionType.CASH_WITHDRAWAL, ftRepo.listAll().single().type)
    }

    @Test
    fun fee_type() = runBlocking {
        persistEvent(
            smsId = "sms-fee",
            event = event(
                id = "pe-fee",
                rawSmsId = "sms-fee",
                family = MessageFamily.FEE,
                amount = money("2.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
            ),
        )
        reconciliation.reconcileStoredEvents()
        assertEquals(FinancialTransactionType.FEE, ftRepo.listAll().single().type)
    }

    @Test
    fun wifeIntraBank_externalTransferIn() = runBlocking {
        val wife = AccountReference(Bank.BANK_ALJAZIRA, "wife1")
        val user = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        confirmation.markAccountExternal(wife)
        confirmation.confirmAccountOwned(user)
        persistEvent(
            smsId = "sms-wife",
            event = event(
                id = "pe-wife",
                rawSmsId = "sms-wife",
                family = MessageFamily.TRANSFER_IN,
                amount = money("500.00"),
                source = wife,
                destination = user,
                network = BankNetworkType.INTRA_BANK,
            ),
        )
        reconciliation.reconcileStoredEvents()
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_IN, tx.type)
        assertNotEquals(FinancialTransactionType.SELF_TRANSFER, tx.type)
        assertNotEquals(FinancialTransactionType.INCOME, tx.type)
    }

    @Test
    fun singleEventOwnedToOwned_selfTransfer() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3002"))
        persistEvent(
            smsId = "sms-self",
            event = event(
                id = "pe-self",
                rawSmsId = "sms-self",
                family = MessageFamily.TRANSFER_OUT,
                amount = money("50.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3002"),
                network = BankNetworkType.INTRA_BANK,
            ),
        )
        reconciliation.reconcileStoredEvents()
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.SELF_TRANSFER, tx.type)
        assertEquals(FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"), tx.sourceContainerId)
        assertEquals(FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3002"), tx.destinationContainerId)
        assertEquals(listOf("pe-self"), tx.linkedParsedEventIds)
    }

    @Test
    fun duplicateOwnedToOwnedSmsLegs_absorbIntoSingleSelfTransfer() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3003"))
        persistEvent(
            smsId = "sms-out",
            event = event(
                id = "pe-out",
                rawSmsId = "sms-out",
                family = MessageFamily.TRANSFER_OUT,
                amount = money("4445.67"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3003"),
                network = BankNetworkType.INTRA_BANK,
                counterparty = "براء بخش",
            ),
        )
        persistEvent(
            smsId = "sms-in",
            event = event(
                id = "pe-in",
                rawSmsId = "sms-in",
                family = MessageFamily.TRANSFER_IN,
                amount = money("4445.67"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3003"),
                network = BankNetworkType.INTRA_BANK,
            ),
        )
        reconciliation.reconcileStoredEvents()
        assertEquals(1, ftRepo.listAll().size)
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.SELF_TRANSFER, tx.type)
        assertEquals(setOf("sms-in", "sms-out"), ftRepo.listRawSmsIds(tx.id).toSet())
    }

    @Test
    fun outgoingUnknownWithoutCounterpart_postsExternalOut() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        persistEvent(
            smsId = "sms-unk",
            event = event(
                id = "pe-unk",
                rawSmsId = "sms-unk",
                family = MessageFamily.TRANSFER_OUT,
                amount = money("500.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.UNKNOWN, "6810"),
                network = BankNetworkType.INTER_BANK,
                counterparty = "TEST_BENEFICIARY",
            ),
        )
        val summary = reconciliation.reconcileStoredEvents()
        assertEquals(1, ftRepo.listAll().size)
        assertEquals(0, summary.pendingMatch)
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_OUT, tx.type)
        assertNotEquals(FinancialTransactionType.EXPENSE, tx.type)
        assertEquals(FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"), tx.sourceContainerId)
        assertNull(tx.destinationContainerId)
        assertEquals("TEST_BENEFICIARY", tx.counterparty)
    }

    @Test
    fun incomingUnknownWithoutCounterpart_postsExternalIn() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        persistEvent(
            smsId = "sms-in-unk",
            event = event(
                id = "pe-in-unk",
                rawSmsId = "sms-in-unk",
                family = MessageFamily.TRANSFER_IN,
                amount = money("200.00"),
                source = AccountReference(Bank.UNKNOWN, "9999"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                network = BankNetworkType.INTER_BANK,
                counterparty = "TEST_COMPANY",
            ),
        )
        val summary = reconciliation.reconcileStoredEvents()
        assertEquals(0, summary.pendingMatch)
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_IN, tx.type)
        assertNotEquals(FinancialTransactionType.INCOME, tx.type)
        assertNull(tx.sourceContainerId)
        assertEquals(FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"), tx.destinationContainerId)
        assertEquals("TEST_COMPANY", tx.counterparty)
    }

    @Test
    fun incomingMissingSource_ownedDestination_postsExternalIn() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        persistEvent(
            smsId = "sms-in-nosrc",
            event = event(
                id = "pe-in-nosrc",
                rawSmsId = "sms-in-nosrc",
                family = MessageFamily.TRANSFER_IN,
                amount = money("4445.67"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                network = BankNetworkType.INTER_BANK,
            ),
        )
        reconciliation.reconcileStoredEvents()
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_IN, tx.type)
        assertEquals(FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"), tx.destinationContainerId)
    }

    @Test
    fun unmatchedTransfer_unownedLocalSide_staysPending() = runBlocking {
        persistEvent(
            smsId = "sms-unowned",
            event = event(
                id = "pe-unowned",
                rawSmsId = "sms-unowned",
                family = MessageFamily.TRANSFER_OUT,
                amount = money("500.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.UNKNOWN, "6810"),
                network = BankNetworkType.INTER_BANK,
            ),
        )
        val summary = reconciliation.reconcileStoredEvents()
        assertEquals(0, ftRepo.listAll().size)
        assertTrue(summary.pendingMatch >= 1)
    }

    @Test
    fun aljaziraToD360Pair_oneSelfTransfer_doesNotMutateUnknownRegistry() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank("D360"), "6810"))
        val t = LocalDateTime.parse("2026-08-10T12:00:00")
        persistEvent(
            smsId = "sms-out",
            event = event(
                id = "pe-out",
                rawSmsId = "sms-out",
                family = MessageFamily.TRANSFER_OUT,
                amount = money("500.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.UNKNOWN, "6810"),
                network = BankNetworkType.INTER_BANK,
            ),
            details = ParsedEventDetails(occurredAtLocal = t),
            at = Instant.parse("2026-08-10T09:00:00Z"),
        )
        persistEvent(
            smsId = "sms-in",
            event = event(
                id = "pe-in",
                rawSmsId = "sms-in",
                bank = Bank("D360"),
                family = MessageFamily.TRANSFER_IN,
                amount = money("500.00"),
                destination = AccountReference(Bank("D360"), "6810"),
                network = BankNetworkType.INTER_BANK,
            ),
            details = ParsedEventDetails(occurredAtLocal = t.plusMinutes(1)),
            at = Instant.parse("2026-08-10T09:01:00Z"),
        )
        reconciliation.reconcileStoredEvents()
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.SELF_TRANSFER, tx.type)
        assertEquals(2, tx.linkedParsedEventIds.size)
        assertEquals(FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"), tx.sourceContainerId)
        assertEquals(FinancialContainerIdFactory.accountId(Bank("D360"), "6810"), tx.destinationContainerId)
        assertFalse(tx.sourceContainerId!!.contains("UNKNOWN"))
        assertFalse(tx.destinationContainerId!!.contains("UNKNOWN"))
        assertNull(accounts.get(AccountReference(Bank.UNKNOWN, "6810")))
        assertEquals(OwnershipStatus.UNKNOWN, OwnershipResolver(accounts, cards, NoOpLoanRegistryRepository).resolveAccount(AccountReference(Bank.UNKNOWN, "6810")))
        assertEquals(2, accounts.listAll().size)
        assertNull(FinancialContainerIdFactory.accountId(AccountReference(Bank.UNKNOWN, "6810")))
    }

    @Test
    fun matcher_requiresStrongBridge() {
        val out = candidate(
            id = "o",
            raw = "ro",
            family = MessageFamily.TRANSFER_OUT,
            amount = money("10.00"),
            source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
            destination = AccountReference(Bank.UNKNOWN, "9999"),
            sourceOwn = OwnershipStatus.OWNED,
            destOwn = OwnershipStatus.UNKNOWN,
            local = LocalDateTime.parse("2026-08-10T12:00:00"),
        )
        val inn = candidate(
            id = "i",
            raw = "ri",
            family = MessageFamily.TRANSFER_IN,
            amount = money("10.00"),
            destination = AccountReference(Bank("D360"), "6810"),
            sourceOwn = OwnershipStatus.UNKNOWN,
            destOwn = OwnershipStatus.OWNED,
            local = LocalDateTime.parse("2026-08-10T12:01:00"),
            bank = Bank("D360"),
        )
        assertTrue(TransactionMatcher.findMutuallyUniquePairs(listOf(out, inn)).isEmpty())
    }

    @Test
    fun matcher_outsideWindow_noMatch() {
        val out = candidate(
            id = "o",
            raw = "ro",
            family = MessageFamily.TRANSFER_OUT,
            amount = money("10.00"),
            source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
            destination = AccountReference(Bank.UNKNOWN, "6810"),
            sourceOwn = OwnershipStatus.OWNED,
            destOwn = OwnershipStatus.UNKNOWN,
            local = LocalDateTime.parse("2026-08-10T12:00:00"),
        )
        val inn = candidate(
            id = "i",
            raw = "ri",
            family = MessageFamily.TRANSFER_IN,
            amount = money("10.00"),
            destination = AccountReference(Bank("D360"), "6810"),
            sourceOwn = OwnershipStatus.UNKNOWN,
            destOwn = OwnershipStatus.OWNED,
            local = LocalDateTime.parse("2026-08-10T12:30:00"),
            bank = Bank("D360"),
        )
        assertTrue(TransactionMatcher.findMutuallyUniquePairs(listOf(out, inn)).isEmpty())
    }

    @Test
    fun matcher_ambiguousSameAmount_noAutoMatch() {
        val out = candidate(
            id = "o",
            raw = "ro",
            family = MessageFamily.TRANSFER_OUT,
            amount = money("500.00"),
            source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
            destination = AccountReference(Bank.UNKNOWN, "6810"),
            sourceOwn = OwnershipStatus.OWNED,
            destOwn = OwnershipStatus.UNKNOWN,
            local = LocalDateTime.parse("2026-08-10T12:00:00"),
            ref = "SAME",
        )
        val b = candidate(
            id = "b",
            raw = "rb",
            family = MessageFamily.TRANSFER_IN,
            amount = money("500.00"),
            destination = AccountReference(Bank("D360"), "6810"),
            sourceOwn = OwnershipStatus.UNKNOWN,
            destOwn = OwnershipStatus.OWNED,
            local = LocalDateTime.parse("2026-08-10T12:01:00"),
            bank = Bank("D360"),
            ref = "SAME",
        )
        val c = candidate(
            id = "c",
            raw = "rc",
            family = MessageFamily.TRANSFER_IN,
            amount = money("500.00"),
            destination = AccountReference(Bank("D360"), "6810"),
            sourceOwn = OwnershipStatus.UNKNOWN,
            destOwn = OwnershipStatus.OWNED,
            local = LocalDateTime.parse("2026-08-10T12:02:00"),
            bank = Bank("D360"),
            ref = "SAME",
        )
        assertTrue(TransactionMatcher.findMutuallyUniquePairs(listOf(out, b, c)).isEmpty())
    }

    @Test
    fun reconciliation_isIdempotent() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3002"))
        persistEvent(
            smsId = "sms-idemp",
            event = event(
                id = "pe-idemp",
                rawSmsId = "sms-idemp",
                family = MessageFamily.TRANSFER_OUT,
                amount = money("50.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.BANK_ALJAZIRA, "3002"),
            ),
        )
        reconciliation.reconcileStoredEvents()
        val first = ftRepo.listAll()
        reconciliation.reconcileStoredEvents()
        assertEquals(first, ftRepo.listAll())
        assertEquals(1, first.size)
    }

    @Test
    fun eventOrderIndependence_forMatchedPair() = runBlocking {
        val t = LocalDateTime.parse("2026-08-10T12:00:00")

        suspend fun seed(outFirst: Boolean): com.baraa.masroof.domain.model.FinancialTransaction {
            db.clearAllTables()
            confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
            confirmation.confirmAccountOwned(AccountReference(Bank("D360"), "6810"))
            suspend fun out() {
                persistEvent(
                    smsId = "sms-out",
                    event = event(
                        id = "pe-out",
                        rawSmsId = "sms-out",
                        family = MessageFamily.TRANSFER_OUT,
                        amount = money("500.00"),
                        source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                        destination = AccountReference(Bank.UNKNOWN, "6810"),
                    ),
                    details = ParsedEventDetails(occurredAtLocal = t, transactionReference = "REF1"),
                )
            }
            suspend fun inn() {
                persistEvent(
                    smsId = "sms-in",
                    event = event(
                        id = "pe-in",
                        rawSmsId = "sms-in",
                        bank = Bank("D360"),
                        family = MessageFamily.TRANSFER_IN,
                        amount = money("500.00"),
                        destination = AccountReference(Bank("D360"), "6810"),
                    ),
                    details = ParsedEventDetails(
                        occurredAtLocal = t.plusMinutes(1),
                        transactionReference = "REF1",
                    ),
                    at = Instant.parse("2026-08-10T09:01:00Z"),
                )
            }
            if (outFirst) {
                out(); inn()
            } else {
                inn(); out()
            }
            reconciliation.reconcileStoredEvents()
            return ftRepo.listAll().single()
        }

        val a = seed(true)
        val b = seed(false)
        assertEquals(a.id, b.id)
        assertEquals(a.type, b.type)
        assertEquals(a.amount, b.amount)
        assertEquals(a.sourceContainerId, b.sourceContainerId)
        assertEquals(a.destinationContainerId, b.destinationContainerId)
        assertEquals(a.linkedParsedEventIds.sorted(), b.linkedParsedEventIds.sorted())
    }

    @Test
    fun parseReprocessing_keepsTransactionLinkedToCurrentEvent() = runBlocking {
        persistEvent(
            smsId = "sms-re",
            event = event(
                id = "pe-e1",
                rawSmsId = "sms-re",
                family = MessageFamily.PURCHASE,
                amount = money("11.00"),
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
                channel = PurchaseChannel.POS,
            ),
        )
        confirmation.confirmCardOwned(CardReference(Bank.BANK_ALJAZIRA, "7271"))
        reconciliation.reconcileStoredEvents()
        val txId = ftRepo.findByRawSmsId("sms-re")!!.id

        parsedRepo.save(
            event(
                id = "pe-e2",
                rawSmsId = "sms-re",
                family = MessageFamily.PURCHASE,
                amount = money("11.00"),
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
                channel = PurchaseChannel.ONLINE,
                merchant = "Updated",
            ),
        )
        val tx = ftRepo.getById(txId)!!
        assertEquals(listOf("pe-e2"), tx.linkedParsedEventIds)
        assertEquals(1, ftRepo.listAll().size)
    }

    @Test
    fun billPayment_autoAssembles() = runBlocking {
        persistEvent(
            smsId = "sms-bill",
            event = event(
                id = "pe-bill",
                rawSmsId = "sms-bill",
                family = MessageFamily.BILL_PAYMENT,
                amount = money("80.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
            ),
        )
        val summary = reconciliation.reconcileStoredEvents()
        assertEquals(1, ftRepo.listAll().size)
        assertEquals(FinancialTransactionType.BILL_PAYMENT, ftRepo.listAll().single().type)
        assertEquals(0, summary.needsReview)
    }

    @Test
    fun unknownInformationalNotice_isAutoIgnored() = runBlocking {
        val body = """
            اسم المستفيد : براء ف بن
            الاسم المختصر : حسابي D360
            حالة: غير نشط
            حساب: SA2036036036045864332670
            بنك: D360 بنك
            في : 14:04 2026-07-29
        """.trimIndent()
        persistEvent(
            smsId = "sms-info",
            event = event(
                id = "pe-info",
                rawSmsId = "sms-info",
                family = MessageFamily.UNKNOWN,
                amount = null,
            ),
            body = body,
        )
        val summary = reconciliation.reconcileStoredEvents()
        assertEquals(0, ftRepo.listAll().size)
        assertTrue(summary.ignored >= 1)
        assertEquals(0, summary.needsReview)
    }

    @Test
    fun unknownWithMoneyInBodyButNoParsedAmount_stillNeedsReview() = runBlocking {
        persistEvent(
            smsId = "sms-parse-fail",
            event = event(
                id = "pe-parse-fail",
                rawSmsId = "sms-parse-fail",
                family = MessageFamily.UNKNOWN,
                amount = null,
            ),
            body = "عملية غير معروفة بمبلغ: 15000.00 SAR",
        )
        val summary = reconciliation.reconcileStoredEvents()
        assertEquals(0, ftRepo.listAll().size)
        assertTrue(summary.needsReview >= 1)
    }

    @Test
    fun balanceAndNonFinancial_ignored() = runBlocking {
        persistEvent(
            smsId = "sms-bal",
            event = event(
                id = "pe-bal",
                rawSmsId = "sms-bal",
                family = MessageFamily.BALANCE_NOTICE,
                amount = null,
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
            ),
        )
        persistEvent(
            smsId = "sms-nf",
            event = event(
                id = "pe-nf",
                rawSmsId = "sms-nf",
                family = MessageFamily.NON_FINANCIAL,
                amount = null,
            ),
            at = Instant.parse("2026-08-02T00:00:00Z"),
        )
        val summary = reconciliation.reconcileStoredEvents()
        assertEquals(0, ftRepo.listAll().size)
        assertTrue(summary.ignored >= 2)
    }

    @Test
    fun missingAmount_needsReview() = runBlocking {
        persistEvent(
            smsId = "sms-na",
            event = event(
                id = "pe-na",
                rawSmsId = "sms-na",
                family = MessageFamily.PURCHASE,
                amount = null,
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
            ),
        )
        val summary = reconciliation.reconcileStoredEvents()
        assertEquals(0, ftRepo.listAll().size)
        assertTrue(summary.needsReview >= 1)
    }

    @Test
    fun containerIds_areBankScopedAndNamespaced() {
        val a1 = FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001")
        val a2 = FinancialContainerIdFactory.accountId(Bank("D360"), "3001")
        val c1 = FinancialContainerIdFactory.cardId(Bank.BANK_ALJAZIRA, "3001")
        assertNotEquals(a1, a2)
        assertNotEquals(a1, c1)
        assertEquals(a1, FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"))
        assertNull(FinancialContainerIdFactory.accountId(AccountReference(Bank.UNKNOWN, "6810")))
        try {
            FinancialContainerIdFactory.accountId(Bank.UNKNOWN, "6810")
            fail("expected IllegalArgumentException for Bank.UNKNOWN")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun transactionId_isDeterministicFromSortedRawSmsIds() {
        val a = TransactionIdFactory.fromRawSmsIds(listOf("b", "a"))
        val b = TransactionIdFactory.fromRawSmsIds(listOf("a", "b"))
        assertEquals(a, b)
    }

    @Test
    fun bankUnknown_cannotBeConfirmed_still() = runBlocking {
        try {
            confirmation.confirmAccountOwned(AccountReference(Bank.UNKNOWN, "6810"))
            fail("expected")
        } catch (_: IllegalArgumentException) {
        }
        assertFalse(RegistryIdentity.isKnownBank(Bank.UNKNOWN))
    }

    @Test
    fun matchedUnknownBridge_doesNotPersistUnknownContainerId() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank("D360"), "6810"))
        val t = LocalDateTime.parse("2026-08-10T12:00:00")
        persistEvent(
            smsId = "sms-out-u",
            event = event(
                id = "pe-out-u",
                rawSmsId = "sms-out-u",
                family = MessageFamily.TRANSFER_OUT,
                amount = money("500.00"),
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                destination = AccountReference(Bank.UNKNOWN, "6810"),
                network = BankNetworkType.INTER_BANK,
            ),
            details = ParsedEventDetails(occurredAtLocal = t),
            at = Instant.parse("2026-08-10T09:00:00Z"),
        )
        persistEvent(
            smsId = "sms-in-u",
            event = event(
                id = "pe-in-u",
                rawSmsId = "sms-in-u",
                bank = Bank("D360"),
                family = MessageFamily.TRANSFER_IN,
                amount = money("500.00"),
                destination = AccountReference(Bank("D360"), "6810"),
                network = BankNetworkType.INTER_BANK,
            ),
            details = ParsedEventDetails(occurredAtLocal = t.plusMinutes(1)),
            at = Instant.parse("2026-08-10T09:01:00Z"),
        )
        reconciliation.reconcileStoredEvents()
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.SELF_TRANSFER, tx.type)
        assertEquals("account:D360:6810", tx.destinationContainerId)
        assertNotEquals("account:UNKNOWN:6810", tx.destinationContainerId)
        assertNotEquals("account:UNKNOWN:6810", tx.sourceContainerId)
    }

    private suspend fun persistEvent(
        smsId: String,
        event: ParsedEvent,
        details: ParsedEventDetails = ParsedEventDetails(),
        at: Instant = Instant.parse("2026-08-01T12:00:00Z"),
        body: String = "body-$smsId",
    ) {
        rawRepo.insertIfAbsent(
            RawSms(
                id = smsId,
                sender = "AlJazira",
                body = body,
                receivedAt = at,
                deviceMessageId = smsId.removePrefix("sms-"),
                bodyHash = SmsBodyHasher.sha256Hex(body),
            ),
        )
        parsedRepo.save(event, details)
    }

    private fun money(v: String) = Money.of(BigDecimal(v), Currency.SAR)

    private fun event(
        id: String,
        rawSmsId: String,
        family: MessageFamily,
        amount: Money?,
        bank: Bank = Bank.BANK_ALJAZIRA,
        source: AccountReference? = null,
        destination: AccountReference? = null,
        card: CardReference? = null,
        network: BankNetworkType? = null,
        channel: PurchaseChannel? = null,
        merchant: String? = null,
        counterparty: String? = null,
    ) = ParsedEvent(
        id = id,
        rawSmsId = rawSmsId,
        bank = bank,
        messageFamily = family,
        direction = MoneyDirection.OUTGOING,
        amount = amount,
        purchaseChannel = channel,
        sourceAccountRef = source,
        destinationAccountRef = destination,
        cardRef = card,
        merchant = merchant,
        counterparty = counterparty,
        occurredAt = null,
        bankNetworkType = network,
        confidence = Confidence(1.0),
        parseStatus = ParseStatus.SUCCESS,
    )

    private fun candidate(
        id: String,
        raw: String,
        family: MessageFamily,
        amount: Money,
        source: AccountReference? = null,
        destination: AccountReference? = null,
        sourceOwn: OwnershipStatus,
        destOwn: OwnershipStatus,
        local: LocalDateTime?,
        bank: Bank = Bank.BANK_ALJAZIRA,
        ref: String? = null,
    ) = TransferMatchCandidate(
        event = event(
            id = id,
            rawSmsId = raw,
            family = family,
            amount = amount,
            bank = bank,
            source = source,
            destination = destination,
        ),
        transactionReference = ref,
        occurredAtLocal = local,
        receivedAt = Instant.parse("2026-08-10T09:00:00Z"),
        sourceOwnership = sourceOwn,
        destinationOwnership = destOwn,
    )
}
