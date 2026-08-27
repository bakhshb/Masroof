package com.baraa.masroof.domain.ownership

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.data.repository.RoomParsedEventRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.PurchaseChannel
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.sms.hash.SmsBodyHasher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DiscoverFromStoredEventsTest {

    private lateinit var db: MasroofDatabase
    private lateinit var discovery: OwnershipDiscoveryService
    private lateinit var accounts: RoomAccountRegistryRepository
    private lateinit var cards: RoomCardRegistryRepository
    private lateinit var parsedRepo: RoomParsedEventRepository
    private lateinit var rawRepo: RoomRawSmsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accounts = RoomAccountRegistryRepository.from(db)
        cards = RoomCardRegistryRepository.from(db)
        discovery = OwnershipDiscoveryService(accounts, cards)
        parsedRepo = RoomParsedEventRepository(db.parsedEventDao())
        rawRepo = RoomRawSmsRepository(db.rawSmsDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun discoverFromStoredEvents_abRerun_isGloballyIdempotent() = runBlocking {
        persistTransferIn(
            smsId = "android-sms:A",
            deviceId = "A",
            eventId = "pe-A",
            wife = "wife1",
            user = "3001",
            at = Instant.parse("2026-08-01T10:00:00Z"),
        )
        persistPurchase(
            smsId = "android-sms:B",
            deviceId = "B",
            eventId = "pe-B",
            account = "3001",
            card = "7271",
            at = Instant.parse("2026-08-01T11:00:00Z"),
        )

        suspend fun runBacklog() {
            for (record in parsedRepo.listAll()) {
                discovery.observe(record.event)
            }
        }

        runBacklog()
        val accountAfterFirst = accounts.get(AccountReference(Bank.BANK_ALJAZIRA, "3001"))!!
        val cardAfterFirst = cards.get(CardReference(Bank.BANK_ALJAZIRA, "7271"))!!
        assertNull(accounts.get(AccountReference(Bank.BANK_ALJAZIRA, "wife1")))

        runBacklog()
        assertEquals(accountAfterFirst, accounts.get(AccountReference(Bank.BANK_ALJAZIRA, "3001")))
        assertEquals(cardAfterFirst, cards.get(CardReference(Bank.BANK_ALJAZIRA, "7271")))
        assertEquals(1, accounts.listAll().size)
        assertEquals(1, cards.listAll().size)
        assertEquals(OwnershipStatus.UNKNOWN, accountAfterFirst.ownership)
    }

    private suspend fun persistTransferIn(
        smsId: String,
        deviceId: String,
        eventId: String,
        wife: String,
        user: String,
        at: Instant,
    ) {
        val body = "transfer-$deviceId"
        rawRepo.insertIfAbsent(
            RawSms(
                id = smsId,
                sender = "AlJazira",
                body = body,
                receivedAt = at,
                deviceMessageId = deviceId,
                bodyHash = SmsBodyHasher.sha256Hex(body),
            ),
        )
        parsedRepo.save(
            ParsedEvent(
                id = eventId,
                rawSmsId = smsId,
                bank = Bank.BANK_ALJAZIRA,
                messageFamily = MessageFamily.TRANSFER_IN,
                direction = MoneyDirection.INCOMING,
                amount = Money(BigDecimal("100"), Currency.SAR),
                purchaseChannel = null,
                sourceAccountRef = AccountReference(Bank.BANK_ALJAZIRA, wife),
                destinationAccountRef = AccountReference(Bank.BANK_ALJAZIRA, user),
                cardRef = null,
                merchant = null,
                counterparty = null,
                occurredAt = null,
                bankNetworkType = BankNetworkType.INTRA_BANK,
                confidence = Confidence(1.0),
                parseStatus = ParseStatus.SUCCESS,
            ),
        )
    }

    private suspend fun persistPurchase(
        smsId: String,
        deviceId: String,
        eventId: String,
        account: String,
        card: String,
        at: Instant,
    ) {
        val body = "purchase-$deviceId"
        rawRepo.insertIfAbsent(
            RawSms(
                id = smsId,
                sender = "AlJazira",
                body = body,
                receivedAt = at,
                deviceMessageId = deviceId,
                bodyHash = SmsBodyHasher.sha256Hex(body),
            ),
        )
        parsedRepo.save(
            ParsedEvent(
                id = eventId,
                rawSmsId = smsId,
                bank = Bank.BANK_ALJAZIRA,
                messageFamily = MessageFamily.PURCHASE,
                direction = MoneyDirection.OUTGOING,
                amount = Money(BigDecimal("51.99"), Currency.SAR),
                purchaseChannel = PurchaseChannel.ONLINE,
                sourceAccountRef = AccountReference(Bank.BANK_ALJAZIRA, account),
                destinationAccountRef = null,
                cardRef = CardReference(Bank.BANK_ALJAZIRA, card),
                merchant = "Keeta",
                counterparty = null,
                occurredAt = null,
                bankNetworkType = null,
                confidence = Confidence(1.0),
                parseStatus = ParseStatus.SUCCESS,
            ),
        )
    }
}
