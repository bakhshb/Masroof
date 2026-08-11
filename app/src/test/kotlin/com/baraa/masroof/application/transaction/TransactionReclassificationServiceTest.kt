package com.baraa.masroof.application.transaction

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.application.review.EffectiveParsedEventProvider
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.data.repository.RoomFinancialTransactionRepository
import com.baraa.masroof.data.repository.RoomParsedEventRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.repository.RoomUserCorrectionRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.ids.TransactionIdFactory
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.ownership.OwnershipResolver
import com.baraa.masroof.domain.repository.FinancialTransactionSaveResult
import com.baraa.masroof.sms.hash.SmsBodyHasher
import com.baraa.masroof.parsing.model.ParsedEventDetails
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TransactionReclassificationServiceTest {
    private lateinit var db: MasroofDatabase
    private lateinit var ftRepo: RoomFinancialTransactionRepository
    private lateinit var parsedRepo: RoomParsedEventRepository
    private lateinit var rawRepo: RoomRawSmsRepository
    private lateinit var confirmation: OwnershipConfirmationService
    private lateinit var service: TransactionReclassificationService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ftRepo = RoomFinancialTransactionRepository(db.financialTransactionDao(), db.parsedEventDao())
        parsedRepo = RoomParsedEventRepository(db.parsedEventDao())
        rawRepo = RoomRawSmsRepository(db.rawSmsDao())
        val accounts = RoomAccountRegistryRepository(db.accountRegistryDao())
        val cards = RoomCardRegistryRepository(db.cardRegistryDao())
        confirmation = OwnershipConfirmationService(accounts, cards)
        service = TransactionReclassificationService(
            financialTransactionRepository = ftRepo,
            effectiveParsedEventProvider = EffectiveParsedEventProvider(
                parsedRepo,
                RoomUserCorrectionRepository(db.userCorrectionDao()),
            ),
            ownershipResolver = OwnershipResolver(accounts, cards),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun incomeToExternalTransferIn_updatesType() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        persistIncomeTransfer(smsId = "sms-in", counterparty = "DERAYAH", amount = "28093.33")
        val txId = TransactionIdFactory.fromRawSmsIds(listOf("sms-in"))
        val result = service.reclassify(txId, FinancialTransactionType.EXTERNAL_TRANSFER_IN)
        assertTrue(result is ReclassificationResult.Success)
        val updated = ftRepo.getById(txId)!!
        assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_IN, updated.type)
        assertEquals(
            FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"),
            updated.destinationContainerId,
        )
    }

    @Test
    fun pairedTransaction_isRejected() = runBlocking {
        listOf("sms-a", "sms-b").forEach { smsId ->
            rawRepo.insertIfAbsent(
                RawSms(
                    id = smsId,
                    sender = "AlJazira",
                    body = "body-$smsId",
                    bodyHash = SmsBodyHasher.sha256Hex("body-$smsId"),
                    receivedAt = Instant.parse("2026-08-01T10:00:00Z"),
                    deviceMessageId = smsId,
                ),
            )
        }
        val tx = FinancialTransaction(
            id = "tx-pair",
            type = FinancialTransactionType.SELF_TRANSFER,
            amount = Money.of("100.00", Currency.SAR),
            occurredAt = Instant.parse("2026-08-01T10:00:00Z"),
            sourceContainerId = null,
            destinationContainerId = null,
            merchant = null,
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = emptyList(),
        )
        ftRepo.save(tx, listOf("sms-a", "sms-b"))
        val result = service.reclassify("tx-pair", FinancialTransactionType.INCOME)
        assertTrue(result is ReclassificationResult.Rejected)
        assertEquals("paired_transaction_not_supported", (result as ReclassificationResult.Rejected).reason)
    }

    private suspend fun persistIncomeTransfer(smsId: String, counterparty: String, amount: String) {
        val body = "transfer-$smsId"
        rawRepo.insertIfAbsent(
            RawSms(
                id = smsId,
                sender = "Jazira Bank",
                body = body,
                bodyHash = SmsBodyHasher.sha256Hex(body),
                receivedAt = Instant.parse("2026-08-02T10:00:00Z"),
                deviceMessageId = smsId,
            ),
        )
        val event = ParsedEvent(
            id = "pe-$smsId",
            rawSmsId = smsId,
            bank = Bank.BANK_ALJAZIRA,
            messageFamily = MessageFamily.TRANSFER_IN,
            direction = MoneyDirection.INCOMING,
            parseStatus = ParseStatus.SUCCESS,
            amount = Money.of(amount, Currency.SAR),
            occurredAt = Instant.parse("2026-08-02T10:00:00Z"),
            sourceAccountRef = AccountReference(Bank.UNKNOWN, "9999"),
            destinationAccountRef = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
            cardRef = null,
            purchaseChannel = null,
            merchant = null,
            counterparty = counterparty,
            bankNetworkType = null,
            confidence = Confidence(1.0),
        )
        parsedRepo.save(event, ParsedEventDetails())
        val tx = FinancialTransaction(
            id = TransactionIdFactory.fromRawSmsIds(listOf(smsId)),
            type = FinancialTransactionType.INCOME,
            amount = Money.of(amount, Currency.SAR),
            occurredAt = event.occurredAt!!,
            sourceContainerId = null,
            destinationContainerId = FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001"),
            merchant = null,
            counterparty = counterparty,
            categoryId = null,
            linkedParsedEventIds = listOf(event.id),
        )
        assertEquals(FinancialTransactionSaveResult.Saved, ftRepo.save(tx, listOf(smsId)))
    }
}
