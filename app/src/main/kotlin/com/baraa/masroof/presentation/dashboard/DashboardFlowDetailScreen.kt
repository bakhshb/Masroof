package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
    periodRangeLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    val presentation = when (mode) {
        DashboardFlowDetailMode.Expense -> FlowDetailPresentation(
            titleRes = R.string.dashboard_expense_details_title,
            total = summary.totalOutflow,
            totalColor = extended.outflow,
            totalLabelRes = R.string.dashboard_flow_detail_expense_total,
        )
        DashboardFlowDetailMode.Income -> FlowDetailPresentation(
            titleRes = R.string.dashboard_income_details_title,
            total = summary.totalInflow,
            totalColor = extended.inflow,
            totalLabelRes = R.string.dashboard_flow_detail_income_total,
        )
    }
    val formattedTotal = formatLocalizedMoney(presentation.total)

    MasroofSecondaryScaffold(
        title = stringResource(presentation.titleRes),
        onBack = onBack,
        backContentDescription = stringResource(R.string.dashboard_flow_detail_back),
        modifier = modifier,
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowDetailHeroCard(
                periodRangeLabel = periodRangeLabel,
                totalLabel = stringResource(presentation.totalLabelRes, formattedTotal),
                totalColor = presentation.totalColor,
            )

            MasroofCard {
                Text(
                    stringResource(R.string.dashboard_flow_detail_breakdown_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outline,
                )
                when (mode) {
                    DashboardFlowDetailMode.Income -> DashboardIncomeBreakdown(summary)
                    DashboardFlowDetailMode.Expense -> DashboardExpenseBreakdown(summary)
                }
            }
        }
    }
}

private data class FlowDetailPresentation(
    val titleRes: Int,
    val total: Money,
    val totalColor: androidx.compose.ui.graphics.Color,
    val totalLabelRes: Int,
)

@Composable
private fun FlowDetailHeroCard(
    periodRangeLabel: String,
    totalLabel: String,
    totalColor: androidx.compose.ui.graphics.Color,
) {
    MasroofCard {
        Text(
            stringResource(R.string.dashboard_flow_detail_period, periodRangeLabel),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            totalLabel,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = totalColor,
            ),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun DashboardFlowBreakdownCard(
    summary: CurrentAccountSummary,
    modifier: Modifier = Modifier,
) {
    MasroofCard(modifier = modifier) {
        Text(
            stringResource(R.string.dashboard_flow_detail_breakdown_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            stringResource(R.string.dashboard_income_details_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DashboardIncomeBreakdown(
            summary = summary,
            modifier = Modifier.padding(top = 6.dp),
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            stringResource(R.string.dashboard_expense_details_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DashboardExpenseBreakdown(
            summary = summary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun DashboardIncomeBreakdown(
    summary: CurrentAccountSummary,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowDetailRow(
            label = stringResource(R.string.dashboard_salary),
            amount = summary.salary,
            direction = TransactionDirectionUi.INCOME,
        )
        FlowDetailRow(
            label = stringResource(R.string.dashboard_external_in_short),
            amount = summary.externalTransfersIn,
            direction = TransactionDirectionUi.TRANSFER_IN,
        )
        if (summary.otherIncome.amount.signum() > 0) {
            FlowDetailRow(
                label = stringResource(R.string.dashboard_other_income),
                amount = summary.otherIncome,
                direction = TransactionDirectionUi.INCOME,
            )
        }
    }
}

@Composable
private fun DashboardExpenseBreakdown(
    summary: CurrentAccountSummary,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowDetailRow(
            label = stringResource(R.string.dashboard_external_out_short),
            amount = summary.externalTransfersOut,
            direction = TransactionDirectionUi.OUTWARD,
        )
        FlowDetailRow(
            label = stringResource(R.string.dashboard_credit_card_payment),
            amount = summary.creditCardPayments,
            direction = TransactionDirectionUi.OUTWARD,
        )
        FlowDetailRow(
            label = stringResource(R.string.dashboard_cash_withdrawals),
            amount = summary.cashWithdrawals,
            direction = TransactionDirectionUi.OUTWARD,
        )
        FlowDetailRow(
            label = stringResource(R.string.dashboard_bill_payments),
            amount = summary.billPayments,
            direction = TransactionDirectionUi.OUTWARD,
        )
        FlowDetailRow(
            label = stringResource(R.string.dashboard_pos_purchases_short),
            amount = summary.posPurchases,
            direction = TransactionDirectionUi.OUTWARD,
        )
        FlowDetailRow(
            label = stringResource(R.string.dashboard_fees_short),
            amount = summary.fees,
            direction = TransactionDirectionUi.OUTWARD,
        )
    }
}

@Composable
private fun FlowDetailRow(
    label: String,
    amount: Money,
    direction: TransactionDirectionUi,
) {
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
