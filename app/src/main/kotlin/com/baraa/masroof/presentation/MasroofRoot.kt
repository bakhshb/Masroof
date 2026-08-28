package com.baraa.masroof.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.DebitCardOverview
import com.baraa.masroof.application.notification.NotificationAction
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.MasroofScreenBackground
import com.baraa.masroof.presentation.dashboard.AccountsSummaryRoute
import com.baraa.masroof.presentation.dashboard.CardOwnershipKey
import com.baraa.masroof.presentation.dashboard.CardsSummaryRoute
import com.baraa.masroof.presentation.dashboard.DashboardRoute
import com.baraa.masroof.presentation.dashboard.DashboardUiState
import com.baraa.masroof.presentation.dashboard.DashboardViewModel
import com.baraa.masroof.presentation.dashboard.LoanOwnershipKey
import com.baraa.masroof.presentation.dashboard.LoansSummaryRoute
import com.baraa.masroof.presentation.dashboard.TransactionDetailScreen
import com.baraa.masroof.presentation.dashboard.TransactionListFilterState
import com.baraa.masroof.presentation.dashboard.TransactionListScreen
import com.baraa.masroof.presentation.notification.NotificationCenterRoute
import com.baraa.masroof.presentation.notification.NotificationCenterViewModel
import com.baraa.masroof.presentation.notification.notificationCenterExternalState
import com.baraa.masroof.presentation.onboarding.OnboardingRoute
import com.baraa.masroof.presentation.onboarding.OnboardingStep
import com.baraa.masroof.presentation.onboarding.OnboardingViewModel
import com.baraa.masroof.presentation.review.ReviewRoute
import com.baraa.masroof.presentation.review.ReviewViewModel
import com.baraa.masroof.presentation.settings.SettingsDestination
import com.baraa.masroof.presentation.settings.SettingsRoute
import com.baraa.masroof.presentation.settings.SettingsViewModel

private enum class HomeDestination {
    Dashboard,
    AccountsSummary,
    CardsSummary,
    LoansSummary,
    NotificationCenter,
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
    notificationCenterViewModel: NotificationCenterViewModel,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onLocaleChanged: () -> Unit,
    onRequestExport: () -> Unit,
    onRequestImport: () -> Unit,
    onRequestExportLogs: () -> Unit,
    onRequestRestoreBackup: () -> Unit,
) {
    val onboardingState by onboardingViewModel.uiState.collectAsState()
    val reviewState by reviewViewModel.uiState.collectAsState()
    val dashboardState by dashboardViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val notificationState by notificationCenterViewModel.uiState.collectAsState()
    var homeDestination by rememberSaveable { mutableStateOf(HomeDestination.Dashboard) }
    var pendingSettingsDestination by remember { mutableStateOf<SettingsDestination?>(null) }
    var transactionListSeedFilter by remember { mutableStateOf<TransactionListFilterState?>(null) }
    var transactionListReturnDestination by rememberSaveable {
        mutableStateOf(HomeDestination.Dashboard)
    }
    var transactionListOpenGeneration by remember { mutableStateOf(0) }
    var pendingLoansSummaryLoanKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCardsSummaryCardKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCardsSummaryDebitKey by rememberSaveable { mutableStateOf<String?>(null) }

    fun openTransactionList(
        filter: TransactionListFilterState? = null,
        returnTo: HomeDestination = HomeDestination.Dashboard,
    ) {
        transactionListSeedFilter = filter
        transactionListReturnDestination = returnTo
        transactionListOpenGeneration++
        homeDestination = HomeDestination.AllTransactions
    }

    val notificationExternalState = notificationCenterExternalState(
        dashboardState = dashboardState,
        settingsState = settingsState,
    )

    LaunchedEffect(notificationExternalState) {
        notificationCenterViewModel.refresh(notificationExternalState)
    }

    if (onboardingState.step == OnboardingStep.HOME) {
        BackHandler {
            when {
                dashboardState.selectedTransactionId != null ->
                    dashboardViewModel.closeTransactionDetail()

                homeDestination == HomeDestination.AllTransactions ->
                    homeDestination = transactionListReturnDestination

                homeDestination == HomeDestination.AccountsSummary ->
                    homeDestination = HomeDestination.Dashboard

                homeDestination == HomeDestination.CardsSummary ->
                    homeDestination = HomeDestination.Dashboard

                homeDestination == HomeDestination.LoansSummary ->
                    homeDestination = HomeDestination.Dashboard

                homeDestination == HomeDestination.NotificationCenter ->
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

        val detailOpenedFromAllTransactions =
            homeDestination == HomeDestination.AllTransactions &&
                dashboardState.selectedTransactionId != null

        if (!detailOpenedFromAllTransactions) {
            if (showTransactionDetail(
                    dashboardState = dashboardState,
                    onBack = dashboardViewModel::closeTransactionDetail,
                    onReclassify = dashboardViewModel::reclassifySelectedTransaction,
                    onIgnore = dashboardViewModel::ignoreSelectedTransaction,
                )
            ) {
                return
            }
        }

        when (homeDestination) {
            HomeDestination.Dashboard -> DashboardRoute(
                viewModel = dashboardViewModel,
                notificationUnreadCount = notificationState.unreadCount,
                onOpenNotificationCenter = { homeDestination = HomeDestination.NotificationCenter },
                onOpenAllTransactions = { openTransactionList() },
                onOpenTransaction = dashboardViewModel::openTransactionDetail,
                onOpenSettings = { homeDestination = HomeDestination.Settings },
                onOpenAccountsSummary = {
                    homeDestination = HomeDestination.AccountsSummary
                },
                onOpenCardsSummary = {
                    pendingCardsSummaryCardKey = null
                    pendingCardsSummaryDebitKey = null
                    homeDestination = HomeDestination.CardsSummary
                },
                onOpenLoansSummary = {
                    pendingLoansSummaryLoanKey = null
                    homeDestination = HomeDestination.LoansSummary
                },
                onOpenLoanDetail = { loan ->
                    pendingLoansSummaryLoanKey = LoanOwnershipKey.of(loan)
                    homeDestination = HomeDestination.LoansSummary
                },
                onOpenCardDetail = { card ->
                    pendingCardsSummaryCardKey = CardOwnershipKey.of(card)
                    homeDestination = HomeDestination.CardsSummary
                },
                onOpenDebitDetail = { debit ->
                    pendingCardsSummaryDebitKey = CardOwnershipKey.of(debit)
                    homeDestination = HomeDestination.CardsSummary
                },
                onRequestSmsPermission = onRequestPermissions,
                onOpenAppSettings = onOpenAppSettings,
            )
            HomeDestination.AccountsSummary -> AccountsSummaryRoute(
                viewModel = dashboardViewModel,
                onBack = { homeDestination = HomeDestination.Dashboard },
                onManageAccounts = {
                    pendingSettingsDestination = SettingsDestination.Banks
                    homeDestination = HomeDestination.Settings
                },
                onOpenTransaction = dashboardViewModel::openTransactionDetail,
                onOpenAllTransactions = { filter ->
                    openTransactionList(
                        filter = filter,
                        returnTo = HomeDestination.AccountsSummary,
                    )
                },
            )
            HomeDestination.CardsSummary -> CardsSummaryRoute(
                viewModel = dashboardViewModel,
                initialSelectedCardKey = pendingCardsSummaryCardKey,
                initialSelectedDebitKey = pendingCardsSummaryDebitKey,
                onInitialSelectionConsumed = {
                    pendingCardsSummaryCardKey = null
                    pendingCardsSummaryDebitKey = null
                },
                onBack = { homeDestination = HomeDestination.Dashboard },
                onManageCards = {
                    pendingSettingsDestination = SettingsDestination.Banks
                    homeDestination = HomeDestination.Settings
                },
                onOpenTransaction = dashboardViewModel::openTransactionDetail,
                onOpenAllTransactions = { filter ->
                    openTransactionList(
                        filter = filter,
                        returnTo = HomeDestination.CardsSummary,
                    )
                },
            )
            HomeDestination.LoansSummary -> LoansSummaryRoute(
                viewModel = dashboardViewModel,
                initialSelectedLoanKey = pendingLoansSummaryLoanKey,
                onInitialSelectionConsumed = { pendingLoansSummaryLoanKey = null },
                onBack = { homeDestination = HomeDestination.Dashboard },
                onManageLoans = {
                    pendingSettingsDestination = SettingsDestination.Banks
                    homeDestination = HomeDestination.Settings
                },
                onOpenTransaction = dashboardViewModel::openTransactionDetail,
                onOpenAllTransactions = { filter ->
                    openTransactionList(
                        filter = filter,
                        returnTo = HomeDestination.LoansSummary,
                    )
                },
            )
            HomeDestination.NotificationCenter -> NotificationCenterRoute(
                viewModel = notificationCenterViewModel,
                externalState = notificationExternalState,
                onBack = { homeDestination = HomeDestination.Dashboard },
                onNavigate = { action ->
                    handleNotificationAction(
                        action = action,
                        onRequestPermissions = onRequestPermissions,
                        onOpenAppSettings = onOpenAppSettings,
                        onOpenReview = { homeDestination = HomeDestination.Review },
                        onOpenSettings = { destination ->
                            pendingSettingsDestination = destination
                            homeDestination = HomeDestination.Settings
                        },
                        onDismissRescanStatus = dashboardViewModel::clearRescanStatus,
                    )
                },
            )
            HomeDestination.Review -> ReviewRoute(
                viewModel = reviewViewModel,
                onBack = {
                    homeDestination = HomeDestination.Dashboard
                    dashboardViewModel.refresh()
                },
            )
            HomeDestination.AllTransactions -> {
                val ownedCardKeys = remember(dashboardState.ownedCards) {
                    CardOwnershipKey.ownedKeys(dashboardState.ownedCards)
                }
                val ownedAccountContainerIds = remember(dashboardState.ownedAccounts) {
                    dashboardState.ownedAccounts.mapNotNull { account ->
                        FinancialContainerIdFactory.accountId(account.bank, account.maskedNumber)
                    }.toSet()
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    TransactionListScreen(
                        periodLabel = dashboardState.periodLabel,
                        transactions = dashboardState.allTransactions,
                        onBack = { homeDestination = transactionListReturnDestination },
                        onOpenTransaction = dashboardViewModel::openTransactionDetail,
                        seedFilter = transactionListSeedFilter,
                        onSeedFilterApplied = { transactionListSeedFilter = null },
                        openGeneration = transactionListOpenGeneration,
                        ownedCardKeys = ownedCardKeys,
                        ownedAccountContainerIds = ownedAccountContainerIds,
                        ownedCards = dashboardState.ownedCards,
                        ownedAccounts = dashboardState.ownedAccounts,
                        transactionAccountInvolvement = dashboardState.transactionAccountInvolvement,
                    )
                    showTransactionDetail(
                        dashboardState = dashboardState,
                        onBack = dashboardViewModel::closeTransactionDetail,
                        onReclassify = dashboardViewModel::reclassifySelectedTransaction,
                        onIgnore = dashboardViewModel::ignoreSelectedTransaction,
                        overlayOnList = true,
                    )
                }
            }
            HomeDestination.Settings -> SettingsRoute(
                viewModel = settingsViewModel,
                reviewRequiredCount = dashboardState.summary?.reviewRequiredCount ?: 0,
                pendingDestination = pendingSettingsDestination,
                onPendingDestinationConsumed = { pendingSettingsDestination = null },
                onBack = {
                    homeDestination = HomeDestination.Dashboard
                    dashboardViewModel.refresh()
                },
                onOpenReview = { homeDestination = HomeDestination.Review },
                onLocaleChanged = onLocaleChanged,
                onRequestExport = onRequestExport,
                onRequestImport = onRequestImport,
                onRequestExportLogs = onRequestExportLogs,
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

/**
 * Shows transaction detail when a selection exists.
 *
 * Returns true when the caller should stop composing sibling destinations (full-screen detail).
 * Returns false when nothing is shown, or when detail is rendered as an overlay sibling.
 */
@Composable
private fun showTransactionDetail(
    dashboardState: DashboardUiState,
    onBack: () -> Unit,
    onReclassify: (FinancialTransactionType) -> Unit,
    onIgnore: () -> Unit,
    overlayOnList: Boolean = false,
): Boolean {
    val selectedId = dashboardState.selectedTransactionId ?: return false
    val selected = dashboardState.allTransactions.find { it.id == selectedId }
        ?: dashboardState.recentTransactions.find { it.id == selectedId }
    if (selected != null) {
        val detailContent: @Composable () -> Unit = {
            TransactionDetailScreen(
                transaction = selected,
                smsEvidence = dashboardState.selectedTransactionSms,
                smsLoading = dashboardState.selectedTransactionSmsLoading,
                reclassifying = dashboardState.reclassifying,
                reclassifySuccess = dashboardState.reclassifySuccess,
                ignoring = dashboardState.ignoring,
                error = dashboardState.reclassifyError ?: dashboardState.ignoreError,
                onBack = onBack,
                onReclassify = onReclassify,
                onIgnore = onIgnore,
                ownedCards = dashboardState.ownedCards,
            )
        }
        if (overlayOnList) {
            MasroofScreenBackground(modifier = Modifier.fillMaxSize(), content = detailContent)
        } else {
            detailContent()
        }
        return true
    }
    // Keep the detail destination while a refresh reloads rows for the same period.
    return dashboardState.loading
}

private fun handleNotificationAction(
    action: NotificationAction,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenSettings: (SettingsDestination) -> Unit,
    onDismissRescanStatus: () -> Unit,
) {
    when (action) {
        NotificationAction.REQUEST_SMS_PERMISSION -> onRequestPermissions()
        NotificationAction.OPEN_APP_SETTINGS -> onOpenAppSettings()
        NotificationAction.OPEN_REVIEW -> onOpenReview()
        NotificationAction.OPEN_SETTINGS_CARDS -> onOpenSettings(SettingsDestination.Banks)
        NotificationAction.OPEN_SETTINGS_ACCOUNTS -> onOpenSettings(SettingsDestination.Banks)
        NotificationAction.OPEN_SETTINGS_ABOUT -> onOpenSettings(SettingsDestination.About)
        NotificationAction.DISMISS_RESCAN -> onDismissRescanStatus()
        NotificationAction.MARK_READ_ONLY -> Unit
    }
}
