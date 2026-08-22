package com.baraa.masroof.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.CardRole
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.data.room.MasroofDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomCardRegistryRepositoryTest {
    private lateinit var database: MasroofDatabase
    private lateinit var repository: RoomCardRegistryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCardRegistryRepository(database.cardRegistryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun setPrimaryCard_demotesExistingPrimary() = runBlocking {
        seedCard("1111", CardRole.PRIMARY)
        seedCard("2222", CardRole.STANDALONE)

        repository.setPrimaryCard(CardReference(Bank.BANK_ALJAZIRA, "2222"))

        val cards = repository.listAll().associateBy { it.last4 }
        assertEquals(CardRole.STANDALONE, cards.getValue("1111").cardRole)
        assertEquals(CardRole.PRIMARY, cards.getValue("2222").cardRole)
    }

    @Test
    fun setSupplementaryCard_rejectsMissingPrimary() {
        runBlocking {
            seedCard("2222", CardRole.STANDALONE)

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "2222"), "9999")
                }
            }
        }
    }

    @Test
    fun clearCardRole_onPrimary_detachesSupplementaries() = runBlocking {
        seedCard("1111", CardRole.PRIMARY)
        repository.observe(CardReference(Bank.BANK_ALJAZIRA, "2222"), "sms-2222")
        repository.setOwnership(CardReference(Bank.BANK_ALJAZIRA, "2222"), OwnershipStatus.OWNED)
        repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "2222"), "1111")

        repository.clearCardRole(CardReference(Bank.BANK_ALJAZIRA, "1111"))

        val supplement = repository.get(CardReference(Bank.BANK_ALJAZIRA, "2222"))!!
        assertEquals(CardRole.STANDALONE, supplement.cardRole)
        assertEquals(null, supplement.parentCardLast4)
    }

    @Test
    fun setPrimaryCard_demotesPrimaryAndDetachesSupplementaries() = runBlocking {
        seedCard("1111", CardRole.PRIMARY)
        repository.observe(CardReference(Bank.BANK_ALJAZIRA, "2222"), "sms-2222")
        repository.setOwnership(CardReference(Bank.BANK_ALJAZIRA, "2222"), OwnershipStatus.OWNED)
        repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "2222"), "1111")
        repository.observe(CardReference(Bank.BANK_ALJAZIRA, "3333"), "sms-3333")
        repository.setOwnership(CardReference(Bank.BANK_ALJAZIRA, "3333"), OwnershipStatus.OWNED)
        repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "3333"), "1111")
        seedCard("4444", CardRole.STANDALONE)

        repository.setPrimaryCard(CardReference(Bank.BANK_ALJAZIRA, "4444"))

        val cards = repository.listAll().associateBy { it.last4 }
        assertEquals(CardRole.STANDALONE, cards.getValue("1111").cardRole)
        assertEquals(CardRole.STANDALONE, cards.getValue("2222").cardRole)
        assertEquals(null, cards.getValue("2222").parentCardLast4)
        assertEquals(CardRole.STANDALONE, cards.getValue("3333").cardRole)
        assertEquals(CardRole.PRIMARY, cards.getValue("4444").cardRole)
    }

    @Test
    fun setSupplementaryCard_onPrimaryWithChildren_detachesChildren() = runBlocking {
        seedCard("1111", CardRole.PRIMARY)
        repository.observe(CardReference(Bank.BANK_ALJAZIRA, "2222"), "sms-2222")
        repository.setOwnership(CardReference(Bank.BANK_ALJAZIRA, "2222"), OwnershipStatus.OWNED)
        repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "2222"), "1111")
        seedCard("3333", CardRole.PRIMARY)

        repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "1111"), "3333")

        val cards = repository.listAll().associateBy { it.last4 }
        assertEquals(CardRole.SUPPLEMENTARY, cards.getValue("1111").cardRole)
        assertEquals("3333", cards.getValue("1111").parentCardLast4)
        assertEquals(CardRole.STANDALONE, cards.getValue("2222").cardRole)
        assertEquals(null, cards.getValue("2222").parentCardLast4)
    }

    @Test
    fun linkDebitToAccount_rejectsCrossBank() {
        runBlocking {
            seedCard("1111", CardRole.STANDALONE)

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.linkDebitToAccount(
                        CardReference(Bank.BANK_ALJAZIRA, "1111"),
                        com.baraa.masroof.domain.model.AccountReference(
                            Bank("D360"),
                            "3001",
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun linkDebitToAccount_clearsPrimaryRoleAndChildren() = runBlocking {
        seedCard("1111", CardRole.PRIMARY)
        repository.observe(CardReference(Bank.BANK_ALJAZIRA, "2222"), "sms-2222")
        repository.setOwnership(CardReference(Bank.BANK_ALJAZIRA, "2222"), OwnershipStatus.OWNED)
        repository.setSupplementaryCard(CardReference(Bank.BANK_ALJAZIRA, "2222"), "1111")

        repository.linkDebitToAccount(
            CardReference(Bank.BANK_ALJAZIRA, "1111"),
            com.baraa.masroof.domain.model.AccountReference(Bank.BANK_ALJAZIRA, "3001"),
        )

        val cards = repository.listAll().associateBy { it.last4 }
        assertEquals(CardType.DEBIT, cards.getValue("1111").cardType)
        assertEquals(CardRole.STANDALONE, cards.getValue("1111").cardRole)
        assertEquals(CardRole.STANDALONE, cards.getValue("2222").cardRole)
    }

    private suspend fun seedCard(last4: String, role: CardRole) {
        repository.observe(CardReference(Bank.BANK_ALJAZIRA, last4), "sms-$last4")
        repository.setOwnership(CardReference(Bank.BANK_ALJAZIRA, last4), OwnershipStatus.OWNED)
        when (role) {
            CardRole.PRIMARY -> repository.setPrimaryCard(CardReference(Bank.BANK_ALJAZIRA, last4))
            CardRole.SUPPLEMENTARY -> error("use setSupplementaryCard")
            CardRole.STANDALONE -> repository.updateCardType(
                CardReference(Bank.BANK_ALJAZIRA, last4),
                CardType.CREDIT,
            )
        }
    }
}
