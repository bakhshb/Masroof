package com.baraa.masroof.presentation.onboarding

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.sms.scanner.SmsScanFailure
import com.baraa.masroof.sms.scanner.SmsScanResult
import java.time.LocalDate

enum class OnboardingStep {
    WELCOME,
    PERMISSION,
    IMPORT_DATE,
    IMPORTING,
    OWNERSHIP,
    FINALIZE,
    HOME,
}

enum class ImportDateOption {
    CURRENT_MONTH_START,
    LAST_30_DAYS,
    LAST_27TH,
    CUSTOM,
}

sealed interface ImportState {
    data object Idle : ImportState
    data object Scanning : ImportState
    data class Completed(val result: SmsScanResult) : ImportState
    data class PermissionError(val result: SmsScanResult) : ImportState
    data class ProviderError(val result: SmsScanResult) : ImportState
}

data class OwnershipCandidateUi(
    val kind: CandidateKind,
    val bank: Bank,
    val suffix: String,
    val ownership: OwnershipStatus,
) {
    enum class CandidateKind { ACCOUNT, CARD }
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val permissionGranted: Boolean = false,
    val selectedDateOption: ImportDateOption = ImportDateOption.LAST_27TH,
    val selectedImportDate: LocalDate? = null,
    val importState: ImportState = ImportState.Idle,
    val accounts: List<OwnershipCandidateUi> = emptyList(),
    val cards: List<OwnershipCandidateUi> = emptyList(),
    val finalizing: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val ownedAccountsCount: Int = 0,
    val ownedCardsCount: Int = 0,
    val reviewRequiredCount: Int = 0,
    val error: OnboardingError? = null,
) {
    val hasUnknownCandidates: Boolean =
        (accounts + cards).any { it.ownership == OwnershipStatus.UNKNOWN }
}

enum class OnboardingError {
    PERMISSION_DENIED,
    SMS_PROVIDER_ERROR,
    IMPORT_FAILED,
    OWNERSHIP_UPDATE_FAILED,
    FINALIZATION_FAILED,
    INVALID_FUTURE_DATE,
}

internal fun SmsScanResult.toImportState(): ImportState =
    when (failure) {
        null -> ImportState.Completed(this)
        SmsScanFailure.PermissionDenied -> ImportState.PermissionError(this)
        is SmsScanFailure.ProviderError -> ImportState.ProviderError(this)
    }
