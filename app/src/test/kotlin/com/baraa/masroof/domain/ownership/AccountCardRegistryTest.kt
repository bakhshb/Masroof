package com.baraa.masroof.domain.ownership

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.OwnershipStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccountCardRegistryTest {

    private lateinit var db: MasroofDatabase
    private lateinit var accounts: RoomAccountRegistryRepository
    private lateinit var cards: RoomCardRegistryRepository
    private lateinit var confirmation: OwnershipConfirmationService

    private val alj3001 = AccountReference(Bank.BANK_ALJAZIRA, "3001")
    private val d3603001 = AccountReference(Bank("D360"), "3001")
    private val card7271 = CardReference(Bank.BANK_ALJAZIRA, "7271")
    private val d360Card7271 = CardReference(Bank("D360"), "7271")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accounts = RoomAccountRegistryRepository(db.accountRegistryDao())
        cards = RoomCardRegistryRepository(db.cardRegistryDao())
        confirmation = OwnershipConfirmationService(accounts, cards)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun accountObserve_createsUnknownCandidate() = runBlocking {
        accounts.observe(alj3001, "sms-1")
        val entry = accounts.get(alj3001)
        assertNotNull(entry)
        assertEquals(OwnershipStatus.UNKNOWN, entry!!.ownership)
        assertEquals(1, entry.evidenceCount)
        assertEquals("sms-1", entry.firstSeenRawSmsId)
    }

    @Test
    fun cardObserve_createsUnknownCandidate() = runBlocking {
        cards.observe(card7271, "sms-1")
        val entry = cards.get(card7271)
        assertNotNull(entry)
        assertEquals(OwnershipStatus.UNKNOWN, entry!!.ownership)
        assertEquals(1, entry.evidenceCount)
    }

    @Test
    fun repeatedObservation_sameRawSms_isIdempotent() = runBlocking {
        accounts.observe(alj3001, "sms-1")
        accounts.observe(alj3001, "sms-1")
        assertEquals(1, accounts.get(alj3001)!!.evidenceCount)
    }

    @Test
    fun distinctObservations_incrementEvidence() = runBlocking {
        accounts.observe(alj3001, "sms-1")
        accounts.observe(alj3001, "sms-2")
        val entry = accounts.get(alj3001)!!
        assertEquals(2, entry.evidenceCount)
        assertEquals("sms-1", entry.firstSeenRawSmsId)
        assertEquals("sms-2", entry.lastSeenRawSmsId)
    }

    @Test
    fun ownedSurvivesLaterObservation() = runBlocking {
        accounts.observe(alj3001, "sms-1")
        confirmation.confirmAccountOwned(alj3001)
        accounts.observe(alj3001, "sms-2")
        assertEquals(OwnershipStatus.OWNED, accounts.resolve(alj3001))
    }

    @Test
    fun externalSurvivesLaterObservation() = runBlocking {
        accounts.observe(alj3001, "sms-1")
        confirmation.markAccountExternal(alj3001)
        accounts.observe(alj3001, "sms-2")
        assertEquals(OwnershipStatus.EXTERNAL, accounts.resolve(alj3001))
    }

    @Test
    fun clearReturnsToUnknown_keepsEvidence() = runBlocking {
        accounts.observe(alj3001, "sms-1")
        confirmation.confirmAccountOwned(alj3001)
        confirmation.clearAccountOwnership(alj3001)
        val entry = accounts.get(alj3001)!!
        assertEquals(OwnershipStatus.UNKNOWN, entry.ownership)
        assertEquals(1, entry.evidenceCount)
        assertEquals("sms-1", entry.firstSeenRawSmsId)
    }

    @Test
    fun bankScopedIdentitiesRemainSeparate() = runBlocking {
        confirmation.confirmAccountOwned(alj3001)
        assertEquals(OwnershipStatus.OWNED, accounts.resolve(alj3001))
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(d3603001))
        assertNull(accounts.get(d3603001))
    }

    @Test
    fun sameLast4AcrossBanksRemainSeparate() = runBlocking {
        confirmation.confirmCardOwned(card7271)
        assertEquals(OwnershipStatus.OWNED, cards.resolve(card7271))
        assertEquals(OwnershipStatus.UNKNOWN, cards.resolve(d360Card7271))
    }

    @Test
    fun missingRegistryLookup_isUnknown() = runBlocking {
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(alj3001))
        assertEquals(OwnershipStatus.UNKNOWN, cards.resolve(card7271))
    }
}
