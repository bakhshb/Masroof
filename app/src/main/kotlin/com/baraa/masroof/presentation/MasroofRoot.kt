package com.baraa.masroof.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.baraa.masroof.presentation.dashboard.DashboardRoute
import com.baraa.masroof.presentation.dashboard.DashboardViewModel
import com.baraa.masroof.presentation.dashboard.TransactionDetailScreen
import com.baraa.masroof.presentation.dashboard.TransactionListScreen
import com.baraa.masroof.presentation.onboarding.OnboardingRoute
import com.baraa.masroof.presentation.onboarding.OnboardingStep
import com.baraa.masroof.presentation.onboarding.OnboardingViewModel
import com.baraa.masroof.presentation.review.ReviewRoute
import com.baraa.masroof.presentation.review.ReviewViewModel
import com.baraa.masroof.presentation.settings.SettingsRoute
import com.baraa.masroof.presentation.settings.SettingsViewModel

private enum class HomeDestination {
    Dashboard,
    Review,
    AllTransactions,
    Settings,
}

/**
 * Minimal root composition: onboarding while incomplete, dashboard + review after HOME.
 */
@Composable
fun MasroofRoot(
    onboardingViewModel: OnboardingViewModel,
    dashboardViewModel: DashboardViewModel,
    reviewViewModel: ReviewViewModel,
    settingsViewModel: SettingsViewModel,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onLocaleChanged: () -> Unit,
    onRequestExport: () -> Unit,
    onRequestImport: () -> Unit,
    onRequestRestoreBackup: () -> Unit,
) {
    val onboardingState by onboardingViewModel.uiState.collectAsState()
    val reviewState by reviewViewModel.uiState.collectAsState()
    val dashboardState by dashboardViewModel.uiState.collectAsState()
    var homeDestination by rememberSaveable { mutableStateOf(HomeDestination.Dashboard) }

    if (onboardingState.step == OnboardingStep.HOME) {
        BackHandler {
            when {
                dashboardState.selectedTransactionId != null ->
                    dashboardViewModel.closeTransactionDetail()

                homeDestination == HomeDestination.AllTransactions ->
                    homeDestination = HomeDestination.Dashboard

                homeDestination == HomeDestination.Settings -> {
                    homeDestination = HomeDestination.Dashboard
                    dashboardViewModel.refresh()
                }

                homeDestination == HomeDestination.Review ->
                    if (reviewState.selectedDetail != null) {
                        reviewViewModel.closeDetail()
                    } else {
                        homeDestination = HomeDestination.Dashboard
                        dashboardViewModel.refresh()
                    }

                else -> {
                    // Consume system back on the home screen — do not exit the app.
                }
            }
        }

        dashboardState.selectedTransactionId?.let { selectedId ->
            val selected = dashboardState.allTransactions.find { it.id == selectedId }
                ?: dashboardState.recentTransactions.find { it.id == selectedId }
            if (selected != null) {
                TransactionDetailScreen(
                    transaction = selected,
                    reclassifying = dashboardState.reclassifying,
                    reclassifySuccess = dashboardState.reclassifySuccess,
                    error = dashboardState.reclassifyError,
                    onBack = dashboardViewModel::closeTransactionDetail,
                    onReclassify = dashboardViewModel::reclassifySelectedTransaction,
                )
                return
            }
            // Keep the detail destination while a refresh reloads rows for the same period.
            if (dashboardState.loading) {
                return
            }
        }

        when (homeDestination) {
            HomeDestination.Dashboard -> DashboardRoute(
                viewModel = dashboardViewModel,
                onOpenReview = { homeDestination = HomeDestination.Review },
                onOpenAllTransactions = { homeDestination = HomeDestination.AllTransactions },
                onOpenTransaction = dashboardViewModel::openTransactionDetail,
                onOpenSettings = { homeDestination = HomeDestination.Settings },
                onRequestSmsPermission = onRequestPermissions,
                onOpenAppSettings = onOpenAppSettings,
            )
            HomeDestination.Review -> ReviewRoute(
                viewModel = reviewViewModel,
                onBack = {
                    homeDestination = HomeDestination.Dashboard
                    dashboardViewModel.refresh()
                },
            )
            HomeDestination.AllTransactions -> {
                TransactionListScreen(
                    periodLabel = dashboardState.periodLabel,
                    transactions = dashboardState.allTransactions,
                    onBack = { homeDestination = HomeDestination.Dashboard },
                    onOpenTransaction = dashboardViewModel::openTransactionDetail,
                )
            }
            HomeDestination.Settings -> SettingsRoute(
                viewModel = settingsViewModel,
                reviewRequiredCount = dashboardState.summary?.reviewRequiredCount ?: 0,
                onBack = {
                    homeDestination = HomeDestination.Dashboard
                    dashboardViewModel.refresh()
                },
                onOpenReview = { homeDestination = HomeDestination.Review },
                onLocaleChanged = onLocaleChanged,
                onRequestExport = onRequestExport,
                onRequestImport = onRequestImport,
                onRequestSmsPermission = onRequestPermissions,
                onOpenAppSettings = onOpenAppSettings,
            )
        }
    } else {
        OnboardingRoute(
            viewModel = onboardingViewModel,
            onRequestPermissions = onRequestPermissions,
            onOpenAppSettings = onOpenAppSettings,
            onRequestRestoreBackup = onRequestRestoreBackup,
        )
    }
}
