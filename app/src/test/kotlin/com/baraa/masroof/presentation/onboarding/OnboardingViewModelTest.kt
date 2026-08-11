package com.baraa.masroof.presentation.onboarding

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
    fun selectedDate_passesCorrectInstantBoundary() = runTest {
        val fixture = Fixture()
        advanceUntilIdle()
        fixture.vm.onPermissionResult(true)
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
        val fixture = Fixture(now = Instant.parse("2026-08-11T08:00:00Z"))
        fixture.vm.onPermissionResult(true)
        fixture.vm.selectCustomDate(LocalDate.parse("2026-08-12"))
        advanceUntilIdle()
        assertEquals(OnboardingError.INVALID_FUTURE_DATE, fixture.vm.uiState.value.error)
    }

    @Test
    fun importSuccess_advancesToOwnership() = runTest {
        val fixture = Fixture()
        fixture.vm.onPermissionResult(true)
        fixture.vm.startImport()
        advanceUntilIdle()
        assertEquals(OnboardingStep.OWNERSHIP, fixture.vm.uiState.value.step)
        assertTrue(fixture.prefs.isHistoricalImportCompleted())
    }

    @Test
    fun providerFailure_showsRetryState() = runTest {
        val fixture = Fixture(scanResult = SmsScanResult(failure = SmsScanFailure.ProviderError("x")))
        fixture.vm.onPermissionResult(true)
        fixture.vm.startImport()
        advanceUntilIdle()
        assertTrue(fixture.vm.uiState.value.importState is ImportState.ProviderError)
        assertEquals(OnboardingError.SMS_PROVIDER_ERROR, fixture.vm.uiState.value.error)
    }

    @Test
    fun permissionFailure_returnsPermissionStep() = runTest {
        val fixture = Fixture(scanResult = SmsScanResult(failure = SmsScanFailure.PermissionDenied))
        fixture.vm.onPermissionResult(true)
        fixture.vm.startImport()
        advanceUntilIdle()
        assertEquals(OnboardingStep.PERMISSION, fixture.vm.uiState.value.step)
    }

    @Test
    fun retryImport_isSafeAndDoesNotResetState() = runTest {
        val fixture = Fixture(scanResult = SmsScanResult(scanned = 2, parsed = 1, duplicates = 1))
        fixture.vm.onPermissionResult(true)
        fixture.vm.startImport()
        fixture.vm.startImport()
        advanceUntilIdle()
        assertEquals(2, fixture.gateway.calls)
    }

    @Test
    fun unknownCandidatesShown_bankUnknownHidden() = runTest {
        val fixture = Fixture(
            accounts = mutableListOf(
                AccountRegistryEntry(Bank.BANK_ALJAZIRA, "3001", OwnershipStatus.UNKNOWN, null, null),
                AccountRegistryEntry(Bank.UNKNOWN, "9999", OwnershipStatus.UNKNOWN, null, null),
            ),
            cards = mutableListOf(
                CardRegistryEntry(Bank.BANK_ALJAZIRA, "7271", OwnershipStatus.UNKNOWN, null, null),
            ),
        )
        fixture.vm.onPermissionResult(true)
        fixture.vm.startImport()
        advanceUntilIdle()
        assertEquals(1, fixture.vm.uiState.value.accounts.size)
        assertEquals("3001", fixture.vm.uiState.value.accounts.single().suffix)
        assertEquals(1, fixture.vm.uiState.value.cards.size)
    }

    @Test
    fun ownershipActions_callP7AndCanRevise() = runTest {
        val fixture = Fixture(
            accounts = mutableListOf(
                AccountRegistryEntry(Bank.BANK_ALJAZIRA, "3001", OwnershipStatus.UNKNOWN, null, null),
            ),
        )
        fixture.vm.onPermissionResult(true)
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
            accounts = mutableListOf(
                AccountRegistryEntry(Bank.BANK_ALJAZIRA, "3001", OwnershipStatus.UNKNOWN, null, null),
            ),
        )
        fixture.vm.onPermissionResult(true)
        fixture.vm.startImport()
        advanceUntilIdle()
        fixture.vm.finalizeOnboarding()
        advanceUntilIdle()
        assertFalse(fixture.prefs.isOnboardingCompleted())

        val noCandidates = Fixture(accounts = mutableListOf(), cards = mutableListOf())
        noCandidates.vm.onPermissionResult(true)
        noCandidates.vm.startImport()
        advanceUntilIdle()
        noCandidates.vm.finalizeOnboarding()
        advanceUntilIdle()
        assertTrue(noCandidates.prefs.isOnboardingCompleted())
    }

    @Test
    fun finalization_refreshesAndPersistsCompletion_andRestartRoutesHome() = runTest {
        val fixture = Fixture(accounts = mutableListOf())
        fixture.vm.onPermissionResult(true)
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
    fun importStartDate_persistsAcrossRestart() = runTest {
        val fixture = Fixture()
        advanceUntilIdle()
        fixture.vm.onPermissionResult(true)
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
        scanResult: SmsScanResult = SmsScanResult(scanned = 10, parsed = 6, duplicates = 2),
        accounts: MutableList<AccountRegistryEntry> = mutableListOf(),
        cards: MutableList<CardRegistryEntry> = mutableListOf(),
    ) {
        val zone: ZoneId = ZoneId.systemDefault()
        val prefs = FakeOnboardingPrefs()
        val accountRepo = FakeAccountRepo(accounts)
        val cardRepo = FakeCardRepo(cards)
        val reviewRepo = FakeReviewRepo()
        val gateway = FakeImportGateway(scanResult)
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
                discoverFromStoredEvents = { 0 },
                refreshReviewQueue = { refreshed = true },
                permissionStateProvider = { true },
                zoneId = zone,
                clock = Clock.fixed(now, zone),
            )
        }
    }

    private class FakeOnboardingPrefs : OnboardingPreferencesRepository {
        var done = false
        var start: Long? = null
        var imported = false
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
        private var result: SmsScanResult,
    ) : HistoricalImportGateway {
        var lastReceivedAfter: Instant? = null
        var calls: Int = 0
        override suspend fun scan(receivedAfter: Instant?): SmsScanResult {
            calls++
            lastReceivedAfter = receivedAfter
            return result
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
