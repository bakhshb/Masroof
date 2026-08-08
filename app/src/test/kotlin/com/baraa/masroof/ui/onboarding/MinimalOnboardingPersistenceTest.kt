package com.baraa.masroof.ui.onboarding

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.repository.FinancialAccountRepository
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class MinimalOnboardingPersistenceTest {
    private class FakeAccountRepository : FinancialAccountRepository {
        val rows = mutableListOf<FinancialAccount>()
        var insertCount = 0
        var updateCount = 0

        override fun observeAll(): Flow<List<FinancialAccount>> = MutableStateFlow(rows)
        override suspend fun getActive() = rows.filter { it.isActive }
        override suspend fun getOwnedActive() = rows.filter { it.isActive && it.isOwnedByUser }
        override suspend fun getById(id: Long) = rows.firstOrNull { it.id == id }

        override suspend fun add(
            displayName: String,
            accountType: AccountType,
            institutionName: String?,
            accountNature: AccountNature,
            currency: Currency,
            openingBalance: BigDecimal,
            openingBalanceDate: Long,
            includeInNetWorth: Boolean,
            includeInLiquidity: Boolean,
            notes: String?,
        ): Long {
            insertCount += 1
            val id = insertCount.toLong()
            rows += FinancialAccount(
                id = id,
                displayName = displayName,
                institutionName = institutionName,
                accountType = accountType,
                accountNature = accountNature,
                currency = currency,
                openingBalance = openingBalance,
                openingBalanceDate = openingBalanceDate,
                includeInNetWorth = includeInNetWorth,
                includeInLiquidity = includeInLiquidity,
                isOwnedByUser = true,
                isActive = true,
                notes = notes,
            )
            return id
        }

        override suspend fun update(account: FinancialAccount) {
            updateCount += 1
            val index = rows.indexOfFirst { it.id == account.id }
            require(index >= 0)
            rows[index] = account
        }

        override suspend fun delete(account: FinancialAccount) = Unit
    }

    private fun state() = UiOnboardingState().apply {
        displayName = "حساب الجزيرة"
        institution = "الجزيرة"
        openingBalance = "1000"
        trackingDate = LocalDate.of(2026, 8, 1)
    }

    private suspend fun persist(
        state: UiOnboardingState,
        accounts: FakeAccountRepository,
        repository: OnboardingRepository = TestOnboardingRepository(),
        identifier: suspend (Long, AccountType, String) -> Unit = { _, _, _ -> },
    ): Long = createOrUpdateOnboardingAccount(
        state = state,
        repository = repository,
        financialAccountRepository = accounts,
        saveOptionalIdentifier = identifier,
    )

    @Test
    fun firstSaveCreatesExactlyOneAccount() = runBlocking {
        val accounts = FakeAccountRepository()
        val id = persist(state(), accounts)
        assertEquals(1L, id)
        assertEquals(1, accounts.insertCount)
        assertEquals(1, accounts.rows.size)
    }

    @Test
    fun pressingContinueTwiceCreatesOnlyOneAccount() = runBlocking {
        val accounts = FakeAccountRepository()
        val state = state()
        persist(state, accounts)
        persist(state, accounts)
        assertEquals(1, accounts.insertCount)
        assertEquals(1, accounts.rows.size)
        assertEquals(1, accounts.updateCount)
    }

    @Test
    fun processRecreationReusesExistingAccount() = runBlocking {
        val accounts = FakeAccountRepository()
        val first = state()
        val id = persist(first, accounts)
        val restored = state().apply { createdAccountId = id }
        assertEquals(id, persist(restored, accounts))
        assertEquals(1, accounts.insertCount)
    }

    @Test
    fun existingAccountUpdatesDisplayName() = runBlocking {
        val accounts = FakeAccountRepository()
        val state = state()
        val id = persist(state, accounts)
        state.displayName = "الجزيرة الرئيسي"
        persist(state, accounts)
        assertEquals("الجزيرة الرئيسي", accounts.getById(id)?.displayName)
    }

    @Test
    fun existingAccountUpdatesOpeningBalance() = runBlocking {
        val accounts = FakeAccountRepository()
        val state = state()
        val id = persist(state, accounts)
        state.openingBalance = "1500"
        persist(state, accounts)
        assertEquals(0, BigDecimal("1500").compareTo(accounts.getById(id)?.openingBalance))
    }

    @Test
    fun existingAccountUpdatesOpeningDate() = runBlocking {
        val accounts = FakeAccountRepository()
        val state = state()
        val id = persist(state, accounts)
        state.trackingDate = LocalDate.of(2026, 7, 15)
        persist(state, accounts)
        val expected = state.trackingDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, accounts.getById(id)?.openingBalanceDate)
    }

    @Test
    fun backThenAddLastFourPersistsIdentifierWithoutNewAccount() = runBlocking {
        val accounts = FakeAccountRepository()
        val state = state()
        val savedIdentifiers = mutableSetOf<Pair<Long, String>>()
        persist(state, accounts) { id, _, value ->
            if (value.isNotBlank()) savedIdentifiers += id to value
        }
        state.lastFour = "3001"
        val id = persist(state, accounts) { accountId, _, value ->
            savedIdentifiers += accountId to value
        }
        assertEquals(setOf(id to "3001"), savedIdentifiers)
        assertEquals(1, accounts.insertCount)
    }

    @Test
    fun identifierFailureRetriesWithoutAnotherAccountInsert() = runBlocking {
        val accounts = FakeAccountRepository()
        val repository = TestOnboardingRepository()
        val state = state().apply { lastFour = "3001" }
        var attempts = 0
        val identifier: suspend (Long, AccountType, String) -> Unit = { _, _, _ ->
            attempts += 1
            if (attempts == 1) error("تعذر حفظ معرف الحساب")
        }
        assertTrue(runCatching { persist(state, accounts, repository, identifier) }.isFailure)
        assertEquals(1L, repository.loadDraft()?.createdAccountId)
        persist(state, accounts, repository, identifier)
        assertEquals(1, accounts.insertCount)
        assertEquals(2, attempts)
    }

    @Test
    fun blankLastFourRemainsValid() = runBlocking {
        val accounts = FakeAccountRepository()
        val state = state()
        assertEquals(1L, persist(state, accounts))
        assertFalse(state.identifierConfirmed)
    }

    @Test
    fun sameIdentifierTwiceIsIdempotent() = runBlocking {
        val accounts = FakeAccountRepository()
        val state = state().apply { lastFour = "3001" }
        val identifiers = mutableSetOf<Pair<Long, String>>()
        val save: suspend (Long, AccountType, String) -> Unit = { id, _, value ->
            identifiers += id to value
        }
        persist(state, accounts, identifier = save)
        persist(state, accounts, identifier = save)
        assertEquals(setOf(1L to "3001"), identifiers)
    }

    @Test
    fun senderAssociationRemainsAttachedAfterAccountUpdate() = runBlocking {
        val accounts = FakeAccountRepository()
        val state = state()
        val id = persist(state, accounts)
        val associations = mutableSetOf(id to 7L)
        state.displayName = "الجزيرة الرئيسي"
        persist(state, accounts)
        assertEquals(setOf(id to 7L), associations)
    }

    @Test
    fun accountIdNeverChangesDuringUpdate() = runBlocking {
        val accounts = FakeAccountRepository()
        val state = state()
        val originalId = persist(state, accounts)
        state.institution = "بنك الجزيرة"
        val updatedId = persist(state, accounts)
        assertEquals(originalId, updatedId)
        assertEquals(originalId, state.createdAccountId)
    }

    @Test
    fun senderAssociatesWithExistingAccountWithoutPatternDependency() = runBlocking {
        val state = state().apply { createdAccountId = 41L }
        var association: Pair<Long, Long>? = null
        associateSelectedSender(
            state = state,
            rawSender = "JAZIRA",
            upsertSender = { SelectedSender(7L, "jazira", "Jazira Bank") },
            associateAccount = { accountId, senderId -> association = accountId to senderId },
        )
        assertEquals(41L to 7L, association)
    }
}
