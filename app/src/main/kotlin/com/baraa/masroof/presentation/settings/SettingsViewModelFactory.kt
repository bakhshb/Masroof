package com.baraa.masroof.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.baraa.masroof.application.AppContainer

class SettingsViewModelFactory(
    private val container: AppContainer,
    private val appVersion: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
        return SettingsViewModel(
            cardRegistryRepository = container.cardRegistryRepository,
            ownershipConfirmationService = container.ownershipConfirmationService,
            refreshReviewQueue = { container.refreshReviewQueue() },
            reparseStoredEvents = { container.reparseAllStoredEvents() },
            appVersion = appVersion,
        ) as T
    }
}
