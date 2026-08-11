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

    init {
        reloadFromCurrentState()
    }

    fun reloadFromCurrentState() {
        viewModelScope.launch {
            val permissionGranted = permissionStateProvider()
            val completed = onboardingPrefs.isOnboardingCompleted()
            val savedEpoch = onboardingPrefs.getHistoricalImportStartEpochMillis()
            val savedDate = savedEpoch?.let {
                Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
            }

            val step = when {
                completed -> OnboardingStep.HOME
                !permissionGranted -> OnboardingStep.PERMISSION
                savedDate == null -> OnboardingStep.IMPORT_DATE
                !onboardingPrefs.isHistoricalImportCompleted() -> OnboardingStep.IMPORTING
                else -> OnboardingStep.OWNERSHIP
            }

            _uiState.update {
                it.copy(
                    permissionGranted = permissionGranted,
                    onboardingCompleted = completed,
                    selectedImportDate = savedDate ?: ImportDatePolicy.last27th(LocalDate.now(clock)),
                    selectedDateOption = if (savedDate == null) ImportDateOption.LAST_27TH else it.selectedDateOption,
                    step = if (completed) OnboardingStep.HOME else step,
                )
            }

            if (step == OnboardingStep.OWNERSHIP || completed) {
                loadCandidatesAndCounts()
            }
        }
    }

    fun onStartClicked() {
        _uiState.update {
            it.copy(step = if (it.permissionGranted) OnboardingStep.IMPORT_DATE else OnboardingStep.PERMISSION)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                permissionGranted = granted,
                step = if (granted) OnboardingStep.IMPORT_DATE else OnboardingStep.PERMISSION,
                error = if (granted) null else OnboardingError.PERMISSION_DENIED,
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
        viewModelScope.launch {
            val selectedDate = _uiState.value.selectedImportDate ?: ImportDatePolicy.last27th(LocalDate.now(clock))
            val startInstant = ImportDatePolicy.toStartOfDayInstant(selectedDate, zoneId)

            onboardingPrefs.setHistoricalImportStartEpochMillis(startInstant.toEpochMilli())
            _uiState.update { it.copy(step = OnboardingStep.IMPORTING, importState = ImportState.Scanning, error = null) }

            val result = historicalImportGateway.scan(receivedAfter = startInstant)
            val state = result.toImportState()
            _uiState.update { it.copy(importState = state) }

            when (state) {
                is ImportState.Completed -> {
                    onboardingPrefs.setHistoricalImportCompleted(true)
                    discoverFromStoredEvents()
                    loadCandidatesAndCounts()
                    _uiState.update { it.copy(step = OnboardingStep.OWNERSHIP) }
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
            val state = _uiState.value
            if (state.hasUnknownCandidates) {
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
