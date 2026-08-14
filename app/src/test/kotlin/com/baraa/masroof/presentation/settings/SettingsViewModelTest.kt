package com.baraa.masroof.presentation.settings

import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.locale.AppLocaleRepository
import com.baraa.masroof.application.backup.BackupImportOutcome
import com.baraa.masroof.application.backup.DatabaseBackupGateway
import com.baraa.masroof.application.theme.ThemeMode
import com.baraa.masroof.application.theme.ThemePreferencesRepository
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refresh_groupsCardsByOwnership() = runTest {
        val cards = FakeCardRegistry(
            CardRegistryEntry(Bank.BANK_ALJAZIRA, "7271", OwnershipStatus.OWNED, "1", "1"),
            CardRegistryEntry(Bank.BANK_ALJAZIRA, "5123", OwnershipStatus.UNKNOWN, "2", "2"),
            CardRegistryEntry(Bank.BANK_ALJAZIRA, "9999", OwnershipStatus.EXTERNAL, "3", "3"),
        )
        val vm = viewModel(cards = cards)
        vm.refresh()
        advanceUntilIdle()

        assertEquals("7271", vm.uiState.value.followedCards.single().last4)
        assertEquals("5123", vm.uiState.value.unregisteredCards.single().last4)
        assertEquals("9999", vm.uiState.value.stoppedCards.single().last4)
        assertEquals(SettingsViewModelTestFixtures.APP_VERSION, vm.uiState.value.appVersion)
    }

    @Test
    fun setThemeMode_updatesState() = runTest {
        val vm = viewModel(themeMode = ThemeMode.SYSTEM)
        vm.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, vm.uiState.value.themeMode)
    }

    @Test
    fun refresh_groupsAccountsByOwnership() = runTest {
        val accounts = FakeAccountRegistry(
            AccountRegistryEntry(Bank.BANK_ALJAZIRA, "3001", OwnershipStatus.OWNED, "1", "1"),
            AccountRegistryEntry(Bank.BANK_ALJAZIRA, "3002", OwnershipStatus.UNKNOWN, "2", "2"),
            AccountRegistryEntry(Bank.BANK_ALJAZIRA, "3003", OwnershipStatus.EXTERNAL, "3", "3"),
        )
        val vm = viewModel(accounts = accounts)
        vm.refresh()
        advanceUntilIdle()

        assertEquals("3001", vm.uiState.value.followedAccounts.single().maskedNumber)
        assertEquals("3002", vm.uiState.value.unregisteredAccounts.single().maskedNumber)
        assertEquals("3003", vm.uiState.value.stoppedAccounts.single().maskedNumber)
    }

    @Test
    fun resumeTracking_marksCardOwned() = runTest {
        val cards = FakeCardRegistry(
            CardRegistryEntry(Bank.BANK_ALJAZIRA, "9999", OwnershipStatus.EXTERNAL, "3", "3"),
        )
        var refreshCalls = 0
        val vm = viewModel(cards = cards) { refreshCalls++ }
        vm.refresh()
        advanceUntilIdle()
        vm.resumeTracking(ManagedCardUi(Bank.BANK_ALJAZIRA, "9999", OwnershipStatus.EXTERNAL))
        advanceUntilIdle()

        assertEquals(OwnershipStatus.OWNED, cards.entries.single().ownership)
        assertEquals(1, refreshCalls)
        assertTrue(vm.uiState.value.followedCards.any { it.last4 == "9999" })
    }

    @Test
    fun resumeAccountTracking_marksAccountOwned() = runTest {
        val accounts = FakeAccountRegistry(
            AccountRegistryEntry(Bank.BANK_ALJAZIRA, "3003", OwnershipStatus.EXTERNAL, "3", "3"),
        )
        val vm = viewModel(accounts = accounts)
        vm.refresh()
        advanceUntilIdle()
        vm.resumeAccountTracking(ManagedAccountUi(Bank.BANK_ALJAZIRA, "3003", OwnershipStatus.EXTERNAL))
        advanceUntilIdle()

        assertEquals(OwnershipStatus.OWNED, accounts.entries.single().ownership)
        assertTrue(vm.uiState.value.followedAccounts.any { it.maskedNumber == "3003" })
    }

    @Test
    fun confirmStopTracking_marksCardExternal() = runTest {
        val cards = FakeCardRegistry(
            CardRegistryEntry(Bank.BANK_ALJAZIRA, "7271", OwnershipStatus.OWNED, "1", "1"),
        )
        val vm = viewModel(cards = cards)
        vm.refresh()
        advanceUntilIdle()
        val card = vm.uiState.value.followedCards.single()
        vm.requestStopTracking(card)
        vm.confirmStopTracking()
        advanceUntilIdle()

        assertEquals(OwnershipStatus.EXTERNAL, cards.entries.single().ownership)
        assertNull(vm.uiState.value.stopConfirmCardTarget)
        assertTrue(vm.uiState.value.stoppedCards.any { it.last4 == "7271" })
    }

    @Test
    fun confirmStopAccountTracking_marksAccountExternal() = runTest {
        val accounts = FakeAccountRegistry(
            AccountRegistryEntry(Bank.BANK_ALJAZIRA, "3001", OwnershipStatus.OWNED, "1", "1"),
        )
        val vm = viewModel(accounts = accounts)
        vm.refresh()
        advanceUntilIdle()
        val account = vm.uiState.value.followedAccounts.single()
        vm.requestStopAccountTracking(account)
        vm.confirmStopAccountTracking()
        advanceUntilIdle()

        assertEquals(OwnershipStatus.EXTERNAL, accounts.entries.single().ownership)
        assertNull(vm.uiState.value.stopConfirmAccountTarget)
        assertTrue(vm.uiState.value.stoppedAccounts.any { it.maskedNumber == "3001" })
    }

    private fun viewModel(
        cards: CardRegistryRepository = FakeCardRegistry(),
        accounts: AccountRegistryRepository = FakeAccountRegistry(),
        themeMode: ThemeMode = ThemeMode.SYSTEM,
        onRefreshReviewQueue: () -> Unit = {},
    ): SettingsViewModel =
        SettingsViewModel(
            cardRegistryRepository = cards,
            accountRegistryRepository = accounts,
            ownershipConfirmationService = OwnershipConfirmationService(
                accountRegistry = accounts,
                cardRegistry = cards,
            ),
            appLocaleRepository = FakeAppLocaleRepository(),
            themePreferencesRepository = FakeThemePreferencesRepository(themeMode),
            databaseBackupService = FakeDatabaseBackupGateway(),
            refreshReviewQueue = { onRefreshReviewQueue() },
            reparseStoredEvents = { 0 },
            appVersion = SettingsViewModelTestFixtures.APP_VERSION,
            appUpdateService = SettingsViewModelTestFixtures.appUpdateService(),
            apkInstaller = SettingsViewModelTestFixtures.apkInstaller(),
            canInstallPackages = { true },
        )

    private class FakeAppLocaleRepository : AppLocaleRepository {
        private var tag: String = AppLocale.DEFAULT_TAG

        override fun getLanguageTag(): String = tag

        override fun setLanguageTag(languageTag: String) {
            tag = languageTag
        }
    }

    private class FakeThemePreferencesRepository(
        private var mode: ThemeMode = ThemeMode.SYSTEM,
    ) : ThemePreferencesRepository {
        override fun getThemeMode(): ThemeMode = mode

        override fun setThemeMode(mode: ThemeMode) {
            this.mode = mode
        }
    }

    private class FakeDatabaseBackupGateway : DatabaseBackupGateway {
        override suspend fun exportTo(destination: android.net.Uri): Result<Unit> = Result.success(Unit)

        override suspend fun importFrom(source: android.net.Uri): BackupImportOutcome =
            BackupImportOutcome.Failed
    }

    private class FakeCardRegistry(
        vararg initial: CardRegistryEntry,
    ) : CardRegistryRepository {
        val entries = initial.toMutableList()

        override suspend fun observe(reference: CardReference, rawSmsId: String) = Unit

        override suspend fun setOwnership(reference: CardReference, status: OwnershipStatus) {
            val index = entries.indexOfFirst { it.bank == reference.bank && it.last4 == reference.last4 }
            if (index >= 0) {
                entries[index] = entries[index].copy(ownership = status)
            }
        }

        override suspend fun resolve(reference: CardReference): OwnershipStatus =
            entries.find { it.bank == reference.bank && it.last4 == reference.last4 }?.ownership
                ?: OwnershipStatus.UNKNOWN

        override suspend fun get(reference: CardReference) =
            entries.find { it.bank == reference.bank && it.last4 == reference.last4 }

        override suspend fun listAll(): List<CardRegistryEntry> = entries.toList()
    }

    private class FakeAccountRegistry(
        vararg initial: AccountRegistryEntry,
    ) : AccountRegistryRepository {
        val entries = initial.toMutableList()

        override suspend fun observe(reference: AccountReference, rawSmsId: String) = Unit

        override suspend fun setOwnership(reference: AccountReference, status: OwnershipStatus) {
            val masked = reference.maskedNumber ?: return
            val index = entries.indexOfFirst { it.bank == reference.bank && it.maskedNumber == masked }
            if (index >= 0) {
                entries[index] = entries[index].copy(ownership = status)
            }
        }

        override suspend fun resolve(reference: AccountReference): OwnershipStatus =
            entries.find { it.bank == reference.bank && it.maskedNumber == reference.maskedNumber }?.ownership
                ?: OwnershipStatus.UNKNOWN

        override suspend fun get(reference: AccountReference) =
            entries.find { it.bank == reference.bank && it.maskedNumber == reference.maskedNumber }

        override suspend fun listAll(): List<AccountRegistryEntry> = entries.toList()
    }
}
