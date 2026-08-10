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
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
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
    private lateinit var parsedRepo: RoomParsedEventRepository
    private lateinit var rawRepo: RoomRawSmsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accounts = RoomAccountRegistryRepository(db.accountRegistryDao())
        val cards = RoomCardRegistryRepository(db.cardRegistryDao())
        discovery = OwnershipDiscoveryService(accounts, cards)
        parsedRepo = RoomParsedEventRepository(db.parsedEventDao())
        rawRepo = RoomRawSmsRepository(db.rawSmsDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun discoverFromStoredEvents_observesPersistedTransfers() = runBlocking {
        val body = "transfer"
        val sms = RawSms(
            id = "android-sms:hist-1",
            sender = "AlJazira",
            body = body,
            receivedAt = Instant.parse("2026-08-01T10:00:00Z"),
            deviceMessageId = "hist-1",
            bodyHash = SmsBodyHasher.sha256Hex(body),
        )
        rawRepo.insertIfAbsent(sms)

        val wife = AccountReference(Bank.BANK_ALJAZIRA, "wife1")
        val user = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        parsedRepo.save(
            ParsedEvent(
                id = "pe-hist-1",
                rawSmsId = sms.id,
                bank = Bank.BANK_ALJAZIRA,
                messageFamily = MessageFamily.TRANSFER_IN,
                direction = MoneyDirection.INCOMING,
                amount = Money(BigDecimal("100"), Currency.SAR),
                purchaseChannel = null,
                sourceAccountRef = wife,
                destinationAccountRef = user,
                cardRef = null,
                merchant = null,
                counterparty = null,
                occurredAt = null,
                bankNetworkType = BankNetworkType.INTRA_BANK,
                confidence = Confidence(1.0),
                parseStatus = ParseStatus.SUCCESS,
            ),
        )

        var count = 0
        for (record in parsedRepo.listAll()) {
            discovery.observe(record.event)
            count++
        }
        assertEquals(1, count)
        assertNull(accounts.get(wife))
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(user))

        // Re-run must stay idempotent for same rawSmsId
        for (record in parsedRepo.listAll()) {
            discovery.observe(record.event)
        }
        assertEquals(1, accounts.get(user)!!.evidenceCount)
    }
}
