package com.baraa.masroof.presentation.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.DebitCardOverview
import com.baraa.masroof.application.dashboard.LoanOverview
import com.baraa.masroof.application.dashboard.CurrentAccountFlowDetailGrouping
import com.baraa.masroof.application.dashboard.DashboardLayoutSnapshot
import com.baraa.masroof.application.dashboard.DashboardSectionId
import com.baraa.masroof.application.dashboard.MonthlyFinancialSummary
import com.baraa.masroof.presentation.common.IconTextButton
import com.baraa.masroof.presentation.common.IconTextButtonOutlined
import com.baraa.masroof.presentation.common.LongPullToRefreshBox
import com.baraa.masroof.presentation.common.MasroofAppBar
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofPeriodPill
import com.baraa.masroof.presentation.common.ReviewNotificationIconButton
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.common.SmsPermissionNotice
import com.baraa.masroof.presentation.common.SmsRescanStatusNotice
import com.baraa.masroof.presentation.common.UnregisteredCardsNotice
import com.baraa.masroof.presentation.common.ForeignCurrencyNotice
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    notificationUnreadCount: Int = 0,
    onOpenNotificationCenter: () -> Unit = {},
    onOpenAllTransactions: () -> Unit = {},
    onOpenTransaction: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAccountsSummary: () -> Unit = onOpenSettings,
    onOpenCardsSummary: () -> Unit = onOpenSettings,
    onOpenLoansSummary: () -> Unit = onOpenSettings,
    onOpenLoanDetail: (LoanOverview) -> Unit = { onOpenLoansSummary() },
    onOpenCardDetail: (CreditCardDashboardRow) -> Unit = { onOpenCardsSummary() },
    onOpenDebitDetail: (DebitCardOverview) -> Unit = { onOpenAccountsSummary() },
    onRequestSmsPermission: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    val state by viewModel.uiState.collectAsState()
    DashboardScreen(
        state = state,
        onPrevious = viewModel::goToPreviousPeriod,
        onNext = viewModel::goToNextPeriod,
        onCurrent = viewModel::goToCurrentPeriod,
        onRetry = viewModel::refreshWithSmsImport,
        onRescan = viewModel::rescanSms,
        notificationUnreadCount = notificationUnreadCount,
        onOpenNotificationCenter = onOpenNotificationCenter,
        onOpenAllTransactions = onOpenAllTransactions,
        onOpenTransaction = onOpenTransaction,
        onOpenSettings = onOpenSettings,
        onOpenAccountsSummary = onOpenAccountsSummary,
        onOpenCardsSummary = onOpenCardsSummary,
        onOpenLoansSummary = onOpenLoansSummary,
        onOpenLoanDetail = onOpenLoanDetail,
        onOpenCardDetail = onOpenCardDetail,
        onOpenDebitDetail = onOpenDebitDetail,
        onRequestSmsPermission = onRequestSmsPermission,
        onOpenAppSettings = onOpenAppSettings,
        onDismissRescanStatus = viewModel::clearRescanStatus,
        onOpenCustomize = viewModel::openCustomizeSheet,
        onDismissCustomize = viewModel::dismissCustomizeSheet,
        onSaveCustomize = viewModel::saveCustomizeLayout,
        onToggleCustomizeSection = viewModel::toggleCustomizeSection,
        onSetCustomizeSectionSize = viewModel::setCustomizeSectionSize,
        onMoveCustomizeSection = viewModel::moveCustomizeSection,
        onToggleCustomizeQuickExpense = viewModel::toggleCustomizeQuickExpense,
        onToggleCustomizeQuickIncome = viewModel::toggleCustomizeQuickIncome,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(
    state: DashboardUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
    onRetry: () -> Unit,
    onRescan: () -> Unit,
    notificationUnreadCount: Int,
    onOpenNotificationCenter: () -> Unit,
    onOpenAllTransactions: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccountsSummary: () -> Unit,
    onOpenCardsSummary: () -> Unit,
    onOpenLoansSummary: () -> Unit,
    onOpenLoanDetail: (LoanOverview) -> Unit,
    onOpenCardDetail: (CreditCardDashboardRow) -> Unit,
    onOpenDebitDetail: (DebitCardOverview) -> Unit,
    onRequestSmsPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismissRescanStatus: () -> Unit,
    onOpenCustomize: () -> Unit,
    onDismissCustomize: () -> Unit,
    onSaveCustomize: () -> Unit,
    onToggleCustomizeSection: (DashboardSectionId) -> Unit,
    onSetCustomizeSectionSize: (DashboardSectionId, com.baraa.masroof.application.dashboard.DashboardSectionSize) -> Unit,
    onMoveCustomizeSection: (DashboardSectionId, Int) -> Unit,
    onToggleCustomizeQuickExpense: () -> Unit,
    onToggleCustomizeQuickIncome: () -> Unit,
) {
    var flowDetailMode by rememberSaveable { mutableStateOf<DashboardFlowDetailMode?>(null) }
    val context = LocalContext.current
    val periodRangeLabel = state.period?.let {
        FinancialPeriodUiFormatter.formatRange(context, it)
    } ?: state.periodLabel
    val today = LocalDate.now(ZoneId.systemDefault())
    val isPullRefreshing = (state.loading && state.summary != null) || state.rescanning
    val activeLayout = state.customizeDraft ?: state.dashboardLayout

    if (flowDetailMode != null && state.currentAccount != null) {
        BackHandler { flowDetailMode = null }
        DashboardFlowDetailScreen(
            mode = flowDetailMode!!,
            summary = state.currentAccount,
            periodRangeLabel = periodRangeLabel,
            transactions = state.allTransactions,
            grouping = state.flowDetailGrouping ?: CurrentAccountFlowDetailGrouping.empty(),
            onBack = { flowDetailMode = null },
            onOpenTransaction = onOpenTransaction,
        )
        return
    }

    if (state.customizeSheetOpen && state.customizeDraft != null) {
        DashboardCustomizeBottomSheet(
            draft = state.customizeDraft,
            onDismiss = onDismissCustomize,
            onSave = onSaveCustomize,
            onToggleSection = onToggleCustomizeSection,
            onSetSectionSize = onSetCustomizeSectionSize,
            onMoveSection = onMoveCustomizeSection,
            onToggleQuickExpense = onToggleCustomizeQuickExpense,
            onToggleQuickIncome = onToggleCustomizeQuickIncome,
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DashboardAppBar(
            unreadCount = notificationUnreadCount,
            onOpenNotificationCenter = onOpenNotificationCenter,
            onOpenSettings = onOpenSettings,
        )
        LongPullToRefreshBox(
            isRefreshing = isPullRefreshing,
            onRefresh = onRetry,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MasroofPeriodPill(
                    label = periodRangeLabel,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    previousContentDescription = stringResource(R.string.dashboard_period_previous),
                    nextContentDescription = stringResource(R.string.dashboard_period_next),
                    onCustomize = onOpenCustomize,
                    customizeLabel = stringResource(R.string.dashboard_customize),
                )

                state.periodAdjustmentHint?.let { hint ->
                    Text(
                        hint,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!state.isCurrentPeriod) {
                    IconTextButtonOutlined(
                        onClick = onCurrent,
                        icon = MasroofIcons.backToCurrent,
                        text = stringResource(R.string.dashboard_back_to_current),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (!state.smsPermissionGranted) {
                    SmsPermissionNotice(
                        onRequestPermission = onRequestSmsPermission,
                        onOpenAppSettings = onOpenAppSettings,
                    )
                }

                state.rescanStatus?.let { status ->
                    SmsRescanStatusNotice(
                        status = status,
                        onDismiss = onDismissRescanStatus,
                    )
                }

                when {
                    state.loading && state.summary == null -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    state.error != null && state.summary == null -> {
                        DashboardErrorContent(onRetry = onRetry)
                    }

                    else -> {
                        val summary = state.summary
                        val currentAccount = state.currentAccount
                        if (summary != null && currentAccount != null) {
                            DashboardCustomizableSections(
                                state = state,
                                summary = summary,
                                layout = activeLayout,
                                today = today,
                                editing = state.customizeSheetOpen,
                                onOpenExpenseDetails = { flowDetailMode = DashboardFlowDetailMode.Expense },
                                onOpenIncomeDetails = { flowDetailMode = DashboardFlowDetailMode.Income },
                                onOpenSettings = onOpenSettings,
                                onOpenAccountsSummary = onOpenAccountsSummary,
                                onOpenCardsSummary = onOpenCardsSummary,
                                onOpenLoansSummary = onOpenLoansSummary,
                                onOpenLoanDetail = onOpenLoanDetail,
                                onOpenCardDetail = onOpenCardDetail,
                                onOpenDebitDetail = onOpenDebitDetail,
                                onOpenAllTransactions = onOpenAllTransactions,
                                onOpenTransaction = onOpenTransaction,
                                onRescan = onRescan,
                            )

                            if (summary.excludedOtherCurrencyCount > 0) {
                                ForeignCurrencyNotice(excludedCount = summary.excludedOtherCurrencyCount)
                            }

                            if (state.error != null) {
                                DashboardErrorContent(onRetry = onRetry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCustomizableSections(
    state: DashboardUiState,
    summary: MonthlyFinancialSummary,
    layout: DashboardLayoutSnapshot,
    today: LocalDate,
    editing: Boolean,
    onOpenExpenseDetails: () -> Unit,
    onOpenIncomeDetails: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccountsSummary: () -> Unit,
    onOpenCardsSummary: () -> Unit,
    onOpenLoansSummary: () -> Unit,
    onOpenLoanDetail: (LoanOverview) -> Unit,
    onOpenCardDetail: (CreditCardDashboardRow) -> Unit,
    onOpenDebitDetail: (DebitCardOverview) -> Unit,
    onOpenAllTransactions: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onRescan: () -> Unit,
) {
    state.currentAccount ?: return
    val accountsFleet = resolveDashboardAccountsFleet(
        ownedAccounts = state.ownedAccounts,
        fleet = state.accountsFleet,
    )

    layout.orderedVisibleSections().forEach { entry ->
        Column(
            modifier = Modifier.dashboardSectionFrame(entry.size, editing),
        ) {
            when (entry.id) {
                DashboardSectionId.HERO -> {
                    DashboardHeroCard(
                        accountsFleet = accountsFleet,
                        period = state.period,
                        isCurrentPeriod = state.isCurrentPeriod,
                        today = today,
                        size = entry.size,
                    )
                }

                DashboardSectionId.QUICK -> {
                    DashboardQuickSummaryRow(
                        accountsFleet = accountsFleet,
                        onOpenExpenseDetails = onOpenExpenseDetails,
                        onOpenIncomeDetails = onOpenIncomeDetails,
                        showExpense = layout.quickExpenseVisible,
                        showIncome = layout.quickIncomeVisible,
                        size = entry.size,
                    )
                }

                DashboardSectionId.ACCOUNTS -> {
                    DashboardAccountsSection(
                        accounts = state.ownedAccounts,
                        bankHierarchy = state.bankHierarchy,
                        onViewAll = onOpenAccountsSummary,
                    )
                }

                DashboardSectionId.CARDS -> {
                    val cardNetworks = state.ownedCards.associate { CardOwnershipKey.of(it) to it.cardNetwork }
                    val followedFacilities = state.followedCreditFacilitiesCreditOnly()
                    if (followedFacilities != null) {
                        CreditFacilitiesSection(
                            overview = followedFacilities,
                            cardNetworksByLast4 = cardNetworks,
                            zoneId = ZoneId.systemDefault(),
                            ownedCards = state.ownedCards,
                            onViewAll = onOpenCardsSummary,
                            onOpenCard = onOpenCardDetail,
                            onOpenDebit = onOpenDebitDetail,
                        )
                    } else {
                        state.followedCreditCardsOverview()?.let { followedOverview ->
                            if (followedOverview.hasContent) {
                                CreditCardsSection(
                                    overview = followedOverview,
                                    cardNetworksByLast4 = cardNetworks,
                                    zoneId = ZoneId.systemDefault(),
                                    ownedCards = state.ownedCards,
                                    onViewAll = onOpenCardsSummary,
                                    onOpenCard = onOpenCardDetail,
                                )
                            }
                        }
                    }
                }

                DashboardSectionId.LOANS -> {
                    state.followedLoansOverview()?.let { loans ->
                        LoansSection(
                            overview = loans,
                            onViewAll = onOpenLoansSummary,
                            onOpenLoan = onOpenLoanDetail,
                        )
                    }
                }

                DashboardSectionId.TRANSACTIONS -> {
                    SectionHeader(
                        title = stringResource(R.string.dashboard_recent_title),
                        icon = MasroofIcons.recentTransactions,
                        onViewAll = onOpenAllTransactions,
                        viewAllLabel = stringResource(R.string.dashboard_view_all),
                    )

                    state.unknownCards.firstOrNull()?.let { firstUnknown ->
                        UnregisteredCardsNotice(
                            firstLast4 = firstUnknown.last4,
                            extraCount = (state.unknownCards.size - 1).coerceAtLeast(0),
                            onOpenSettings = onOpenSettings,
                        )
                    }

                    if (summary.transactionCount == 0) {
                        Text(
                            stringResource(R.string.dashboard_empty_period),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        IconTextButton(
                            onClick = onRescan,
                            enabled = !state.rescanning,
                            icon = MasroofIcons.rescan,
                            text = if (state.rescanning) {
                                stringResource(R.string.dashboard_rescanning)
                            } else {
                                stringResource(R.string.dashboard_rescan_sms)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        state.rescanStatus?.let { status ->
                            Text(
                                rescanStatusMessage(status),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        state.recentTransactions.forEach { row ->
                            TransactionPreviewRow(
                                row = row,
                                ownedCards = state.ownedCards,
                                onClick = { onOpenTransaction(row.id) },
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardErrorContent(onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = MasroofIcons.error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.dashboard_load_error))
    }
    IconTextButton(
        onClick = onRetry,
        icon = MasroofIcons.retry,
        text = stringResource(R.string.dashboard_retry),
    )
}

@Composable
private fun DashboardAppBar(
    unreadCount: Int,
    onOpenNotificationCenter: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    MasroofAppBar(
        title = stringResource(R.string.app_name),
    ) {
        ReviewNotificationIconButton(
            reviewCount = unreadCount,
            onClick = onOpenNotificationCenter,
        )
        androidx.compose.material3.IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = MasroofIcons.settings,
                contentDescription = stringResource(R.string.dashboard_open_settings),
            )
        }
    }
}

@Composable
private fun rescanStatusMessage(status: SmsRescanStatus): String =
    stringResource(
        when (status) {
            SmsRescanStatus.OK -> R.string.dashboard_rescan_ok
            SmsRescanStatus.ALREADY_UP_TO_DATE -> R.string.dashboard_rescan_already_up_to_date
            SmsRescanStatus.NEEDS_REVIEW -> R.string.dashboard_rescan_needs_review
            SmsRescanStatus.NO_MESSAGES -> R.string.dashboard_rescan_no_messages
            SmsRescanStatus.NO_BANK_SMS -> R.string.dashboard_rescan_no_bank_sms
            SmsRescanStatus.NO_TRANSACTIONS -> R.string.dashboard_rescan_no_transactions
            SmsRescanStatus.PERMISSION_DENIED -> R.string.dashboard_rescan_permission_denied
            SmsRescanStatus.FAILED -> R.string.dashboard_rescan_failed
        },
    )
