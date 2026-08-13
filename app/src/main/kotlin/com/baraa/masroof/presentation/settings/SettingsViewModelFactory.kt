package com.baraa.masroof.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.application.theme.ThemeMode

class SettingsViewModelFactory(
    private val container: AppContainer,
    private val appVersion: String,
    private val onThemeModeChanged: (ThemeMode) -> Unit = {},
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
        return SettingsViewModel(
            cardRegistryRepository = container.cardRegistryRepository,
            accountRegistryRepository = container.accountRegistryRepository,
            ownershipConfirmationService = container.ownershipConfirmationService,
            appLocaleRepository = container.appLocaleRepository,
            themePreferencesRepository = container.themePreferencesRepository,
            databaseBackupService = container.databaseBackupService,
            refreshReviewQueue = { container.refreshReviewQueue() },
            reparseStoredEvents = { container.reparseAllStoredEvents() },
            appVersion = appVersion,
            onThemeModeChanged = onThemeModeChanged,
        ) as T
    }
}
