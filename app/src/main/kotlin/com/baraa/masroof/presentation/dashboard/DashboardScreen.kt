package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.baraa.masroof.domain.model.FinancialTransactionType

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    onOpenReview: () -> Unit = {},
) {
    // Load only when dashboard becomes visible (e.g. after onboarding HOME).
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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

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
                Text(stringResource(R.string.dashboard_load_error))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.dashboard_retry))
                }
            }

            else -> {
                val summary = state.summary
                if (summary == null) return@Column

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.dashboard_net_spending), style = MaterialTheme.typography.titleMedium)
                        Text(
                            MoneyUiFormatter.format(summary.spendingNet),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        if (summary.refunds.amount.signum() > 0) {
                            Text(
                                stringResource(
                                    R.string.dashboard_gross_before_refunds,
                                    MoneyUiFormatter.format(summary.spendingGross),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMiniCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.dashboard_income),
                        value = summary.income,
                    )
                    SummaryMiniCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.dashboard_refunds),
                        value = summary.refunds,
                    )
                }

                Text(stringResource(R.string.dashboard_money_movement), style = MaterialTheme.typography.titleMedium)
                MovementRow(stringResource(R.string.dashboard_external_in), summary.externalTransfersIn)
                MovementRow(stringResource(R.string.dashboard_external_out), summary.externalTransfersOut)
                MovementRow(stringResource(R.string.dashboard_card_payments), summary.creditCardPayments)
                MovementRow(stringResource(R.string.dashboard_cash_withdrawals), summary.cashWithdrawals)
                MovementRow(stringResource(R.string.dashboard_self_transfers), summary.selfTransfers)

                Text(stringResource(R.string.dashboard_recent_title), style = MaterialTheme.typography.titleMedium)
                if (summary.transactionCount == 0) {
                    Text(stringResource(R.string.dashboard_empty_period))
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onRescan,
                        enabled = !state.rescanning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.rescanning) {
                                stringResource(R.string.dashboard_rescanning)
                            } else {
                                stringResource(R.string.dashboard_rescan_sms)
                            },
                        )
                    }
                    state.rescanStatus?.let { status ->
                        Text(
                            rescanStatusMessage(status),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    state.recentTransactions.forEach { row ->
                        RecentRow(row)
                    }
                }

                if (summary.excludedOtherCurrencyCount > 0) {
                    Text(
                        stringResource(
                            R.string.dashboard_excluded_other_currency,
                            summary.excludedOtherCurrencyCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (summary.reviewRequiredCount > 0) {
                    Button(
                        onClick = onOpenReview,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.dashboard_review_required, summary.reviewRequiredCount))
                    }
                } else {
                    Text(
                        stringResource(R.string.dashboard_review_required, summary.reviewRequiredCount),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                if (state.error != null) {
                    Text(stringResource(R.string.dashboard_load_error))
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.dashboard_retry))
                    }
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
            TextButton(onClick = onPrevious) { Text("‹") }
            Text(label, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onNext) { Text("›") }
        }
        if (!isCurrentPeriod) {
            TextButton(onClick = onCurrent, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.dashboard_back_to_current))
            }
        }
    }
}

@Composable
private fun SummaryMiniCard(modifier: Modifier, title: String, value: Money) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(MoneyUiFormatter.format(value), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MovementRow(title: String, value: Money) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title)
        Text(MoneyUiFormatter.format(value))
    }
}

@Composable
private fun RecentRow(row: TransactionPreviewUi) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.title ?: typeLabel(row.type), style = MaterialTheme.typography.titleSmall)
                Text(row.amountLabel)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(typeLabel(row.type), style = MaterialTheme.typography.bodySmall)
                Text(row.dateLabel, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                when (row.direction) {
                    TransactionDirectionUi.OUTWARD -> stringResource(R.string.dashboard_direction_out)
                    TransactionDirectionUi.INWARD -> stringResource(R.string.dashboard_direction_in)
                    TransactionDirectionUi.NEUTRAL -> stringResource(R.string.dashboard_direction_neutral)
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
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

@Composable
private fun typeLabel(type: FinancialTransactionType): String =
    stringResource(
        when (type) {
            FinancialTransactionType.EXPENSE -> R.string.txn_type_expense
            FinancialTransactionType.INCOME -> R.string.txn_type_income
            FinancialTransactionType.SELF_TRANSFER -> R.string.txn_type_self_transfer
            FinancialTransactionType.EXTERNAL_TRANSFER_IN -> R.string.txn_type_external_in
            FinancialTransactionType.EXTERNAL_TRANSFER_OUT -> R.string.txn_type_external_out
            FinancialTransactionType.CREDIT_CARD_PAYMENT -> R.string.txn_type_card_payment
            FinancialTransactionType.REFUND -> R.string.txn_type_refund
            FinancialTransactionType.CASH_WITHDRAWAL -> R.string.txn_type_cash_withdrawal
            FinancialTransactionType.FEE -> R.string.txn_type_fee
            FinancialTransactionType.ADJUSTMENT -> R.string.txn_type_adjustment
            FinancialTransactionType.UNKNOWN -> R.string.txn_type_unknown
        },
    )
