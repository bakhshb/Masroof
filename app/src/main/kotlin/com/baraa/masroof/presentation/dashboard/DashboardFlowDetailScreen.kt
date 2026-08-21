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
    val (title, totalAmount, totalColor, totalLabelRes) = when (mode) {
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
    val formattedTotal = formatLocalizedMoney(totalAmount)

    MasroofSecondaryScaffold(
        title = stringResource(title),
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
                stringResource(R.string.dashboard_flow_detail_period, periodRangeLabel),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )

            MasroofCard {
                Text(
                    stringResource(totalLabelRes, formattedTotal),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = totalColor,
                    ),
                )
            }

            when (mode) {
                DashboardFlowDetailMode.Income -> IncomeBreakdown(summary)
                DashboardFlowDetailMode.Expense -> ExpenseBreakdown(summary)
            }

            FlowDetailFooter(
                label = stringResource(totalLabelRes, formattedTotal),
                amountColor = totalColor,
            )
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
private fun IncomeBreakdown(summary: CurrentAccountSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun ExpenseBreakdown(summary: CurrentAccountSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun FlowDetailFooter(
    label: String,
    amountColor: androidx.compose.ui.graphics.Color,
) {
    Spacer(Modifier.height(4.dp))
    MasroofCard {
        Text(
            label,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = amountColor,
        )
    }
}
