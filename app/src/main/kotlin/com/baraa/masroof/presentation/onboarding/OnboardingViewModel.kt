package com.baraa.masroof.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.application.onboarding.HistoricalImportGateway
import com.baraa.masroof.application.onboarding.OnboardingPreferencesRepository
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.sms.scanner.SmsScanFailure
import com.baraa.masroof.sms.scanner.SmsScanResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class OnboardingViewModel(
    private val onboardingPrefs: OnboardingPreferencesRepository,
    private val historicalImportGateway: HistoricalImportGateway,
    private val accountRegistryRepository: AccountRegistryRepository,
    private val cardRegistryRepository: CardRegistryRepository,
    private val ownershipConfirmationService: OwnershipConfirmationService,
    private val reviewRepository: ReviewRepository,
    private val discoverFromStoredEvents: suspend () -> Int,
    private val refreshReviewQueue: suspend () -> Unit,
    private val permissionStateProvider: () -> Boolean,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
    private var importJob: Job? = null

    init {
        reloadFromCurrentState()
    }

    fun reloadFromCurrentState() {
        viewModelScope.launch {
            val permissionGranted = permissionStateProvider()
            val started = onboardingPrefs.isOnboardingStarted()
            val completed = onboardingPrefs.isOnboardingCompleted()
            val savedEpoch = onboardingPrefs.getHistoricalImportStartEpochMillis()
            val savedDate = savedEpoch?.let {
                Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
            }
            val importCompleted = onboardingPrefs.isHistoricalImportCompleted()

            val step = when {
                completed -> OnboardingStep.HOME
                !started -> OnboardingStep.WELCOME
                !permissionGranted -> OnboardingStep.PERMISSION
                !importCompleted -> OnboardingStep.IMPORT_DATE
                else -> OnboardingStep.OWNERSHIP
            }

            _uiState.update {
                it.copy(
                    permissionGranted = permissionGranted,
                    onboardingCompleted = completed,
                    selectedImportDate = savedDate ?: ImportDatePolicy.last27th(LocalDate.now(clock)),
                    selectedDateOption = if (savedDate == null) ImportDateOption.LAST_27TH else it.selectedDateOption,
                    step = step,
                    importState = if (step == OnboardingStep.IMPORTING) ImportState.Scanning else ImportState.Idle,
                )
            }

            if (step == OnboardingStep.OWNERSHIP || step == OnboardingStep.HOME) {
                loadCandidatesAndCounts()
            }
        }
    }

    fun onStartClicked() {
        onboardingPrefs.setOnboardingStarted(true)
        val permissionGranted = permissionStateProvider()
        _uiState.update {
            it.copy(
                permissionGranted = permissionGranted,
                step = if (permissionGranted) OnboardingStep.IMPORT_DATE else OnboardingStep.PERMISSION,
                error = if (permissionGranted) null else OnboardingError.PERMISSION_DENIED,
            )
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update {
            val nextStep = when (it.step) {
                OnboardingStep.PERMISSION -> if (granted) OnboardingStep.IMPORT_DATE else OnboardingStep.PERMISSION
                OnboardingStep.IMPORT_DATE,
                OnboardingStep.IMPORTING,
                -> if (granted) it.step else OnboardingStep.PERMISSION
                OnboardingStep.WELCOME,
                OnboardingStep.OWNERSHIP,
                OnboardingStep.FINALIZE,
                OnboardingStep.HOME,
                -> it.step
            }
            it.copy(
                permissionGranted = granted,
                step = nextStep,
                error = if (!granted && nextStep == OnboardingStep.PERMISSION) {
                    OnboardingError.PERMISSION_DENIED
                } else if (granted && it.error == OnboardingError.PERMISSION_DENIED) {
                    null
                } else {
                    it.error
                },
            )
        }
    }

    fun selectDateOption(option: ImportDateOption) {
        val today = LocalDate.now(clock)
        val date = when (option) {
            ImportDateOption.CURRENT_MONTH_START -> today.withDayOfMonth(1)
            ImportDateOption.LAST_30_DAYS -> today.minusDays(30)
            ImportDateOption.LAST_27TH -> ImportDatePolicy.last27th(today)
            ImportDateOption.CUSTOM -> _uiState.value.selectedImportDate ?: ImportDatePolicy.last27th(today)
        }
        _uiState.update { it.copy(selectedDateOption = option, selectedImportDate = date, error = null) }
    }

    fun selectCustomDate(date: LocalDate) {
        val today = LocalDate.now(clock)
        if (date.isAfter(today)) {
            _uiState.update { it.copy(error = OnboardingError.INVALID_FUTURE_DATE) }
            return
        }
        _uiState.update {
            it.copy(
                selectedDateOption = ImportDateOption.CUSTOM,
                selectedImportDate = date,
                error = null,
            )
        }
    }

    fun startImport() {
        if (importJob?.isActive == true || _uiState.value.importState is ImportState.Scanning) {
            return
        }

        importJob = viewModelScope.launch {
            try {
                val selectedDate = _uiState.value.selectedImportDate ?: ImportDatePolicy.last27th(LocalDate.now(clock))
                val startInstant = ImportDatePolicy.toStartOfDayInstant(selectedDate, zoneId)

                onboardingPrefs.setHistoricalImportStartEpochMillis(startInstant.toEpochMilli())
                onboardingPrefs.setHistoricalImportCompleted(false)
                _uiState.update { it.copy(step = OnboardingStep.IMPORTING, importState = ImportState.Scanning, error = null) }

                val result = historicalImportGateway.scan(receivedAfter = startInstant)
                val state = result.toImportState()
                _uiState.update { it.copy(importState = state) }

                when (state) {
                    is ImportState.Completed -> {
                        try {
                            discoverFromStoredEvents()
                            loadCandidatesAndCounts()
                            onboardingPrefs.setHistoricalImportCompleted(true)
                            _uiState.update { it.copy(step = OnboardingStep.OWNERSHIP) }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            onboardingPrefs.setHistoricalImportCompleted(false)
                            _uiState.update {
                                it.copy(
                                    importState = ImportState.ProviderError(
                                        SmsScanResult(failure = SmsScanFailure.ProviderError("post_scan_setup_failed")),
                                    ),
                                    error = OnboardingError.IMPORT_FAILED,
                                )
                            }
                        }
                    }
                    is ImportState.PermissionError -> {
                        _uiState.update {
                            it.copy(
                                step = OnboardingStep.PERMISSION,
                                error = OnboardingError.PERMISSION_DENIED,
                            )
                        }
                    }
                    is ImportState.ProviderError -> {
                        _uiState.update { it.copy(error = OnboardingError.SMS_PROVIDER_ERROR) }
                    }
                    else -> {
                        _uiState.update { it.copy(error = OnboardingError.IMPORT_FAILED) }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                                importState = ImportState.ProviderError(
                                    SmsScanResult(failure = SmsScanFailure.ProviderError("scan_failed")),
                                ),
                        error = OnboardingError.IMPORT_FAILED,
                    )
                }
            }
        }
    }

    fun setAccountOwnership(candidate: OwnershipCandidateUi, owned: Boolean) {
        viewModelScope.launch {
            try {
                val ref = AccountReference(candidate.bank, candidate.suffix)
                if (owned) {
                    ownershipConfirmationService.confirmAccountOwned(ref)
                } else {
                    ownershipConfirmationService.markAccountExternal(ref)
                }
                loadCandidatesAndCounts()
            } catch (_: Exception) {
                _uiState.update { it.copy(error = OnboardingError.OWNERSHIP_UPDATE_FAILED) }
            }
        }
    }

    fun setCardOwnership(candidate: OwnershipCandidateUi, owned: Boolean) {
        viewModelScope.launch {
            try {
                val ref = CardReference(candidate.bank, candidate.suffix)
                if (owned) {
                    ownershipConfirmationService.confirmCardOwned(ref)
                } else {
                    ownershipConfirmationService.markCardExternal(ref)
                }
                loadCandidatesAndCounts()
            } catch (_: Exception) {
                _uiState.update { it.copy(error = OnboardingError.OWNERSHIP_UPDATE_FAILED) }
            }
        }
    }

    fun finalizeOnboarding() {
        viewModelScope.launch {
            if (hasUnknownCandidatesInRepositories()) {
                _uiState.update { it.copy(error = OnboardingError.OWNERSHIP_UPDATE_FAILED) }
                return@launch
            }
            _uiState.update { it.copy(finalizing = true, error = null) }
            try {
                refreshReviewQueue()
                onboardingPrefs.setOnboardingCompleted(true)
                loadCandidatesAndCounts()
                _uiState.update {
                    it.copy(
                        onboardingCompleted = true,
                        finalizing = false,
                        step = OnboardingStep.FINALIZE,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(finalizing = false, error = OnboardingError.FINALIZATION_FAILED) }
            }
        }
    }

    private suspend fun hasUnknownCandidatesInRepositories(): Boolean {
        val hasUnknownAccounts = accountRegistryRepository.listAll().any {
            it.bank != Bank.UNKNOWN && it.ownership == OwnershipStatus.UNKNOWN
        }
        val hasUnknownCards = cardRegistryRepository.listAll().any {
            it.bank != Bank.UNKNOWN && it.ownership == OwnershipStatus.UNKNOWN
        }
        return hasUnknownAccounts || hasUnknownCards
    }

    fun enterApp() {
        _uiState.update { it.copy(step = OnboardingStep.HOME) }
    }

    private suspend fun loadCandidatesAndCounts() {
        val accounts = accountRegistryRepository.listAll()
            .filter { it.bank != Bank.UNKNOWN }
            .map {
                OwnershipCandidateUi(
                    kind = OwnershipCandidateUi.CandidateKind.ACCOUNT,
                    bank = it.bank,
                    suffix = it.maskedNumber,
                    ownership = it.ownership,
                )
            }
            .sortedBy { it.suffix }
        val cards = cardRegistryRepository.listAll()
            .filter { it.bank != Bank.UNKNOWN }
            .map {
                OwnershipCandidateUi(
                    kind = OwnershipCandidateUi.CandidateKind.CARD,
                    bank = it.bank,
                    suffix = it.last4,
                    ownership = it.ownership,
                )
            }
            .sortedBy { it.suffix }
        val reviewCount = reviewRepository.listRequired().size
        _uiState.update {
            it.copy(
                accounts = accounts,
                cards = cards,
                ownedAccountsCount = accounts.count { c -> c.ownership == OwnershipStatus.OWNED },
                ownedCardsCount = cards.count { c -> c.ownership == OwnershipStatus.OWNED },
                reviewRequiredCount = reviewCount,
            )
        }
    }
}
