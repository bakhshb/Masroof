package com.baraa.masroof.domain.ownership

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.TransferOwnershipType
import com.baraa.masroof.domain.rules.TransferOwnershipResolver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OwnershipResolverAndScenariosTest {

    private lateinit var db: MasroofDatabase
    private lateinit var accounts: RoomAccountRegistryRepository
    private lateinit var cards: RoomCardRegistryRepository
    private lateinit var resolver: OwnershipResolver
    private lateinit var confirmation: OwnershipConfirmationService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accounts = RoomAccountRegistryRepository(db.accountRegistryDao())
        cards = RoomCardRegistryRepository(db.cardRegistryDao())
        resolver = OwnershipResolver(accounts, cards)
        confirmation = OwnershipConfirmationService(accounts, cards)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun exactOwnedReference_resolvesOwned() = runBlocking {
        val ref = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        confirmation.confirmAccountOwned(ref)
        assertEquals(OwnershipStatus.OWNED, resolver.resolveAccount(ref))
    }

    @Test
    fun exactExternalReference_resolvesExternal() = runBlocking {
        val ref = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        confirmation.markAccountExternal(ref)
        assertEquals(OwnershipStatus.EXTERNAL, resolver.resolveAccount(ref))
    }

    @Test
    fun missingReference_resolvesUnknown() = runBlocking {
        assertEquals(
            OwnershipStatus.UNKNOWN,
            resolver.resolveAccount(AccountReference(Bank.BANK_ALJAZIRA, "9999")),
        )
    }

    @Test
    fun ownedAljazira_doesNotResolveUnknownBankSameDigits() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        assertEquals(
            OwnershipStatus.UNKNOWN,
            resolver.resolveAccount(AccountReference(Bank.UNKNOWN, "3001")),
        )
    }

    @Test
    fun ownedAljazira_doesNotResolveD360SameDigits() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        assertEquals(
            OwnershipStatus.UNKNOWN,
            resolver.resolveAccount(AccountReference(Bank("D360"), "3001")),
        )
    }

    @Test
    fun scenarioA_ownAljaziraAccounts_ownedToOwned() = runBlocking {
        val a = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        val b = AccountReference(Bank.BANK_ALJAZIRA, "3002")
        confirmation.confirmAccountOwned(a)
        confirmation.confirmAccountOwned(b)
        val source = resolver.resolveAccount(a)
        val dest = resolver.resolveAccount(b)
        assertEquals(OwnershipStatus.OWNED, source)
        assertEquals(OwnershipStatus.OWNED, dest)
        assertEquals(
            TransferOwnershipType.SELF_TRANSFER,
            TransferOwnershipResolver.resolve(source, dest),
        )
        // INTRA_BANK must not be what drove the classification
        assertNotEquals(BankNetworkType.INTRA_BANK, TransferOwnershipType.SELF_TRANSFER)
    }

    @Test
    fun scenarioB_wifeIntraBank_externalToOwned_notSelfTransfer() = runBlocking {
        val wife = AccountReference(Bank.BANK_ALJAZIRA, "wife1")
        val user = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        confirmation.markAccountExternal(wife)
        confirmation.confirmAccountOwned(user)
        val source = resolver.resolveAccount(wife)
        val dest = resolver.resolveAccount(user)
        assertEquals(OwnershipStatus.EXTERNAL, source)
        assertEquals(OwnershipStatus.OWNED, dest)
        assertEquals(
            TransferOwnershipType.EXTERNAL_INCOMING,
            TransferOwnershipResolver.resolve(source, dest),
        )
        // Same bank / INTRA_BANK must not force SELF_TRANSFER
        assertNotEquals(
            TransferOwnershipType.SELF_TRANSFER,
            TransferOwnershipResolver.resolve(source, dest),
        )
    }

    @Test
    fun scenarioC_aljaziraToD360ViaUnknownDestination_staysUnknown() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        confirmation.confirmAccountOwned(AccountReference(Bank("D360"), "6810"))
        val source = resolver.resolveAccount(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        // Parser exposes external side as Bank.UNKNOWN / 6810
        val dest = resolver.resolveAccount(AccountReference(Bank.UNKNOWN, "6810"))
        assertEquals(OwnershipStatus.OWNED, source)
        assertEquals(OwnershipStatus.UNKNOWN, dest)
        assertEquals(
            TransferOwnershipType.UNKNOWN,
            TransferOwnershipResolver.resolve(source, dest),
        )
    }

    @Test
    fun scenarioD_externalRecipient() = runBlocking {
        val sourceRef = AccountReference(Bank.BANK_ALJAZIRA, "3001")
        val destRef = AccountReference(Bank.UNKNOWN, "0593")
        confirmation.confirmAccountOwned(sourceRef)
        confirmation.markAccountExternal(destRef)
        val source = resolver.resolveAccount(sourceRef)
        val dest = resolver.resolveAccount(destRef)
        assertEquals(OwnershipStatus.OWNED, source)
        assertEquals(OwnershipStatus.EXTERNAL, dest)
        assertEquals(
            TransferOwnershipType.EXTERNAL_OUTGOING,
            TransferOwnershipResolver.resolve(source, dest),
        )
    }

    @Test
    fun scenarioE_sameLast4DifferentBanks() = runBlocking {
        confirmation.confirmAccountOwned(AccountReference(Bank.BANK_ALJAZIRA, "3001"))
        assertEquals(
            OwnershipStatus.OWNED,
            resolver.resolveAccount(AccountReference(Bank.BANK_ALJAZIRA, "3001")),
        )
        assertEquals(
            OwnershipStatus.UNKNOWN,
            resolver.resolveAccount(AccountReference(Bank("D360"), "3001")),
        )
    }

    @Test
    fun p2Integration_unknownCombinations() = runBlocking {
        assertEquals(
            TransferOwnershipType.UNKNOWN,
            TransferOwnershipResolver.resolve(
                resolver.resolveAccount(AccountReference(Bank.BANK_ALJAZIRA, "1")),
                OwnershipStatus.OWNED,
            ),
        )
    }
}
