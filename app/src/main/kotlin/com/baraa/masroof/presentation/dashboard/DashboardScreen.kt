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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.common.IconLabelRow
import com.baraa.masroof.presentation.common.IconTextButton
import com.baraa.masroof.presentation.common.IconTextButtonOutlined
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MetricHighlightCard
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.common.SummaryMiniCard

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    onOpenReview: () -> Unit = {},
    onOpenAllTransactions: () -> Unit = {},
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
        onRetry = viewModel::refresh,
        onRescan = viewModel::rescanSms,
        onOpenReview = onOpenReview,
        onOpenAllTransactions = onOpenAllTransactions,
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
    onOpenReview: () -> Unit,
    onOpenAllTransactions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MasroofIcons.appLogo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        }
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = MasroofIcons.periodHint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp).padding(top = 2.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                stringResource(R.string.dashboard_period_summary_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PeriodSelector(
            label = state.periodLabel,
            isCurrentPeriod = state.isCurrentPeriod,
            onPrevious = onPrevious,
            onNext = onNext,
            onCurrent = onCurrent,
        )

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
                if (summary == null) return@Column

                MetricHighlightCard(
                    title = stringResource(R.string.dashboard_net_spending),
                    value = MoneyUiFormatter.format(summary.spendingNet),
                    icon = MasroofIcons.netSpending,
                    subtitle = if (summary.refunds.amount.signum() > 0) {
                        stringResource(
                            R.string.dashboard_gross_before_refunds,
                            MoneyUiFormatter.format(summary.spendingGross),
                        )
                    } else {
                        null
                    },
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMiniCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.dashboard_income),
                        value = MoneyUiFormatter.format(summary.income),
                        icon = MasroofIcons.income,
                    )
                    SummaryMiniCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.dashboard_refunds),
                        value = MoneyUiFormatter.format(summary.refunds),
                        icon = MasroofIcons.refunds,
                    )
                }

                MetricHighlightCard(
                    title = stringResource(R.string.dashboard_net_cash_flow),
                    value = MoneyUiFormatter.format(summary.netCashFlow),
                    icon = MasroofIcons.netCashFlow,
                )

                SectionHeader(
                    title = stringResource(R.string.dashboard_money_movement),
                    icon = MasroofIcons.moneyMovement,
                )
                MovementRow(stringResource(R.string.dashboard_external_in), summary.externalTransfersIn, MasroofIcons.externalIn)
                MovementRow(stringResource(R.string.dashboard_external_out), summary.externalTransfersOut, MasroofIcons.externalOut)
                MovementRow(stringResource(R.string.dashboard_card_payments), summary.creditCardPayments, MasroofIcons.cardPayment)
                MovementRow(stringResource(R.string.dashboard_cash_withdrawals), summary.cashWithdrawals, MasroofIcons.cashWithdrawal)
                MovementRow(stringResource(R.string.dashboard_self_transfers), summary.selfTransfers, MasroofIcons.selfTransfer)

                SectionHeader(
                    title = stringResource(R.string.dashboard_recent_title),
                    icon = MasroofIcons.recentTransactions,
                )
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
                        TransactionRow(row)
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
                }

                if (summary.excludedOtherCurrencyCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = MasroofIcons.warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            stringResource(
                                R.string.dashboard_excluded_other_currency,
                                summary.excludedOtherCurrencyCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (summary.reviewRequiredCount > 0) {
                    IconTextButton(
                        onClick = onOpenReview,
                        icon = MasroofIcons.reviewQueue,
                        text = stringResource(R.string.dashboard_review_required, summary.reviewRequiredCount),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = MasroofIcons.success,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            stringResource(R.string.dashboard_review_required, summary.reviewRequiredCount),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
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

@Composable
private fun PeriodSelector(
    label: String,
    isCurrentPeriod: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = MasroofIcons.periodPrevious,
                    contentDescription = null,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MasroofIcons.calendar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = MasroofIcons.periodNext,
                    contentDescription = null,
                )
            }
        }
        if (!isCurrentPeriod) {
            IconTextButtonOutlined(
                onClick = onCurrent,
                icon = MasroofIcons.backToCurrent,
                text = stringResource(R.string.dashboard_back_to_current),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MovementRow(title: String, value: Money, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    IconLabelRow(
        icon = icon,
        label = title,
        trailing = MoneyUiFormatter.format(value),
    )
}

@Composable
private fun rescanStatusMessage(status: SmsRescanStatus): String =
    stringResource(
        when (status) {
            SmsRescanStatus.OK -> R.string.dashboard_rescan_ok
            SmsRescanStatus.NO_MESSAGES -> R.string.dashboard_rescan_no_messages
            SmsRescanStatus.NO_BANK_SMS -> R.string.dashboard_rescan_no_bank_sms
            SmsRescanStatus.NO_TRANSACTIONS -> R.string.dashboard_rescan_no_transactions
            SmsRescanStatus.FAILED -> R.string.dashboard_rescan_failed
        },
    )
