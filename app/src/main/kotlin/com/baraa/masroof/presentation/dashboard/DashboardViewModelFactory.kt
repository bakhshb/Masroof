package com.baraa.masroof.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.baraa.masroof.application.onboarding.HistoricalSmsRescanService
import com.baraa.masroof.application.dashboard.TransactionSmsEvidenceLoader
import android.content.Context
import com.baraa.masroof.application.AppContainer
import java.time.Clock
import java.time.ZoneId

class DashboardViewModelFactory(
    private val container: AppContainer,
    private val permissionStateProvider: () -> Boolean,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DashboardViewModel::class.java))
        return DashboardViewModel(
            overviewLoader = container.dashboardService,
            cardRegistryRepository = container.cardRegistryRepository,
            accountRegistryRepository = container.accountRegistryRepository,
            layoutPreferencesRepository = container.dashboardLayoutPreferencesRepository,
            rescanService = { HistoricalSmsRescanService(container).rescan() },
            reclassificationService = container.transactionReclassificationService,
            ignoreService = container.transactionIgnoreService,
            smsEvidenceLoader = TransactionSmsEvidenceLoader(
                financialTransactionRepository = container.financialTransactionRepository,
                rawSmsRepository = container.rawSmsRepository,
            ),
            permissionStateProvider = permissionStateProvider,
            appContext = container.applicationContext,
            appLocaleRepository = container.appLocaleRepository,
            zoneId = zoneId,
            clock = clock,
        ) as T
    }
}
