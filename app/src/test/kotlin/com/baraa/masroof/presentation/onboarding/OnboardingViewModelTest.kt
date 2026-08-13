package com.baraa.masroof.presentation.onboarding

import com.baraa.masroof.application.backup.BackupImportOutcome
import com.baraa.masroof.application.backup.DatabaseBackupGateway
import com.baraa.masroof.application.onboarding.HistoricalImportGateway
import com.baraa.masroof.application.onboarding.OnboardingPreferencesRepository
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ReviewItem
import com.baraa.masroof.domain.model.ReviewKind
import com.baraa.masroof.domain.model.ReviewStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import com.baraa.masroof.domain.repository.ReviewRepository
import kotlinx.coroutines.CompletableDeferred
import com.baraa.masroof.sms.scanner.SmsScanFailure
import com.baraa.masroof.sms.scanner.SmsScanResult
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
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
    fun last27th_beforeAndAfter27() {
        assertEquals(
            LocalDate.parse("2026-07-27"),
            ImportDatePolicy.last27th(LocalDate.parse("2026-08-11")),
        )
        assertEquals(
            LocalDate.parse("2026-08-27"),
            ImportDatePolicy.last27th(LocalDate.parse("2026-08-29")),
        )
    }

    @Test
    fun requiredPermissions_doNotRequestSendSms() {
        assertTrue(OnboardingPermissionPolicy.REQUIRED_SMS_PERMISSIONS.contains(android.Manifest.permission.READ_SMS))
        assertTrue(OnboardingPermissionPolicy.REQUIRED_SMS_PERMISSIONS.contains(android.Manifest.permission.RECEIVE_SMS))
        assertFalse(OnboardingPermissionPolicy.REQUIRED_SMS_PERMISSIONS.contains(android.Manifest.permission.SEND_SMS))
    }

    @Test
    fun freshInstall_permissionDenied_stillWelcome() = runTest {
        val fixture = Fixture(permissionGranted = false)
        advanceUntilIdle()
        assertEquals(OnboardingStep.WELCOME, fixture.vm.uiState.value.step)
    }

    @Test
    fun freshInstall_permissionGranted_stillWelcome() = runTest {
        val fixture = Fixture(permissionGranted = true)
        advanceUntilIdle()
        assertEquals(OnboardingStep.WELCOME, fixture.vm.uiState.value.step)
    }

    @Test
    fun startClicked_permissionDenied_routesPermission() = runTest {
        val fixture = Fixture(permissionGranted = false)
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        advanceUntilIdle()
        assertEquals(OnboardingStep.PERMISSION, fixture.vm.uiState.value.step)
        assertTrue(fixture.prefs.isOnboardingStarted())
    }

    @Test
    fun startClicked_permissionGranted_routesImportDate() = runTest {
        val fixture = Fixture(permissionGranted = true)
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        advanceUntilIdle()
        assertEquals(OnboardingStep.IMPORT_DATE, fixture.vm.uiState.value.step)
        assertTrue(fixture.prefs.isOnboardingStarted())
    }

    @Test
    fun restartAfterStartBeforePermission_routesPermission() = runTest {
        val fixture = Fixture(permissionGranted = false)
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        advanceUntilIdle()
        val restartVm = fixture.newViewModel()
        restartVm.reloadFromCurrentState()
        advanceUntilIdle()
        assertEquals(OnboardingStep.PERMISSION, restartVm.uiState.value.step)
    }

    @Test
    fun restartAfterPermissionGranted_routesImportDate() = runTest {
        val fixture = Fixture(permissionGranted = true)
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        advanceUntilIdle()
        val restartVm = fixture.newViewModel()
        restartVm.reloadFromCurrentState()
        advanceUntilIdle()
        assertEquals(OnboardingStep.IMPORT_DATE, restartVm.uiState.value.step)
    }

    @Test
    fun completedOnboarding_routesHome() = runTest {
        val fixture = Fixture(permissionGranted = false)
        fixture.prefs.setOnboardingCompleted(true)
        val restartVm = fixture.newViewModel()
        restartVm.reloadFromCurrentState()
        advanceUntilIdle()
        assertEquals(OnboardingStep.HOME, restartVm.uiState.value.step)
    }

    @Test
    fun permissionRefreshWhileWelcome_doesNotSkipWelcome() = runTest {
        val fixture = Fixture(permissionGranted = false)
        advanceUntilIdle()
        fixture.vm.onPermissionResult(true)
        advanceUntilIdle()
        assertEquals(OnboardingStep.WELCOME, fixture.vm.uiState.value.step)
    }

    @Test
    fun permissionRefreshWhileHome_doesNotRestartOnboarding() = runTest {
        val fixture = Fixture(permissionGranted = true)
        fixture.prefs.setOnboardingCompleted(true)
        val restartVm = fixture.newViewModel()
        restartVm.reloadFromCurrentState()
        advanceUntilIdle()
        restartVm.onPermissionResult(false)
        advanceUntilIdle()
        assertEquals(OnboardingStep.HOME, restartVm.uiState.value.step)
    }

    @Test
    fun importDate_permissionRevoked_routesPermission() = runTest {
        val fixture = Fixture(permissionGranted = true)
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        advanceUntilIdle()
        fixture.vm.onPermissionResult(false)
        advanceUntilIdle()
        assertEquals(OnboardingStep.PERMISSION, fixture.vm.uiState.value.step)
    }

    @Test
    fun selectedDate_passesCorrectInstantBoundary() = runTest {
        val fixture = Fixture(permissionGranted = true)
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        fixture.vm.selectCustomDate(LocalDate.parse("2026-08-03"))
        fixture.vm.startImport()
        advanceUntilIdle()
        assertEquals(
            LocalDate.parse("2026-08-03").atStartOfDay(fixture.zone).toInstant(),
            fixture.gateway.lastReceivedAfter,
        )
    }

    @Test
    fun futureCustomDate_rejected() = runTest {
        val fixture = Fixture(now = Instant.parse("2026-08-11T08:00:00Z"), permissionGranted = true)
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        fixture.vm.selectCustomDate(LocalDate.parse("2026-08-12"))
        advanceUntilIdle()
        assertEquals(OnboardingError.INVALID_FUTURE_DATE, fixture.vm.uiState.value.error)
    }

    @Test
    fun persistedIncompleteImport_routesToImportDateNotEndlessImporting() = runTest {
        val fixture = Fixture(permissionGranted = true)
        fixture.prefs.setOnboardingStarted(true)
        fixture.prefs.setHistoricalImportStartEpochMillis(LocalDate.parse("2026-08-03").atStartOfDay(fixture.zone).toInstant().toEpochMilli())
        fixture.prefs.setHistoricalImportCompleted(false)
        val restartVm = fixture.newViewModel()
        restartVm.reloadFromCurrentState()
        advanceUntilIdle()
        assertEquals(OnboardingStep.IMPORT_DATE, restartVm.uiState.value.step)
        assertTrue(restartVm.uiState.value.importState is ImportState.Idle)
    }

    @Test
    fun persistedIncompleteImport_restoresSavedDate() = runTest {
        val fixture = Fixture(permissionGranted = true)
        val savedDate = LocalDate.parse("2026-08-03")
        fixture.prefs.setOnboardingStarted(true)
        fixture.prefs.setHistoricalImportStartEpochMillis(savedDate.atStartOfDay(fixture.zone).toInstant().toEpochMilli())
        val restartVm = fixture.newViewModel()
        restartVm.reloadFromCurrentState()
        advanceUntilIdle()
        assertEquals(savedDate, restartVm.uiState.value.selectedImportDate)
    }

    @Test
    fun explicitRetryAfterRestart_startsScan() = runTest {
        val fixture = Fixture(permissionGranted = true)
        fixture.prefs.setOnboardingStarted(true)
        fixture.prefs.setHistoricalImportStartEpochMillis(LocalDate.parse("2026-08-03").atStartOfDay(fixture.zone).toInstant().toEpochMilli())
        fixture.prefs.setHistoricalImportCompleted(false)
        val restartVm = fixture.newViewModel()
        restartVm.reloadFromCurrentState()
        advanceUntilIdle()
        restartVm.startImport()
        advanceUntilIdle()
        assertEquals(1, fixture.gateway.calls)
    }

    @Test
    fun doubleStartWhileScanning_onlyOneGatewayCall() = runTest {
        val fixture = Fixture(permissionGranted = true, gateway = FakeImportGateway(blockFirstCall = true))
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        advanceUntilIdle()
        fixture.vm.startImport()
        fixture.vm.startImport()
        advanceUntilIdle()
        assertEquals(1, fixture.gateway.calls)
        fixture.gateway.completeBlockedCall()
        advanceUntilIdle()
    }

    @Test
    fun retryAfterProviderError_allowsSecondGatewayCall() = runTest {
        val gateway = FakeImportGateway(
            initialResult = SmsScanResult(failure = SmsScanFailure.ProviderError("x")),
            nextResult = SmsScanResult(scanned = 1, parsed = 1, duplicates = 0),
        )
        val fixture = Fixture(permissionGranted = true, gateway = gateway)
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        advanceUntilIdle()
        fixture.vm.startImport()
        advanceUntilIdle()
        fixture.vm.startImport()
        advanceUntilIdle()
        assertEquals(2, fixture.gateway.calls)
    }

    @Test
    fun discoveryFailureAfterScanSuccess_doesNotPersistImportCompleted() = runTest {
        val fixture = Fixture(permissionGranted = true, discoverShouldFail = true)
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        advanceUntilIdle()
        fixture.vm.startImport()
        advanceUntilIdle()
        assertFalse(fixture.prefs.isHistoricalImportCompleted())
        assertEquals(OnboardingError.IMPORT_FAILED, fixture.vm.uiState.value.error)
    }

    @Test
    fun retryAfterDiscoveryFailure_succeedsSafely() = runTest {
        val fixture = Fixture(permissionGranted = true, discoverShouldFail = true)
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        advanceUntilIdle()
        fixture.vm.startImport()
        advanceUntilIdle()
        fixture.discoverShouldFail = false
        fixture.vm.startImport()
        advanceUntilIdle()
        assertTrue(fixture.prefs.isHistoricalImportCompleted())
        assertEquals(OnboardingStep.OWNERSHIP, fixture.vm.uiState.value.step)
    }

    @Test
    fun importSuccess_advancesToOwnership() = runTest {
        val fixture = Fixture(permissionGranted = true)
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        fixture.vm.startImport()
        advanceUntilIdle()
        assertEquals(OnboardingStep.OWNERSHIP, fixture.vm.uiState.value.step)
        assertTrue(fixture.prefs.isHistoricalImportCompleted())
    }

    @Test
    fun providerFailure_showsRetryState() = runTest {
        val fixture = Fixture(
            permissionGranted = true,
            gateway = FakeImportGateway(initialResult = SmsScanResult(failure = SmsScanFailure.ProviderError("x"))),
        )
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        fixture.vm.startImport()
        advanceUntilIdle()
        assertTrue(fixture.vm.uiState.value.importState is ImportState.ProviderError)
        assertEquals(OnboardingError.SMS_PROVIDER_ERROR, fixture.vm.uiState.value.error)
    }

    @Test
    fun permissionFailure_returnsPermissionStep() = runTest {
        val fixture = Fixture(
            permissionGranted = true,
            gateway = FakeImportGateway(initialResult = SmsScanResult(failure = SmsScanFailure.PermissionDenied)),
        )
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        fixture.vm.startImport()
        advanceUntilIdle()
        assertEquals(OnboardingStep.PERMISSION, fixture.vm.uiState.value.step)
    }

    @Test
    fun unknownCandidatesShown_bankUnknownHidden() = runTest {
        val fixture = Fixture(
            permissionGranted = true,
            accounts = mutableListOf(
                AccountRegistryEntry(Bank.BANK_ALJAZIRA, "3001", OwnershipStatus.UNKNOWN, null, null),
                AccountRegistryEntry(Bank.UNKNOWN, "9999", OwnershipStatus.UNKNOWN, null, null),
            ),
            cards = mutableListOf(
                CardRegistryEntry(Bank.BANK_ALJAZIRA, "7271", OwnershipStatus.UNKNOWN, null, null),
            ),
        )
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        fixture.vm.startImport()
        advanceUntilIdle()
        assertEquals(1, fixture.vm.uiState.value.accounts.size)
        assertEquals("3001", fixture.vm.uiState.value.accounts.single().suffix)
        assertEquals(1, fixture.vm.uiState.value.cards.size)
    }

    @Test
    fun ownershipActions_callP7AndCanRevise() = runTest {
        val fixture = Fixture(
            permissionGranted = true,
            accounts = mutableListOf(
                AccountRegistryEntry(Bank.BANK_ALJAZIRA, "3001", OwnershipStatus.UNKNOWN, null, null),
            ),
        )
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        fixture.vm.startImport()
        advanceUntilIdle()
        val account = fixture.vm.uiState.value.accounts.single()
        fixture.vm.setAccountOwnership(account, owned = true)
        advanceUntilIdle()
        assertEquals(OwnershipStatus.OWNED, fixture.accountRepo.resolve(AccountReference(Bank.BANK_ALJAZIRA, "3001")))
        fixture.vm.setAccountOwnership(account.copy(ownership = OwnershipStatus.OWNED), owned = false)
        advanceUntilIdle()
        assertEquals(OwnershipStatus.EXTERNAL, fixture.accountRepo.resolve(AccountReference(Bank.BANK_ALJAZIRA, "3001")))
    }

    @Test
    fun unresolvedUnknown_blocksFinalize_zeroCandidatesAllows() = runTest {
        val fixture = Fixture(
            permissionGranted = true,
            accounts = mutableListOf(
                AccountRegistryEntry(Bank.BANK_ALJAZIRA, "3001", OwnershipStatus.UNKNOWN, null, null),
            ),
        )
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        fixture.vm.startImport()
        advanceUntilIdle()
        fixture.vm.finalizeOnboarding()
        advanceUntilIdle()
        assertFalse(fixture.prefs.isOnboardingCompleted())

        val noCandidates = Fixture(permissionGranted = true, accounts = mutableListOf(), cards = mutableListOf())
        advanceUntilIdle()
        noCandidates.vm.onStartClicked()
        noCandidates.vm.startImport()
        advanceUntilIdle()
        noCandidates.vm.finalizeOnboarding()
        advanceUntilIdle()
        assertTrue(noCandidates.prefs.isOnboardingCompleted())
    }

    @Test
    fun finalization_refreshesAndPersistsCompletion_andRestartRoutesHome() = runTest {
        val fixture = Fixture(permissionGranted = true, accounts = mutableListOf())
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        fixture.vm.startImport()
        advanceUntilIdle()
        fixture.vm.finalizeOnboarding()
        advanceUntilIdle()
        assertTrue(fixture.refreshed)
        assertTrue(fixture.prefs.isOnboardingCompleted())
        assertEquals(OnboardingStep.FINALIZE, fixture.vm.uiState.value.step)

        val restartVm = fixture.newViewModel()
        restartVm.reloadFromCurrentState()
        advanceUntilIdle()
        assertEquals(OnboardingStep.HOME, restartVm.uiState.value.step)
    }

    @Test
    fun finalizeRechecksRepositoryState_beforeCompleting() = runTest {
        val fixture = Fixture(permissionGranted = true, accounts = mutableListOf())
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        fixture.vm.startImport()
        advanceUntilIdle()
        fixture.accountRepo.addUnknown("3001")
        fixture.vm.finalizeOnboarding()
        advanceUntilIdle()
        assertFalse(fixture.prefs.isOnboardingCompleted())
    }

    @Test
    fun importStartDate_persistsAcrossRestart() = runTest {
        val fixture = Fixture(permissionGranted = true)
        advanceUntilIdle()
        fixture.vm.onStartClicked()
        fixture.vm.selectCustomDate(LocalDate.parse("2026-08-03"))
        fixture.vm.startImport()
        advanceUntilIdle()
        val restartVm = fixture.newViewModel()
        restartVm.reloadFromCurrentState()
        advanceUntilIdle()
        assertEquals(LocalDate.parse("2026-08-03"), restartVm.uiState.value.selectedImportDate)
    }

    private class Fixture(
        now: Instant = Instant.parse("2026-08-11T08:00:00Z"),
        permissionGranted: Boolean = true,
        gateway: FakeImportGateway = FakeImportGateway(),
        discoverShouldFail: Boolean = false,
        accounts: MutableList<AccountRegistryEntry> = mutableListOf(),
        cards: MutableList<CardRegistryEntry> = mutableListOf(),
    ) {
        val zone: ZoneId = ZoneId.systemDefault()
        val prefs = FakeOnboardingPrefs()
        val accountRepo = FakeAccountRepo(accounts)
        val cardRepo = FakeCardRepo(cards)
        val reviewRepo = FakeReviewRepo()
        val gateway = gateway
        val backupGateway = FakeDatabaseBackupGateway()
        var permissionGranted: Boolean = permissionGranted
        var discoverShouldFail: Boolean = discoverShouldFail
        var refreshed: Boolean = false

        val vm: OnboardingViewModel = newViewModel(now)

        fun newViewModel(now: Instant = Instant.parse("2026-08-11T08:00:00Z")): OnboardingViewModel {
            return OnboardingViewModel(
                onboardingPrefs = prefs,
                historicalImportGateway = gateway,
                accountRegistryRepository = accountRepo,
                cardRegistryRepository = cardRepo,
                ownershipConfirmationService = OwnershipConfirmationService(accountRepo, cardRepo),
                reviewRepository = reviewRepo,
                discoverFromStoredEvents = {
                    if (discoverShouldFail) error("discover failed")
                    0
                },
                refreshReviewQueue = { refreshed = true },
                databaseBackupService = backupGateway,
                permissionStateProvider = { permissionGranted },
                zoneId = zone,
                clock = Clock.fixed(now, zone),
            )
        }
    }

    private class FakeDatabaseBackupGateway(
        var outcome: BackupImportOutcome = BackupImportOutcome.InvalidPackage,
    ) : DatabaseBackupGateway {
        var lastImportUri: android.net.Uri? = null

        override suspend fun exportTo(destination: android.net.Uri): Result<Unit> = Result.success(Unit)

        override suspend fun importFrom(source: android.net.Uri): BackupImportOutcome {
            lastImportUri = source
            return outcome
        }
    }

    private class FakeOnboardingPrefs : OnboardingPreferencesRepository {
        var started = false
        var done = false
        var start: Long? = null
        var imported = false
        override fun isOnboardingStarted(): Boolean = started
        override fun setOnboardingStarted(started: Boolean) {
            this.started = started
        }
        override fun isOnboardingCompleted(): Boolean = done
        override fun setOnboardingCompleted(completed: Boolean) {
            done = completed
        }
        override fun getHistoricalImportStartEpochMillis(): Long? = start
        override fun setHistoricalImportStartEpochMillis(epochMillis: Long?) {
            start = epochMillis
        }
        override fun isHistoricalImportCompleted(): Boolean = imported
        override fun setHistoricalImportCompleted(completed: Boolean) {
            imported = completed
        }
    }

    private class FakeImportGateway(
        initialResult: SmsScanResult = SmsScanResult(scanned = 10, parsed = 6, duplicates = 2),
        private val nextResult: SmsScanResult? = null,
        private val blockFirstCall: Boolean = false,
    ) : HistoricalImportGateway {
        private var result: SmsScanResult = initialResult
        private var blocked: CompletableDeferred<Unit>? = null
        var lastReceivedAfter: Instant? = null
        var calls: Int = 0
        override suspend fun scan(receivedAfter: Instant?): SmsScanResult {
            calls++
            lastReceivedAfter = receivedAfter
            if (blockFirstCall && calls == 1) {
                blocked = CompletableDeferred()
                blocked?.await()
            }
            val current = result
            if (nextResult != null) {
                result = nextResult
            }
            return current
        }
        fun completeBlockedCall() {
            blocked?.complete(Unit)
        }
    }

    private class FakeAccountRepo(
        private val entries: MutableList<AccountRegistryEntry>,
    ) : AccountRegistryRepository {
        override suspend fun observe(reference: AccountReference, rawSmsId: String) = Unit
        override suspend fun setOwnership(reference: AccountReference, status: OwnershipStatus) {
            val idx = entries.indexOfFirst { it.bank == reference.bank && it.maskedNumber == reference.maskedNumber }
            if (idx >= 0) entries[idx] = entries[idx].copy(ownership = status)
            else entries += AccountRegistryEntry(reference.bank, reference.maskedNumber ?: "", status, null, null)
        }
        override suspend fun resolve(reference: AccountReference): OwnershipStatus =
            entries.firstOrNull { it.bank == reference.bank && it.maskedNumber == reference.maskedNumber }?.ownership
                ?: OwnershipStatus.UNKNOWN
        override suspend fun get(reference: AccountReference): AccountRegistryEntry? =
            entries.firstOrNull { it.bank == reference.bank && it.maskedNumber == reference.maskedNumber }
        override suspend fun listAll(): List<AccountRegistryEntry> = entries.toList()
        fun addUnknown(maskedNumber: String) {
            entries += AccountRegistryEntry(Bank.BANK_ALJAZIRA, maskedNumber, OwnershipStatus.UNKNOWN, null, null)
        }
    }

    private class FakeCardRepo(
        private val entries: MutableList<CardRegistryEntry>,
    ) : CardRegistryRepository {
        override suspend fun observe(reference: CardReference, rawSmsId: String) = Unit
        override suspend fun setOwnership(reference: CardReference, status: OwnershipStatus) {
            val idx = entries.indexOfFirst { it.bank == reference.bank && it.last4 == reference.last4 }
            if (idx >= 0) entries[idx] = entries[idx].copy(ownership = status)
            else entries += CardRegistryEntry(reference.bank, reference.last4 ?: "", status, null, null)
        }
        override suspend fun resolve(reference: CardReference): OwnershipStatus =
            entries.firstOrNull { it.bank == reference.bank && it.last4 == reference.last4 }?.ownership
                ?: OwnershipStatus.UNKNOWN
        override suspend fun get(reference: CardReference): CardRegistryEntry? =
            entries.firstOrNull { it.bank == reference.bank && it.last4 == reference.last4 }
        override suspend fun listAll(): List<CardRegistryEntry> = entries.toList()
    }

    private class FakeReviewRepo : ReviewRepository {
        override suspend fun getById(id: String): ReviewItem? = null
        override suspend fun findByRawSmsId(rawSmsId: String): ReviewItem? = null
        override suspend fun listRequired(): List<ReviewItem> = emptyList()
        override suspend fun listAll(): List<ReviewItem> = emptyList()
        override suspend fun upsertRequired(
            rawSmsId: String,
            kind: ReviewKind,
            reasons: List<String>,
            now: Instant,
        ): ReviewItem {
            return ReviewItem(
                id = "review:$rawSmsId",
                rawSmsId = rawSmsId,
                kind = kind,
                status = ReviewStatus.REQUIRED,
                reasons = reasons,
                createdAt = now,
                updatedAt = now,
                resolvedAt = null,
                resolutionKind = null,
                resolvedTransactionId = null,
            )
        }
        override suspend fun markResolved(
            id: String,
            resolutionKind: com.baraa.masroof.domain.model.ReviewResolutionKind,
            resolvedAt: Instant,
            resolvedTransactionId: String?,
        ): ReviewItem? = null
    }
}
