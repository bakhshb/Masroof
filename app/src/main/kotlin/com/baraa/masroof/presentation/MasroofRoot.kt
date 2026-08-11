package com.baraa.masroof.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.baraa.masroof.presentation.dashboard.DashboardRoute
import com.baraa.masroof.presentation.dashboard.DashboardViewModel
import com.baraa.masroof.presentation.dashboard.TransactionListScreen
import com.baraa.masroof.presentation.onboarding.OnboardingRoute
import com.baraa.masroof.presentation.onboarding.OnboardingStep
import com.baraa.masroof.presentation.onboarding.OnboardingViewModel
import com.baraa.masroof.presentation.review.ReviewRoute
import com.baraa.masroof.presentation.review.ReviewViewModel

private enum class HomeDestination {
    Dashboard,
    Review,
    AllTransactions,
}

/**
 * Minimal root composition: onboarding while incomplete, dashboard + review after HOME.
 */
@Composable
fun MasroofRoot(
    onboardingViewModel: OnboardingViewModel,
    dashboardViewModel: DashboardViewModel,
    reviewViewModel: ReviewViewModel,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val onboardingState by onboardingViewModel.uiState.collectAsState()
    var homeDestination by rememberSaveable { mutableStateOf(HomeDestination.Dashboard) }

    if (onboardingState.step == OnboardingStep.HOME) {
        when (homeDestination) {
            HomeDestination.Dashboard -> DashboardRoute(
                viewModel = dashboardViewModel,
                onOpenReview = { homeDestination = HomeDestination.Review },
                onOpenAllTransactions = { homeDestination = HomeDestination.AllTransactions },
            )
            HomeDestination.Review -> ReviewRoute(
                viewModel = reviewViewModel,
                onBack = {
                    homeDestination = HomeDestination.Dashboard
                    dashboardViewModel.refresh()
                },
            )
            HomeDestination.AllTransactions -> {
                val dashboardState by dashboardViewModel.uiState.collectAsState()
                TransactionListScreen(
                    periodLabel = dashboardState.periodLabel,
                    transactions = dashboardState.allTransactions,
                    onBack = { homeDestination = HomeDestination.Dashboard },
                )
            }
        }
    } else {
        homeDestination = HomeDestination.Dashboard
        OnboardingRoute(
            viewModel = onboardingViewModel,
            onRequestPermissions = onRequestPermissions,
            onOpenAppSettings = onOpenAppSettings,
        )
    }
}
