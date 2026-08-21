package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CurrentAccountSummary
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofMoneyRow
import com.baraa.masroof.presentation.common.MasroofMoneyRowStyle
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

enum class DashboardFlowDetailMode {
    Expense,
    Income,
}

@Composable
fun DashboardFlowDetailScreen(
    mode: DashboardFlowDetailMode,
    summary: CurrentAccountSummary,
    periodLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    val (title, total, totalColor) = when (mode) {
        DashboardFlowDetailMode.Expense -> Triple(
            stringResource(R.string.dashboard_expense_details_title),
            formatLocalizedMoney(summary.totalOutflow),
            extended.outflow,
        )
        DashboardFlowDetailMode.Income -> Triple(
            stringResource(R.string.dashboard_income_details_title),
            formatLocalizedMoney(summary.totalInflow),
            extended.inflow,
        )
    }

    MasroofSecondaryScaffold(
        title = title,
        onBack = onBack,
        backContentDescription = stringResource(R.string.dashboard_flow_detail_back),
        modifier = modifier,
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                periodLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            MasroofCard {
                Text(
                    stringResource(R.string.dashboard_flow_detail_total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    total,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = totalColor,
                    ),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            when (mode) {
                DashboardFlowDetailMode.Income -> IncomeBreakdown(summary)
                DashboardFlowDetailMode.Expense -> ExpenseBreakdown(summary)
            }
        }
    }
}

@Composable
private fun IncomeBreakdown(summary: CurrentAccountSummary) {
    MasroofSectionLabel(stringResource(R.string.dashboard_current_account_inflows))
    FlowDetailRow(stringResource(R.string.dashboard_salary), summary.salary, TransactionDirectionUi.INCOME)
    FlowDetailRow(
        stringResource(R.string.dashboard_external_in_short),
        summary.externalTransfersIn,
        TransactionDirectionUi.TRANSFER_IN,
    )
    if (summary.otherIncome.amount.signum() > 0) {
        FlowDetailRow(
            stringResource(R.string.dashboard_other_income),
            summary.otherIncome,
            TransactionDirectionUi.INCOME,
        )
    }
    FlowDetailTotal(
        label = stringResource(R.string.dashboard_total_inflow),
        amount = summary.totalInflow,
        amountColor = MasroofThemeExtras.extendedColors.inflow,
    )
}

@Composable
private fun ExpenseBreakdown(summary: CurrentAccountSummary) {
    MasroofSectionLabel(stringResource(R.string.dashboard_current_account_outflows))
    FlowDetailRow(
        stringResource(R.string.dashboard_external_out_short),
        summary.externalTransfersOut,
        TransactionDirectionUi.OUTWARD,
    )
    FlowDetailRow(
        stringResource(R.string.dashboard_credit_card_payment),
        summary.creditCardPayments,
        TransactionDirectionUi.OUTWARD,
    )
    FlowDetailRow(
        stringResource(R.string.dashboard_cash_withdrawals),
        summary.cashWithdrawals,
        TransactionDirectionUi.OUTWARD,
    )
    FlowDetailRow(
        stringResource(R.string.dashboard_bill_payments),
        summary.billPayments,
        TransactionDirectionUi.OUTWARD,
    )
    FlowDetailRow(
        stringResource(R.string.dashboard_pos_purchases_short),
        summary.posPurchases,
        TransactionDirectionUi.OUTWARD,
    )
    FlowDetailRow(
        stringResource(R.string.dashboard_fees_short),
        summary.fees,
        TransactionDirectionUi.OUTWARD,
    )
    FlowDetailTotal(
        label = stringResource(R.string.dashboard_total_spent),
        amount = summary.totalOutflow,
        amountColor = MasroofThemeExtras.extendedColors.outflow,
    )

    if (
        summary.selfTransfersIn.amount.signum() > 0 ||
        summary.selfTransfersOut.amount.signum() > 0
    ) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        MasroofSectionLabel(stringResource(R.string.dashboard_self_transfers))
        FlowDetailRow(
            stringResource(R.string.dashboard_self_transfer_in),
            summary.selfTransfersIn,
            TransactionDirectionUi.NEUTRAL,
        )
        FlowDetailRow(
            stringResource(R.string.dashboard_self_transfer_out),
            summary.selfTransfersOut,
            TransactionDirectionUi.NEUTRAL,
        )
        Text(
            stringResource(R.string.dashboard_self_transfers_neutral_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MasroofSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun FlowDetailRow(
    label: String,
    amount: Money,
    direction: TransactionDirectionUi,
) {
    if (amount.amount.signum() == 0) return
    MasroofMoneyRow(
        label = label,
        value = formatLocalizedMoney(amount),
        style = when (direction) {
            TransactionDirectionUi.INCOME,
            TransactionDirectionUi.INWARD,
            TransactionDirectionUi.TRANSFER_IN,
            -> MasroofMoneyRowStyle.Inflow
            TransactionDirectionUi.OUTWARD -> MasroofMoneyRowStyle.Outflow
            TransactionDirectionUi.NEUTRAL -> MasroofMoneyRowStyle.Neutral
        },
        leadingIcon = TransactionDirectionPresentation.icon(direction),
    )
}

@Composable
private fun FlowDetailTotal(
    label: String,
    amount: Money,
    amountColor: androidx.compose.ui.graphics.Color,
) {
    MasroofCard {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                formatLocalizedMoney(amount),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = amountColor,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}
