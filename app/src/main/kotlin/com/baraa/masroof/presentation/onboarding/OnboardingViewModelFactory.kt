package com.baraa.masroof.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.application.onboarding.HistoricalImportGateway
import com.baraa.masroof.application.onboarding.OnboardingPreferencesRepository

class OnboardingViewModelFactory(
    private val container: AppContainer,
    private val onboardingPreferencesRepository: OnboardingPreferencesRepository,
    private val permissionStateProvider: () -> Boolean,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            return OnboardingViewModel(
                onboardingPrefs = onboardingPreferencesRepository,
                historicalImportGateway = HistoricalImportGateway { after ->
                    container.historicalSmsScanner.scan(after)
                },
                accountRegistryRepository = container.accountRegistryRepository,
                cardRegistryRepository = container.cardRegistryRepository,
                ownershipConfirmationService = container.ownershipConfirmationService,
                reviewRepository = container.reviewRepository,
                discoverFromStoredEvents = { container.discoverFromStoredEvents() },
                refreshReviewQueue = { container.refreshReviewQueue() },
                databaseBackupService = container.databaseBackupService,
                permissionStateProvider = permissionStateProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
