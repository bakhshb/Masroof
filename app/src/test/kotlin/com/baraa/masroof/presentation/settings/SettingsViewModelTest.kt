package com.baraa.masroof.presentation.settings

import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.locale.AppLocaleRepository
import com.baraa.masroof.application.backup.BackupImportOutcome
import com.baraa.masroof.application.backup.DatabaseBackupGateway
import com.baraa.masroof.application.theme.ThemeMode
import com.baraa.masroof.application.theme.ThemePreferencesRepository
import com.baraa.masroof.application.update.ApkIntegrityVerifier
import com.baraa.masroof.application.update.UpdateManifest
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.model.LoanRegistryEntry
import com.baraa.masroof.domain.model.LoanType
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import com.baraa.masroof.domain.repository.LoanRegistryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            CardRegistryEntry.forTest(
                Bank.BANK_ALJAZIRA,
                "7271",
                OwnershipStatus.OWNED,
                firstSeenRawSmsId = "1",
                lastSeenRawSmsId = "1",
            ),
            CardRegistryEntry.forTest(
                Bank.BANK_ALJAZIRA,
                "5123",
                OwnershipStatus.UNKNOWN,
                firstSeenRawSmsId = "2",
                lastSeenRawSmsId = "2",
            ),
            CardRegistryEntry.forTest(
                Bank.BANK_ALJAZIRA,
                "9999",
                OwnershipStatus.EXTERNAL,
                firstSeenRawSmsId = "3",
                lastSeenRawSmsId = "3",
            ),
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
            AccountRegistryEntry.forTest(
                Bank.BANK_ALJAZIRA,
                "3001",
                OwnershipStatus.OWNED,
                firstSeenRawSmsId = "1",
                lastSeenRawSmsId = "1",
            ),
            AccountRegistryEntry.forTest(
                Bank.BANK_ALJAZIRA,
                "3002",
                OwnershipStatus.UNKNOWN,
                firstSeenRawSmsId = "2",
                lastSeenRawSmsId = "2",
            ),
            AccountRegistryEntry.forTest(
                Bank.BANK_ALJAZIRA,
                "3003",
                OwnershipStatus.EXTERNAL,
                firstSeenRawSmsId = "3",
                lastSeenRawSmsId = "3",
            ),
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
            CardRegistryEntry.forTest(
                Bank.BANK_ALJAZIRA,
                "9999",
                OwnershipStatus.EXTERNAL,
                firstSeenRawSmsId = "3",
                lastSeenRawSmsId = "3",
            ),
        )
        var refreshCalls = 0
        val vm = viewModel(cards = cards, onRefreshReviewQueue = { refreshCalls++ })
        vm.refresh()
        advanceUntilIdle()
        vm.resumeTracking(
            ManagedCardUi(
                id = com.baraa.masroof.domain.ids.RegistryEntityIdFactory.stableCardId(
                    Bank.BANK_ALJAZIRA.id,
                    "9999",
                ),
                bank = Bank.BANK_ALJAZIRA,
                last4 = "9999",
                ownership = OwnershipStatus.EXTERNAL,
            ),
        )
        advanceUntilIdle()

        assertEquals(OwnershipStatus.OWNED, cards.entries.single().ownership)
        assertEquals(1, refreshCalls)
        assertTrue(vm.uiState.value.followedCards.any { it.last4 == "9999" })
    }

    @Test
    fun resumeAccountTracking_marksAccountOwned() = runTest {
        val accounts = FakeAccountRegistry(
            AccountRegistryEntry.forTest(
                Bank.BANK_ALJAZIRA,
                "3003",
                OwnershipStatus.EXTERNAL,
                firstSeenRawSmsId = "3",
                lastSeenRawSmsId = "3",
            ),
        )
        val vm = viewModel(accounts = accounts)
        vm.refresh()
        advanceUntilIdle()
        vm.resumeAccountTracking(
            ManagedAccountUi(
                id = com.baraa.masroof.domain.ids.RegistryEntityIdFactory.stableAccountId(
                    Bank.BANK_ALJAZIRA.id,
                    "3003",
                ),
                bank = Bank.BANK_ALJAZIRA,
                maskedNumber = "3003",
                ownership = OwnershipStatus.EXTERNAL,
            ),
        )
        advanceUntilIdle()

        assertEquals(OwnershipStatus.OWNED, accounts.entries.single().ownership)
        assertTrue(vm.uiState.value.followedAccounts.any { it.maskedNumber == "3003" })
    }

    @Test
    fun confirmStopTracking_marksCardExternal() = runTest {
        val cards = FakeCardRegistry(
            CardRegistryEntry.forTest(
                Bank.BANK_ALJAZIRA,
                "7271",
                OwnershipStatus.OWNED,
                firstSeenRawSmsId = "1",
                lastSeenRawSmsId = "1",
            ),
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
            AccountRegistryEntry.forTest(
                Bank.BANK_ALJAZIRA,
                "3001",
                OwnershipStatus.OWNED,
                firstSeenRawSmsId = "1",
                lastSeenRawSmsId = "1",
            ),
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

    @Test
    fun markCardAsDebit_clearsRoleAndSetsDebitType() = runTest {
        val cards = TrackingCardRegistry(
            CardRegistryEntry.forTest(
                Bank.BANK_ALJAZIRA,
                "7271",
                OwnershipStatus.OWNED,
                firstSeenRawSmsId = "1",
                lastSeenRawSmsId = "1",
                cardRole = com.baraa.masroof.domain.model.CardRole.PRIMARY,
            ),
        )
        val vm = viewModel(cards = cards)
        vm.refresh()
        advanceUntilIdle()
        val card = vm.uiState.value.followedCards.single()

        vm.markCardAsDebit(card)
        advanceUntilIdle()

        assertTrue(cards.markedAsDebit)
    }

    @Test
    fun clearGithubToken_clearsTokenPendingUpdateAndResetsUi() = runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val pendingUpdateStore = com.baraa.masroof.application.update.PendingUpdateStore(
            context.getSharedPreferences("pending_update_prefs_vm_test", android.content.Context.MODE_PRIVATE),
        ).also { it.clear() }
        val manifest = UpdateManifest(
            versionCode = 99,
            versionName = "9.9.0",
            apkFileName = "masroof.apk",
            sha256 = "abc",
            releaseNotes = null,
        )
        pendingUpdateStore.saveAvailable(manifest)
        val appUpdateService = SettingsViewModelTestFixtures.appUpdateService(token = "secret-token")
        val updateCheckCoordinator = SettingsViewModelTestFixtures.updateCheckCoordinator(
            appUpdateService = appUpdateService,
            pendingUpdateStore = pendingUpdateStore,
        )
        val vm = viewModel(
            appUpdateService = appUpdateService,
            updateCheckCoordinator = updateCheckCoordinator,
        )
        vm.refresh()
        advanceUntilIdle()

        vm.clearGithubToken()

        assertFalse(vm.uiState.value.githubTokenConfigured)
        assertEquals(AppUpdateUiState.Idle, vm.uiState.value.updateState)
        assertNull(updateCheckCoordinator.restorePendingUpdate())
        assertFalse(appUpdateService.hasConfiguredToken())
    }

    @Test
    fun refresh_clearsStalePendingUpdateAfterInstall() = runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val pendingUpdateStore = com.baraa.masroof.application.update.PendingUpdateStore(
            context.getSharedPreferences("pending_update_prefs_vm_stale_test", android.content.Context.MODE_PRIVATE),
        ).also { it.clear() }
        val installedManifest = UpdateManifest(
            versionCode = 4,
            versionName = "0.2.1",
            apkFileName = "masroof.apk",
            sha256 = "abc",
            releaseNotes = null,
        )
        pendingUpdateStore.saveAvailable(installedManifest)
        val appUpdateService = SettingsViewModelTestFixtures.appUpdateService()
        val updateCheckCoordinator = SettingsViewModelTestFixtures.updateCheckCoordinator(
            appUpdateService = appUpdateService,
            pendingUpdateStore = pendingUpdateStore,
        )
        val vm = viewModel(
            appUpdateService = appUpdateService,
            updateCheckCoordinator = updateCheckCoordinator,
        )

        vm.refresh()
        advanceUntilIdle()

        assertEquals(AppUpdateUiState.Idle, vm.uiState.value.updateState)
        assertNull(updateCheckCoordinator.restorePendingUpdate())
    }

    @Test
    fun installPendingUpdate_clearsUpdateUiAfterLaunchingInstaller() = runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val pendingUpdateStore = com.baraa.masroof.application.update.PendingUpdateStore(
            context.getSharedPreferences("pending_update_prefs_vm_install_test", android.content.Context.MODE_PRIVATE),
        ).also { it.clear() }
        val apkBytes = "fake-apk".toByteArray()
        val sha256 = ApkIntegrityVerifier.sha256Hex(
            java.io.File(context.cacheDir, "sha-temp").apply {
                writeBytes(apkBytes)
            },
        )
        val manifest = UpdateManifest(
            versionCode = 99,
            versionName = "9.9.0",
            apkFileName = "masroof.apk",
            sha256 = sha256,
            releaseNotes = null,
        )
        pendingUpdateStore.saveAvailable(manifest)
        val appUpdateService = SettingsViewModelTestFixtures.appUpdateService()
        val apkFile = appUpdateService.updateApkFile(manifest)
        apkFile.parentFile?.mkdirs()
        apkFile.writeBytes(apkBytes)
        val updateCheckCoordinator = SettingsViewModelTestFixtures.updateCheckCoordinator(
            appUpdateService = appUpdateService,
            pendingUpdateStore = pendingUpdateStore,
        )
        val vm = viewModel(
            appUpdateService = appUpdateService,
            updateCheckCoordinator = updateCheckCoordinator,
        )

        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.updateState is AppUpdateUiState.ReadyToInstall)

        vm.installPendingUpdate()
        advanceUntilIdle()

        assertEquals(AppUpdateUiState.Idle, vm.uiState.value.updateState)
        assertEquals(manifest, pendingUpdateStore.readAvailable())
    }

    @Test
    fun refresh_buildsBankSummariesAndLoanTrees() = runTest {
        val loans = FakeLoanRegistry(
            LoanRegistryEntry(
                id = "lreg_1",
                bank = Bank.BANK_ALJAZIRA,
                loanType = LoanType.PERSONAL,
                ownership = OwnershipStatus.OWNED,
                firstSeenRawSmsId = "1",
                lastSeenRawSmsId = "1",
            ),
        )
        val cards = FakeCardRegistry(
            CardRegistryEntry.forTest(
                Bank.BANK_ALJAZIRA,
                "7271",
                OwnershipStatus.OWNED,
                firstSeenRawSmsId = "1",
                lastSeenRawSmsId = "1",
            ),
        )
        val accounts = FakeAccountRegistry(
            AccountRegistryEntry.forTest(
                Bank.BANK_ALJAZIRA,
                "3001",
                OwnershipStatus.UNKNOWN,
                firstSeenRawSmsId = "2",
                lastSeenRawSmsId = "2",
            ),
        )
        val vm = viewModel(cards = cards, accounts = accounts, loans = loans)
        vm.refresh()
        advanceUntilIdle()

        val summary = vm.uiState.value.bankSummaries.single()
        assertEquals(Bank.BANK_ALJAZIRA, summary.bank)
        assertEquals(0, summary.followedAccountCount)
        assertEquals(1, summary.unregisteredAccountCount)
        assertEquals(1, summary.followedCardCount)
        assertEquals(1, summary.accountCount)
        assertEquals(1, summary.cardCount)
        assertEquals(1, summary.loanCount)

        val tree = vm.uiState.value.bankTrees.single()
        assertEquals(1, tree.loans.size)
    }

    private fun viewModel(
        cards: CardRegistryRepository = FakeCardRegistry(),
        accounts: AccountRegistryRepository = FakeAccountRegistry(),
        loans: LoanRegistryRepository = FakeLoanRegistry(),
        themeMode: ThemeMode = ThemeMode.SYSTEM,
        onRefreshReviewQueue: () -> Unit = {},
        appUpdateService: com.baraa.masroof.application.update.AppUpdateService =
            SettingsViewModelTestFixtures.appUpdateService(),
        updateCheckCoordinator: com.baraa.masroof.application.update.UpdateCheckCoordinator =
            SettingsViewModelTestFixtures.updateCheckCoordinator(appUpdateService = appUpdateService),
    ): SettingsViewModel =
        SettingsViewModel(
            cardRegistryRepository = cards,
            accountRegistryRepository = accounts,
            loanRegistryRepository = loans,
            ownershipConfirmationService = OwnershipConfirmationService(
                accountRegistry = accounts,
                cardRegistry = cards,
            ),
            appLocaleRepository = FakeAppLocaleRepository(),
            themePreferencesRepository = FakeThemePreferencesRepository(themeMode),
            databaseBackupService = FakeDatabaseBackupGateway(),
            refreshReviewQueue = { onRefreshReviewQueue() },
            reparseStoredEvents = { 0 },
            importSmsFromInbox = { com.baraa.masroof.sms.scanner.SmsScanResult() },
            permissionStateProvider = { true },
            appVersion = SettingsViewModelTestFixtures.APP_VERSION,
            appUpdateService = appUpdateService,
            updateCheckCoordinator = updateCheckCoordinator,
            appLogService = SettingsViewModelTestFixtures.appLogService(),
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

        override suspend fun updateDisplayName(reference: CardReference, displayName: String?) = Unit

        override suspend fun updateCardNetwork(reference: CardReference, network: com.baraa.masroof.domain.model.CardNetwork?) = Unit

        override suspend fun updateCardType(reference: CardReference, cardType: com.baraa.masroof.domain.model.CardType?) = Unit

        override suspend fun linkDebitToAccount(card: CardReference, account: AccountReference) = Unit

        override suspend fun markAsDebit(reference: CardReference) = Unit

        override suspend fun setPrimaryCard(reference: CardReference) = Unit

        override suspend fun setSupplementaryCard(reference: CardReference, primaryLast4: String) = Unit

        override suspend fun clearCardRole(reference: CardReference) = Unit
    }

    private class TrackingCardRegistry(
        vararg initial: CardRegistryEntry,
    ) : CardRegistryRepository {
        val entries = initial.toMutableList()
        var markedAsDebit: Boolean = false

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

        override suspend fun markAsDebit(reference: CardReference) {
            markedAsDebit = true
            val index = entries.indexOfFirst { it.bank == reference.bank && it.last4 == reference.last4 }
            if (index >= 0) {
                entries[index] = entries[index].copy(
                    cardType = com.baraa.masroof.domain.model.CardType.DEBIT,
                    cardRole = com.baraa.masroof.domain.model.CardRole.STANDALONE,
                    parentCardLast4 = null,
                )
            }
        }

        override suspend fun clearCardRole(reference: CardReference) = Unit

        override suspend fun updateCardType(reference: CardReference, cardType: com.baraa.masroof.domain.model.CardType?) = Unit

        override suspend fun updateCardNetwork(
            reference: CardReference,
            network: com.baraa.masroof.domain.model.CardNetwork?,
        ) = Unit

        override suspend fun updateDisplayName(reference: CardReference, displayName: String?) = Unit

        override suspend fun linkDebitToAccount(card: CardReference, account: AccountReference) = Unit

        override suspend fun setPrimaryCard(reference: CardReference) = Unit

        override suspend fun setSupplementaryCard(reference: CardReference, primaryLast4: String) = Unit

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

        override suspend fun updateDisplayName(reference: AccountReference, displayName: String?) = Unit

        override suspend fun updateAccountType(
            reference: AccountReference,
            accountType: com.baraa.masroof.domain.model.AccountType,
        ) = Unit
    }

    private class FakeLoanRegistry(
        vararg initial: LoanRegistryEntry,
    ) : LoanRegistryRepository {
        private val entries = initial.toList()

        override suspend fun listAll(): List<LoanRegistryEntry> = entries
    }
}
