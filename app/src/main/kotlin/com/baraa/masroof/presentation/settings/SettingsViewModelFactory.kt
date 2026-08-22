package com.baraa.masroof.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.application.onboarding.HistoricalSmsRescanService
import com.baraa.masroof.application.update.InstallPermissionHelper
class SettingsViewModelFactory(
    private val container: AppContainer,
    private val appVersion: String,
    private val permissionStateProvider: () -> Boolean,
    private val onRequestInstallPermission: () -> Unit = {},
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
        return SettingsViewModel(
            cardRegistryRepository = container.cardRegistryRepository,
            accountRegistryRepository = container.accountRegistryRepository,
            ownershipConfirmationService = container.ownershipConfirmationService,
            transactionRestoreService = container.transactionRestoreService,
            rawSmsRepository = container.rawSmsRepository,
            appLocaleRepository = container.appLocaleRepository,
            themePreferencesRepository = container.themePreferencesRepository,
            databaseBackupService = container.databaseBackupService,
            refreshReviewQueue = { container.refreshReviewQueue() },
            reparseStoredEvents = { container.reparseAllStoredEvents() },
            importSmsFromInbox = { HistoricalSmsRescanService(container).rescan() },
            permissionStateProvider = permissionStateProvider,
            appVersion = appVersion,
            appUpdateService = container.appUpdateService,
            apkInstaller = container.apkInstaller,
            canInstallPackages = {
                InstallPermissionHelper.canInstallPackages(container.applicationContext)
            },
            onRequestInstallPermission = onRequestInstallPermission,
        ) as T
    }
}
