package com.baraa.masroof.domain.ownership

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OwnershipDiscoveryServiceTest {

    private lateinit var db: MasroofDatabase
    private lateinit var accounts: RoomAccountRegistryRepository
    private lateinit var cards: RoomCardRegistryRepository
    private lateinit var discovery: OwnershipDiscoveryService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accounts = RoomAccountRegistryRepository(db.accountRegistryDao())
        cards = RoomCardRegistryRepository(db.cardRegistryDao())
        discovery = OwnershipDiscoveryService(accounts, cards)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun transferOut_discoversSourceOnly() = runBlocking {
        val source = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        val dest = AccountReference(Bank.UNKNOWN, "0593")
        discovery.observe(
            event(
                family = MessageFamily.TRANSFER_OUT,
                rawSmsId = "out-1",
                source = source,
                destination = dest,
                network = BankNetworkType.INTER_BANK,
            ),
        )
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(source))
        assertNull(accounts.get(dest))
        assertEquals(1, accounts.listAll().size)
    }

    @Test
    fun transferIn_discoversDestinationOnly() = runBlocking {
        val source = AccountReference(Bank.BANK_ALJAZIRA, "8888")
        val dest = AccountReference(Bank.BANK_ALJAZIRA, "3003")
        discovery.observe(
            event(
                family = MessageFamily.TRANSFER_IN,
                rawSmsId = "in-1",
                source = source,
                destination = dest,
                network = BankNetworkType.INTRA_BANK,
            ),
        )
        assertNull(accounts.get(source))
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(dest))
    }

    @Test
    fun intraBankIncomingWifeScenario_onlyDestinationCandidate() = runBlocking {
        val wife = AccountReference(Bank.BANK_ALJAZIRA, "wife1")
        val user = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        discovery.observe(
            event(
                family = MessageFamily.TRANSFER_IN,
                rawSmsId = "wife-1",
                source = wife,
                destination = user,
                network = BankNetworkType.INTRA_BANK,
            ),
        )
        assertNull(accounts.get(wife))
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(user))
        assertEquals(1, accounts.listAll().size)
    }

    @Test
    fun purchase_discoversCardAndLocalAccount() = runBlocking {
        val account = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        val card = CardReference(Bank.BANK_ALJAZIRA, "7271")
        discovery.observe(
            event(
                family = MessageFamily.PURCHASE,
                rawSmsId = "buy-1",
                source = account,
                card = card,
                channel = PurchaseChannel.ONLINE,
            ),
        )
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(account))
        assertEquals(OwnershipStatus.UNKNOWN, cards.resolve(card))
    }

    @Test
    fun billPayment_discoversDebitedAccount() = runBlocking {
        val account = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        discovery.observe(
            event(
                family = MessageFamily.BILL_PAYMENT,
                rawSmsId = "bill-1",
                source = account,
            ),
        )
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(account))
    }

    @Test
    fun cardPayment_discoversSourceAndCard() = runBlocking {
        val account = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        val card = CardReference(Bank.BANK_ALJAZIRA, "7271")
        val other = AccountReference(Bank.BANK_ALJAZIRA, "9999")
        discovery.observe(
            event(
                family = MessageFamily.CARD_PAYMENT,
                rawSmsId = "cp-1",
                source = account,
                destination = other,
                card = card,
            ),
        )
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(account))
        assertEquals(OwnershipStatus.UNKNOWN, cards.resolve(card))
        assertNull(accounts.get(other))
    }

    @Test
    fun withdrawal_discoversSourceOnly() = runBlocking {
        val source = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        val dest = AccountReference(Bank.BANK_ALJAZIRA, "atm")
        discovery.observe(
            event(
                family = MessageFamily.WITHDRAWAL,
                rawSmsId = "wd-1",
                source = source,
                destination = dest,
            ),
        )
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(source))
        assertNull(accounts.get(dest))
        assertEquals(0, cards.listAll().size)
    }

    @Test
    fun withdrawal_discoversMadaCardWhenPresent() = runBlocking {
        val source = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        val card = CardReference(Bank.BANK_ALJAZIRA, "8219")
        discovery.observe(
            event(
                family = MessageFamily.WITHDRAWAL,
                rawSmsId = "wd-atm",
                source = source,
                card = card,
            ),
        )
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(source))
        assertEquals(OwnershipStatus.UNKNOWN, cards.resolve(card))
    }

    @Test
    fun balanceNotice_prefersSourceThenDestination() = runBlocking {
        val source = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        discovery.observe(
            event(
                family = MessageFamily.BALANCE_NOTICE,
                rawSmsId = "bal-1",
                source = source,
                destination = AccountReference(Bank.BANK_ALJAZIRA, "ignored"),
            ),
        )
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(source))
        assertNull(accounts.get(AccountReference(Bank.BANK_ALJAZIRA, "ignored")))

        val destOnly = AccountReference(Bank.BANK_ALJAZIRA, "3002")
        discovery.observe(
            event(
                family = MessageFamily.BALANCE_NOTICE,
                rawSmsId = "bal-2",
                destination = destOnly,
            ),
        )
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(destOnly))
    }

    @Test
    fun refund_discoversDestinationAndCard_notSourceFallback() = runBlocking {
        val source = AccountReference(Bank.BANK_ALJAZIRA, "ext-src")
        val dest = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        val card = CardReference(Bank.BANK_ALJAZIRA, "7271")
        discovery.observe(
            event(
                family = MessageFamily.REFUND,
                rawSmsId = "rf-1",
                source = source,
                destination = dest,
                card = card,
            ),
        )
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(dest))
        assertEquals(OwnershipStatus.UNKNOWN, cards.resolve(card))
        assertNull(accounts.get(source))

        // No destination → still must NOT discover source
        discovery.observe(
            event(
                family = MessageFamily.REFUND,
                rawSmsId = "rf-2",
                source = source,
                card = card,
            ),
        )
        assertNull(accounts.get(source))
        assertEquals(OwnershipStatus.UNKNOWN, cards.resolve(card))
    }

    @Test
    fun fee_discoversSourceAndCard() = runBlocking {
        val source = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        val card = CardReference(Bank.BANK_ALJAZIRA, "7271")
        discovery.observe(
            event(
                family = MessageFamily.FEE,
                rawSmsId = "fee-1",
                source = source,
                card = card,
            ),
        )
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(source))
        assertEquals(OwnershipStatus.UNKNOWN, cards.resolve(card))
    }

    @Test
    fun otpAndNonFinancial_noCandidates() = runBlocking {
        discovery.observe(
            event(
                family = MessageFamily.OTP,
                rawSmsId = "otp-1",
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
            ),
        )
        discovery.observe(
            event(
                family = MessageFamily.NON_FINANCIAL,
                rawSmsId = "nf-1",
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                card = CardReference(Bank.BANK_ALJAZIRA, "7271"),
            ),
        )
        assertEquals(0, accounts.listAll().size)
        assertEquals(0, cards.listAll().size)
    }

    @Test
    fun unknownFamily_noCandidates() = runBlocking {
        discovery.observe(
            event(
                family = MessageFamily.UNKNOWN,
                rawSmsId = "unk-1",
                source = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
            ),
        )
        assertEquals(0, accounts.listAll().size)
    }

    @Test
    fun bankUnknownReference_notPromoted() = runBlocking {
        discovery.observe(
            event(
                family = MessageFamily.TRANSFER_OUT,
                rawSmsId = "unk-bank",
                source = AccountReference(Bank.BANK_ALJAZIRA, "3002"),
                destination = AccountReference(Bank.UNKNOWN, "0593"),
                network = BankNetworkType.INTER_BANK,
            ),
        )
        assertEquals(1, accounts.listAll().size)
        assertNull(accounts.get(AccountReference(Bank.UNKNOWN, "0593")))
        assertNull(accounts.get(AccountReference(Bank("D360"), "0593")))
    }

    private fun event(
        family: MessageFamily,
        rawSmsId: String,
        source: AccountReference? = null,
        destination: AccountReference? = null,
        card: CardReference? = null,
        network: BankNetworkType? = null,
        channel: PurchaseChannel? = null,
    ): ParsedEvent = ParsedEvent(
        id = "pe-$rawSmsId",
        rawSmsId = rawSmsId,
        bank = Bank.BANK_ALJAZIRA,
        messageFamily = family,
        direction = MoneyDirection.OUTGOING,
        amount = Money(BigDecimal("10.00"), Currency.SAR),
        purchaseChannel = channel,
        sourceAccountRef = source,
        destinationAccountRef = destination,
        cardRef = card,
        merchant = null,
        counterparty = null,
        occurredAt = null,
        bankNetworkType = network,
        confidence = Confidence(1.0, emptyList()),
        parseStatus = ParseStatus.SUCCESS,
    )
}
