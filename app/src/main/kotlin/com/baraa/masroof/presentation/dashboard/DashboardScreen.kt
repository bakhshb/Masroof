package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.common.ReviewNotificationIconButton
import com.baraa.masroof.presentation.common.UnregisteredCardsNotice
import com.baraa.masroof.presentation.common.ForeignCurrencyNotice
import com.baraa.masroof.presentation.common.IconTextButton
import com.baraa.masroof.presentation.common.IconTextButtonOutlined
import com.baraa.masroof.presentation.common.LongPullToRefreshBox
import com.baraa.masroof.presentation.common.MasroofAppBar
import com.baraa.masroof.presentation.common.MasroofHintBox
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofPeriodPill
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.common.SmsPermissionNotice
import com.baraa.masroof.presentation.common.SmsRescanStatusNotice
import com.baraa.masroof.presentation.common.SummaryMiniCard

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    notificationUnreadCount: Int = 0,
    onOpenNotificationCenter: () -> Unit = {},
    onOpenAllTransactions: () -> Unit = {},
    onOpenTransaction: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
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
        onRequestSmsPermission = onRequestSmsPermission,
        onOpenAppSettings = onOpenAppSettings,
        onDismissRescanStatus = viewModel::clearRescanStatus,
    )
}

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
    onRequestSmsPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismissRescanStatus: () -> Unit,
) {
    val isPullRefreshing = (state.loading && state.summary != null) || state.rescanning
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
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
        MasroofHintBox(
            text = stringResource(R.string.dashboard_period_summary_hint),
        )

        MasroofPeriodPill(
            label = state.periodLabel,
            onPrevious = onPrevious,
            onNext = onNext,
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

            else -> {
                val summary = state.summary
                val currentAccount = state.currentAccount
                if (summary != null && currentAccount != null) {
                val accountBadge = formatOwnedAccountsBadge(state.ownedAccounts, LocalContext.current)

                CurrentAccountSection(
                    summary = currentAccount,
                    accountBadge = accountBadge,
                    ownedAccountCount = state.ownedAccounts.size.coerceAtLeast(1),
                )

                state.creditCards?.let { creditCards ->
                    val ownedLast4s = state.ownedCards.map { it.last4 }.toSet()
                    val followedOverview = creditCards.followedOnly(ownedLast4s)
                    if (followedOverview.hasContent) {
                        CreditCardsSection(
                            overview = followedOverview,
                            zoneId = java.time.ZoneId.systemDefault(),
                        )
                    }
                }

                if (summary.refunds.amount.signum() > 0) {
                    SummaryMiniCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = stringResource(R.string.dashboard_refunds),
                        value = formatLocalizedMoney(summary.refunds),
                        icon = MasroofIcons.refunds,
                    )
                }

                SectionHeader(
                    title = stringResource(R.string.dashboard_recent_title),
                    icon = MasroofIcons.recentTransactions,
                )

                state.unknownCards.firstOrNull()?.let { firstUnknown ->
                    UnregisteredCardsNotice(
                        firstLast4 = firstUnknown.last4,
                        extraCount = (state.unknownCards.size - 1).coerceAtLeast(0),
                        onOpenSettings = onOpenSettings,
                    )
                }
                if (summary.transactionCount == 0) {
                    Text(stringResource(R.string.dashboard_empty_period))
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
                        TransactionRow(row, onClick = { onOpenTransaction(row.id) })
                        Spacer(Modifier.height(4.dp))
                    }
                    IconTextButtonOutlined(
                        onClick = onOpenAllTransactions,
                        icon = MasroofIcons.recentTransactions,
                        text = stringResource(
                            R.string.dashboard_view_all_transactions,
                            summary.transactionCount,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.smsPermissionGranted) {
                        IconTextButtonOutlined(
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
                    }
                }

                if (summary.excludedOtherCurrencyCount > 0) {
                    ForeignCurrencyNotice(excludedCount = summary.excludedOtherCurrencyCount)
                }

                if (state.error != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = MasroofIcons.error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.dashboard_load_error))
                    }
                    IconTextButtonOutlined(
                        onClick = onRetry,
                        icon = MasroofIcons.retry,
                        text = stringResource(R.string.dashboard_retry),
                    )
                }
                }
            }
        }
            }
        }
    }
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
        IconButton(onClick = onOpenSettings) {
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
            SmsRescanStatus.NO_MESSAGES -> R.string.dashboard_rescan_no_messages
            SmsRescanStatus.NO_BANK_SMS -> R.string.dashboard_rescan_no_bank_sms
            SmsRescanStatus.NO_TRANSACTIONS -> R.string.dashboard_rescan_no_transactions
            SmsRescanStatus.PERMISSION_DENIED -> R.string.dashboard_rescan_permission_denied
            SmsRescanStatus.FAILED -> R.string.dashboard_rescan_failed
        },
    )
