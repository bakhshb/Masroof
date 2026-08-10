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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
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
        assertEquals("sms-1", entry.firstSeenRawSmsId)
        assertEquals("sms-1", entry.lastSeenRawSmsId)
    }

    @Test
    fun cardObserve_createsUnknownCandidate() = runBlocking {
        cards.observe(card7271, "sms-1")
        val entry = cards.get(card7271)
        assertNotNull(entry)
        assertEquals(OwnershipStatus.UNKNOWN, entry!!.ownership)
        assertEquals("sms-1", entry.firstSeenRawSmsId)
    }

    @Test
    fun accountBacklogRerun_ab_isGloballyIdempotent() = runBlocking {
        accounts.observe(alj3001, "sms-A")
        accounts.observe(alj3001, "sms-B")
        val afterFirst = accounts.get(alj3001)!!

        accounts.observe(alj3001, "sms-A")
        accounts.observe(alj3001, "sms-B")
        val afterSecond = accounts.get(alj3001)!!

        assertEquals(afterFirst, afterSecond)
        assertEquals("sms-A", afterSecond.firstSeenRawSmsId)
        assertEquals("sms-B", afterSecond.lastSeenRawSmsId)
        assertEquals(1, accounts.listAll().size)
    }

    @Test
    fun cardBacklogRerun_ab_isGloballyIdempotent() = runBlocking {
        cards.observe(card7271, "sms-A")
        cards.observe(card7271, "sms-B")
        val afterFirst = cards.get(card7271)!!

        cards.observe(card7271, "sms-A")
        cards.observe(card7271, "sms-B")
        val afterSecond = cards.get(card7271)!!

        assertEquals(afterFirst, afterSecond)
        assertEquals("sms-A", afterSecond.firstSeenRawSmsId)
        assertEquals("sms-B", afterSecond.lastSeenRawSmsId)
        assertEquals(1, cards.listAll().size)
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
    fun clearReturnsToUnknown_keepsRow() = runBlocking {
        accounts.observe(alj3001, "sms-1")
        confirmation.confirmAccountOwned(alj3001)
        confirmation.clearAccountOwnership(alj3001)
        val entry = accounts.get(alj3001)!!
        assertEquals(OwnershipStatus.UNKNOWN, entry.ownership)
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

    @Test
    fun bankUnknown_cannotBeConfirmedOwned() = runBlocking {
        val unknown = AccountReference(Bank.UNKNOWN, "6810")
        try {
            confirmation.confirmAccountOwned(unknown)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        assertNull(accounts.get(unknown))
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(unknown))
        assertEquals(0, accounts.listAll().size)
    }

    @Test
    fun bankUnknown_observeDoesNotPersist() = runBlocking {
        accounts.observe(AccountReference(Bank.UNKNOWN, "0593"), "sms-x")
        assertEquals(0, accounts.listAll().size)
    }

    @Test
    fun concurrentAccountObservations_areSafe() = runBlocking {
        val jobs = (1..20).map { i ->
            async(Dispatchers.IO) {
                accounts.observe(alj3001, "sms-$i")
            }
        }
        jobs.awaitAll()
        assertEquals(1, accounts.listAll().size)
        assertEquals(OwnershipStatus.UNKNOWN, accounts.resolve(alj3001))
        assertNotNull(accounts.get(alj3001)!!.firstSeenRawSmsId)
    }

    @Test
    fun concurrentCardObservations_areSafe() = runBlocking {
        val jobs = (1..20).map { i ->
            async(Dispatchers.IO) {
                cards.observe(card7271, "sms-$i")
            }
        }
        jobs.awaitAll()
        assertEquals(1, cards.listAll().size)
        assertEquals(OwnershipStatus.UNKNOWN, cards.resolve(card7271))
    }

    @Test
    fun observationRacingConfirmOwned_preservesOwned() = runBlocking {
        val observeJobs = (1..30).map { i ->
            async(Dispatchers.IO) { accounts.observe(alj3001, "obs-$i") }
        }
        val confirmJob = async(Dispatchers.IO) {
            confirmation.confirmAccountOwned(alj3001)
        }
        (observeJobs + confirmJob).awaitAll()
        assertEquals(1, accounts.listAll().size)
        assertEquals(OwnershipStatus.OWNED, accounts.resolve(alj3001))
    }

    @Test
    fun observationRacingMarkExternal_preservesExternal() = runBlocking {
        val observeJobs = (1..30).map { i ->
            async(Dispatchers.IO) { accounts.observe(alj3001, "obs-$i") }
        }
        val externalJob = async(Dispatchers.IO) {
            confirmation.markAccountExternal(alj3001)
        }
        (observeJobs + externalJob).awaitAll()
        assertEquals(1, accounts.listAll().size)
        assertEquals(OwnershipStatus.EXTERNAL, accounts.resolve(alj3001))
    }
}
