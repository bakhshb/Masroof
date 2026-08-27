package com.baraa.masroof.application.transaction

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.bank.aljazira.AlJaziraParsingPipeline
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.data.repository.RoomFinancialTransactionRepository
import com.baraa.masroof.data.repository.RoomParsedEventRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.matching.TransactionMatcher
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.ownership.OwnershipResolver
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.model.SmsParseInput
import com.baraa.masroof.sms.hash.SmsBodyHasher
import com.baraa.masroof.domain.model.RawSms
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IntraBankSelfTransferReconciliationTest {

    private val outgoingBody =
        """
        حوالة صادرة الى حسابك الجاري
        من: 3001
        مبلغ: SAR 5,500.00
        إلى: 3002
        في: 2026-08-27 07:36
        """.trimIndent()

    private val incomingBody =
        """
        حوالة واردة داخلية
        مبلغ: SAR 5,500.00
        إلى: 3002
        اسم المرسل: براء بخش
        رقم حساب المرسل: 3001
        البنك المرسل: بنك الجزيرة
        في: 2026-08-27 07:36
        """.trimIndent()

    private lateinit var db: MasroofDatabase
    private lateinit var rawRepo: RoomRawSmsRepository
    private lateinit var parsedRepo: RoomParsedEventRepository
    private lateinit var ftRepo: RoomFinancialTransactionRepository
    private lateinit var accounts: RoomAccountRegistryRepository
    private lateinit var cards: RoomCardRegistryRepository
    private lateinit var confirmation: OwnershipConfirmationService
    private lateinit var reconciliation: TransactionReconciliationService
    private val pipeline = AlJaziraParsingPipeline()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        rawRepo = RoomRawSmsRepository(db.rawSmsDao())
        parsedRepo = RoomParsedEventRepository(db.parsedEventDao())
        ftRepo = RoomFinancialTransactionRepository(db.financialTransactionDao(), db.parsedEventDao())
        accounts = RoomAccountRegistryRepository(db.accountRegistryDao())
        cards = RoomCardRegistryRepository(db.cardRegistryDao())
        confirmation = OwnershipConfirmationService(accounts, cards)
        reconciliation = TransactionReconciliationService(
            parsedEventRepository = parsedRepo,
            rawSmsRepository = rawRepo,
            financialTransactionRepository = ftRepo,
            ownershipResolver = OwnershipResolver(accounts, cards),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun parser_exactBugSms_extractsIntraBankEndpoints() {
        val out = parseSms("sms-out", outgoingBody) as ParseResult.Success
        assertEquals(MessageFamily.TRANSFER_OUT, out.event.messageFamily)
        assertEquals(BankNetworkType.INTRA_BANK, out.event.bankNetworkType)
        assertEquals(Money.of("5500.00", Currency.SAR), out.event.amount)
        assertEquals("3001", out.event.sourceAccountRef?.maskedNumber)
        assertEquals("3002", out.event.destinationAccountRef?.maskedNumber)

        val inn = parseSms("sms-in", incomingBody) as ParseResult.Success
        assertEquals(MessageFamily.TRANSFER_IN, inn.event.messageFamily)
        assertEquals(BankNetworkType.INTRA_BANK, inn.event.bankNetworkType)
        assertEquals(Money.of("5500.00", Currency.SAR), inn.event.amount)
        assertEquals("3001", inn.event.sourceAccountRef?.maskedNumber)
        assertEquals("3002", inn.event.destinationAccountRef?.maskedNumber)
        assertEquals("براء بخش", inn.event.counterparty)
    }

    @Test
    fun matcher_intraBankLegs_pairByAccountBridge() {
        val out = parseSms("sms-out", outgoingBody) as ParseResult.Success
        val inn = parseSms("sms-in", incomingBody) as ParseResult.Success
        val t = LocalDateTime.parse("2026-08-27T07:36:00")
        val outCandidate = transferCandidate(out, t)
        val inCandidate = transferCandidate(inn, t)

        assertTrue(TransactionMatcher.hasIntraBankAccountBridge(outCandidate, inCandidate))
        val pairs = TransactionMatcher.findMutuallyUniquePairs(listOf(outCandidate, inCandidate))
        assertEquals(1, pairs.size)
    }

    @Test
    fun bothOwned_exactBugSms_singleSelfTransfer() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3002"))
        persistParsed("sms-out", outgoingBody)
        persistParsed("sms-in", incomingBody)

        reconciliation.reconcileStoredEvents()

        assertEquals(1, ftRepo.listAll().size)
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.SELF_TRANSFER, tx.type)
        assertEquals(
            FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"),
            tx.sourceContainerId,
        )
        assertEquals(
            FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3002"),
            tx.destinationContainerId,
        )
        assertEquals(setOf("sms-in", "sms-out"), ftRepo.listRawSmsIds(tx.id).toSet())
    }

    @Test
    fun bothOwned_registryLongMasked_singleSelfTransfer() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "1234567890123001"))
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "1234567890123002"))
        persistParsed("sms-out", outgoingBody)
        persistParsed("sms-in", incomingBody)

        reconciliation.reconcileStoredEvents()

        assertEquals(1, ftRepo.listAll().size)
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.SELF_TRANSFER, tx.type)
        assertEquals(setOf("sms-in", "sms-out"), ftRepo.listRawSmsIds(tx.id).toSet())
    }

    @Test
    fun confirmSourceFirst_thenDestination_upgradesToSelfTransfer() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        persistParsed("sms-out", outgoingBody)
        persistParsed("sms-in", incomingBody)

        reconciliation.reconcileStoredEvents()
        assertTrue(ftRepo.listAll().isEmpty())

        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3002"))
        reconciliation.reconcileStoredEvents()

        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.SELF_TRANSFER, tx.type)
        assertEquals(setOf("sms-in", "sms-out"), ftRepo.listRawSmsIds(tx.id).toSet())
    }

    @Test
    fun confirmDestinationFirst_thenSource_pairsWhenBothOwned() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3002"))
        persistParsed("sms-out", outgoingBody)
        persistParsed("sms-in", incomingBody)

        reconciliation.reconcileStoredEvents()
        assertTrue(
            "Intra-bank counterpart should defer single-leg external posting",
            ftRepo.listAll().isEmpty(),
        )

        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        reconciliation.reconcileStoredEvents()

        assertEquals(1, ftRepo.listAll().size)
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.SELF_TRANSFER, tx.type)
        assertEquals(setOf("sms-in", "sms-out"), ftRepo.listRawSmsIds(tx.id).toSet())
    }

    @Test
    fun upgrade_staleExternalPair_becomesSelfTransfer() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3002"))
        persistParsed("sms-out", outgoingBody)
        persistParsed("sms-in", incomingBody)
        seedStaleExternalPair("sms-out", "sms-in")

        reconciliation.reconcileStoredEvents()

        assertEquals(1, ftRepo.listAll().size)
        val tx = ftRepo.listAll().single()
        assertEquals(FinancialTransactionType.SELF_TRANSFER, tx.type)
        assertEquals(setOf("sms-in", "sms-out"), ftRepo.listRawSmsIds(tx.id).toSet())
    }

    @Test
    fun upgrade_staleExternalPair_outsideTimeWindow_staysExternal() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3002"))

        val outBodyLate =
            """
            حوالة صادرة الى حسابك الجاري
            من: 3001
            مبلغ: SAR 5,500.00
            إلى: 3002
            في: 2026-08-27 08:00
            """.trimIndent()
        val inBodyEarly =
            """
            حوالة واردة داخلية
            مبلغ: SAR 5,500.00
            إلى: 3002
            اسم المرسل: براء بخش
            رقم حساب المرسل: 3001
            البنك المرسل: بنك الجزيرة
            في: 2026-08-27 07:36
            """.trimIndent()

        persistParsed("sms-out", outBodyLate)
        persistParsed("sms-in", inBodyEarly)
        seedStaleExternalPair("sms-out", "sms-in")

        reconciliation.reconcileStoredEvents()

        assertEquals(2, ftRepo.listAll().size)
        assertTrue(
            ftRepo.listAll().all {
                it.type == FinancialTransactionType.EXTERNAL_TRANSFER_OUT ||
                    it.type == FinancialTransactionType.EXTERNAL_TRANSFER_IN
            },
        )
    }

    @Test
    fun upgrade_ambiguousSameTimestamp_doesNotMergeWrongLegs() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3002"))

        persistParsed("sms-out-a", outgoingBody, Instant.parse("2026-08-27T04:36:00Z"))
        persistParsed("sms-out-b", outgoingBody, Instant.parse("2026-08-27T04:37:00Z"))
        persistParsed("sms-in-a", incomingBody, Instant.parse("2026-08-27T04:36:30Z"))
        persistParsed("sms-in-b", incomingBody, Instant.parse("2026-08-27T04:37:30Z"))
        seedStaleExternalPair("sms-out-a", "sms-in-a", idSuffix = "a")
        seedStaleExternalPair("sms-out-b", "sms-in-b", idSuffix = "b")

        reconciliation.reconcileStoredEvents()

        assertEquals(4, ftRepo.listAll().size)
        assertTrue(
            ftRepo.listAll().all {
                it.type == FinancialTransactionType.EXTERNAL_TRANSFER_OUT ||
                    it.type == FinancialTransactionType.EXTERNAL_TRANSFER_IN
            },
        )
    }

    private suspend fun seedStaleExternalPair(
        outSmsId: String,
        inSmsId: String,
        idSuffix: String = "",
    ) {
        val outParsed = parsedRepo.listAll().first { it.event.rawSmsId == outSmsId }
        val inParsed = parsedRepo.listAll().first { it.event.rawSmsId == inSmsId }
        val amount = Money.of("5500.00", Currency.SAR)
        val sourceId = FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001")!!
        val destId = FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3002")!!

        ftRepo.save(
            com.baraa.masroof.domain.model.FinancialTransaction(
                id = "tx-stale-out$idSuffix",
                type = FinancialTransactionType.EXTERNAL_TRANSFER_OUT,
                amount = amount,
                occurredAt = Instant.parse("2026-08-27T04:36:00Z"),
                sourceContainerId = sourceId,
                destinationContainerId = destId,
                merchant = null,
                counterparty = "براء بخش",
                categoryId = null,
                linkedParsedEventIds = listOf(outParsed.event.id),
            ),
            listOf(outSmsId),
        )
        ftRepo.save(
            com.baraa.masroof.domain.model.FinancialTransaction(
                id = "tx-stale-in$idSuffix",
                type = FinancialTransactionType.EXTERNAL_TRANSFER_IN,
                amount = amount,
                occurredAt = Instant.parse("2026-08-27T04:36:00Z"),
                sourceContainerId = sourceId,
                destinationContainerId = destId,
                merchant = null,
                counterparty = "براء بخش",
                categoryId = null,
                linkedParsedEventIds = listOf(inParsed.event.id),
            ),
            listOf(inSmsId),
        )
    }

    private fun parseSms(
        rawSmsId: String,
        body: String,
        receivedAt: Instant = Instant.parse("2026-08-27T04:36:00Z"),
    ): ParseResult =
        pipeline.parse(
            SmsParseInput(
                rawSmsId = rawSmsId,
                sender = "AlJazira",
                body = body,
                receivedAt = receivedAt,
            ),
        )

    private suspend fun persistParsed(
        rawSmsId: String,
        body: String,
        receivedAt: Instant = Instant.parse("2026-08-27T04:36:00Z"),
    ) {
        val result = parseSms(rawSmsId, body, receivedAt)
        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertEquals(ParseStatus.SUCCESS, success.event.parseStatus)
        rawRepo.insertIfAbsent(
            RawSms(
                id = rawSmsId,
                sender = "AlJazira",
                body = body,
                receivedAt = receivedAt,
                deviceMessageId = rawSmsId,
                bodyHash = SmsBodyHasher.sha256Hex(body),
            ),
        )
        parsedRepo.save(success.event, success.details)
    }

    private fun transferCandidate(
        result: ParseResult.Success,
        occurredAtLocal: LocalDateTime,
    ) = com.baraa.masroof.domain.matching.TransferMatchCandidate(
        event = result.event,
        transactionReference = result.details.transactionReference,
        occurredAtLocal = occurredAtLocal,
        receivedAt = Instant.parse("2026-08-27T04:36:00Z"),
        sourceOwnership = com.baraa.masroof.domain.model.OwnershipStatus.OWNED,
        destinationOwnership = com.baraa.masroof.domain.model.OwnershipStatus.OWNED,
    )
}
