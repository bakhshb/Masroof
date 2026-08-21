package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CurrentAccountFlowDetailGrouping
import com.baraa.masroof.application.dashboard.AccountFlowTotalsMode
import com.baraa.masroof.application.dashboard.CurrentAccountSummary
import com.baraa.masroof.application.dashboard.flowInflow
import com.baraa.masroof.application.dashboard.flowOutflow
import com.baraa.masroof.application.dashboard.FlowExpenseCategory
import com.baraa.masroof.application.dashboard.FlowIncomeCategory
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.common.MasroofMoneyRow
import com.baraa.masroof.presentation.common.MasroofMoneyRowStyle
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
import com.baraa.masroof.presentation.common.MasroofSectionTitle
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
    transactions: List<TransactionPreviewUi>,
    grouping: CurrentAccountFlowDetailGrouping,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    val presentation = when (mode) {
        DashboardFlowDetailMode.Expense -> FlowDetailPresentation(
            titleRes = R.string.dashboard_expense_details_title,
            total = summary.flowOutflow(AccountFlowTotalsMode.AGGREGATE_NET),
            totalColor = extended.outflow,
            totalLabelRes = R.string.dashboard_flow_detail_expense_total,
        )
        DashboardFlowDetailMode.Income -> FlowDetailPresentation(
            titleRes = R.string.dashboard_income_details_title,
            total = summary.flowInflow(AccountFlowTotalsMode.AGGREGATE_NET),
            totalColor = extended.inflow,
            totalLabelRes = R.string.dashboard_flow_detail_income_total,
        )
    }
    val formattedTotal = formatLocalizedMoney(presentation.total)
    val previewsById = remember(transactions) { transactions.associateBy { it.id } }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FlowDetailHeroCard(
                periodRangeLabel = periodRangeLabel,
                totalLabel = stringResource(presentation.totalLabelRes, formattedTotal),
                totalColor = presentation.totalColor,
            )

            when (mode) {
                DashboardFlowDetailMode.Expense -> {
                    FlowDetailExpenseSummarySection(summary = summary)
                    FlowDetailTransactionsSection(
                        transactions = flowExpenseTransactions(grouping, previewsById),
                        onOpenTransaction = onOpenTransaction,
                    )
                }
                DashboardFlowDetailMode.Income -> {
                    FlowDetailIncomeSummarySection(summary = summary)
                    FlowDetailTransactionsSection(
                        transactions = flowIncomeTransactions(grouping, previewsById),
                        onOpenTransaction = onOpenTransaction,
                    )
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        )
    }
}

@Composable
private fun FlowDetailExpenseSummarySection(summary: CurrentAccountSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        expenseSummaryRows(summary).forEach { row ->
            FlowDetailSummaryRow(
                label = stringResource(row.labelRes),
                amount = row.amount,
                direction = TransactionDirectionUi.OUTWARD,
            )
        }
    }
}

@Composable
private fun FlowDetailIncomeSummarySection(summary: CurrentAccountSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        incomeSummaryRows(summary).forEach { row ->
            FlowDetailSummaryRow(
                label = stringResource(row.labelRes),
                amount = row.amount,
                direction = TransactionDirectionUi.INCOME,
            )
        }
    }
}

@Composable
private fun FlowDetailTransactionsSection(
    transactions: List<TransactionPreviewUi>,
    onOpenTransaction: (String) -> Unit,
) {
    if (transactions.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MasroofSectionTitle(
            title = stringResource(R.string.dashboard_flow_detail_transactions_title),
        )
        transactions.forEach { row ->
            DashboardRecentTransactionRow(
                row = row,
                onClick = { onOpenTransaction(row.id) },
            )
        }
    }
}

private fun flowExpenseTransactions(
    grouping: CurrentAccountFlowDetailGrouping,
    previewsById: Map<String, TransactionPreviewUi>,
): List<TransactionPreviewUi> =
    grouping.expense.values
        .flatten()
        .mapNotNull { previewsById[it.id] }
        .sortedByDateDesc()

private fun flowIncomeTransactions(
    grouping: CurrentAccountFlowDetailGrouping,
    previewsById: Map<String, TransactionPreviewUi>,
): List<TransactionPreviewUi> =
    grouping.income.values
        .flatten()
        .mapNotNull { previewsById[it.id] }
        .sortedByDateDesc()

private fun List<TransactionPreviewUi>.sortedByDateDesc(): List<TransactionPreviewUi> =
    sortedWith(
        compareByDescending<TransactionPreviewUi> { it.localDate }
            .thenByDescending { it.id },
    )

@Composable
fun DashboardFlowBreakdownCard(
    summary: CurrentAccountSummary,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.dashboard_flow_detail_breakdown_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.dashboard_income_details_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowDetailIncomeSummarySection(summary = summary)
        Text(
            stringResource(R.string.dashboard_expense_details_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        FlowDetailExpenseSummarySection(summary = summary)
    }
}

@Composable
private fun FlowDetailSummaryRow(
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

private data class FlowSummaryRow(
    val labelRes: Int,
    val amount: Money,
)

private fun expenseSummaryRows(summary: CurrentAccountSummary): List<FlowSummaryRow> {
    val rows = CurrentAccountFlowDetailGrouping.EXPENSE_DISPLAY_ORDER.mapNotNull { category ->
        val amount = expenseAmount(summary, category)
        if (amount.amount.signum() <= 0) null
        else FlowSummaryRow(labelRes = expenseCategoryLabelRes(category), amount = amount)
    }.toMutableList()
    if (summary.selfTransfersOut.amount.signum() > 0) {
        rows += FlowSummaryRow(
            labelRes = R.string.dashboard_self_transfer_out,
            amount = summary.outflow.selfTransfersOut,
        )
    }
    return rows
}

private fun incomeSummaryRows(summary: CurrentAccountSummary): List<FlowSummaryRow> {
    val rows = CurrentAccountFlowDetailGrouping.INCOME_DISPLAY_ORDER.mapNotNull { category ->
        val amount = incomeAmount(summary, category)
        if (amount.amount.signum() <= 0) null
        else FlowSummaryRow(labelRes = incomeCategoryLabelRes(category), amount = amount)
    }.toMutableList()
    if (summary.selfTransfersIn.amount.signum() > 0) {
        rows += FlowSummaryRow(
            labelRes = R.string.dashboard_self_transfer_in,
            amount = summary.inflow.selfTransfersIn,
        )
    }
    return rows
}

private fun expenseAmount(summary: CurrentAccountSummary, category: FlowExpenseCategory): Money =
    when (category) {
        FlowExpenseCategory.EXTERNAL_TRANSFER_OUT -> summary.outflow.externalTransfersOut
        FlowExpenseCategory.CREDIT_CARD_PAYMENT -> summary.outflow.creditCardPayments
        FlowExpenseCategory.CASH_WITHDRAWAL -> summary.outflow.cashWithdrawals
        FlowExpenseCategory.BILL_PAYMENT -> summary.outflow.billPayments
        FlowExpenseCategory.POS_PURCHASE -> summary.outflow.posPurchases
        FlowExpenseCategory.FEE -> summary.outflow.fees
    }

private fun incomeAmount(summary: CurrentAccountSummary, category: FlowIncomeCategory): Money =
    when (category) {
        FlowIncomeCategory.SALARY -> summary.inflow.salary
        FlowIncomeCategory.EXTERNAL_TRANSFER_IN -> summary.inflow.externalTransfersIn
        FlowIncomeCategory.OTHER_INCOME -> summary.inflow.otherIncome
    }

private fun expenseCategoryLabelRes(category: FlowExpenseCategory): Int =
    when (category) {
        FlowExpenseCategory.EXTERNAL_TRANSFER_OUT -> R.string.dashboard_external_out_short
        FlowExpenseCategory.CREDIT_CARD_PAYMENT -> R.string.dashboard_credit_card_payment
        FlowExpenseCategory.CASH_WITHDRAWAL -> R.string.dashboard_cash_withdrawals
        FlowExpenseCategory.BILL_PAYMENT -> R.string.dashboard_bill_payments
        FlowExpenseCategory.POS_PURCHASE -> R.string.dashboard_pos_purchases_short
        FlowExpenseCategory.FEE -> R.string.dashboard_fees_short
    }

private fun incomeCategoryLabelRes(category: FlowIncomeCategory): Int =
    when (category) {
        FlowIncomeCategory.SALARY -> R.string.dashboard_salary
        FlowIncomeCategory.EXTERNAL_TRANSFER_IN -> R.string.dashboard_external_in_short
        FlowIncomeCategory.OTHER_INCOME -> R.string.dashboard_other_income
    }
