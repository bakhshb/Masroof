package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.DailySpendingTrend
import com.baraa.masroof.application.dashboard.MerchantSpendingOverview
import com.baraa.masroof.application.dashboard.MerchantSpendingRow
import com.baraa.masroof.presentation.common.MasroofBarChart
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofHorizontalBarStyle
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofLineChart
import com.baraa.masroof.presentation.common.MasroofRankedBarRow
import com.baraa.masroof.presentation.common.MasroofSectionHeader
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofSpacing
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@Composable
fun DashboardAnalysisSection(
    merchantOverview: MerchantSpendingOverview,
    dailySpendingTrend: DailySpendingTrend,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap),
    ) {
        MasroofSectionHeader(
            title = stringResource(R.string.dashboard_analysis_title),
            icon = MasroofIcons.merchant,
        )
        MerchantSpendingSection(
            overview = merchantOverview,
            onViewAll = onViewAll,
        )
        DailySpendingTrendSection(trend = dailySpendingTrend)
    }
}

@Composable
private fun MerchantSpendingSection(
    overview: MerchantSpendingOverview,
    onViewAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
        MasroofSectionHeader(
            title = stringResource(R.string.dashboard_top_merchants_title),
            icon = MasroofIcons.merchant,
            onViewAll = if (overview.hasContent) onViewAll else null,
            viewAllLabel = stringResource(R.string.dashboard_merchants_view_all),
        )
        if (!overview.hasContent) {
            Text(
                stringResource(R.string.dashboard_merchants_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MerchantSpendingChart(rows = overview.topForDashboard())
        }
    }
}

@Composable
private fun DailySpendingTrendSection(trend: DailySpendingTrend) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
    val firstDate = trend.points.first().date
    val lastDate = trend.points.last().date
    val middleDate = trend.points[trend.points.lastIndex / 2].date

    Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
        MasroofSectionHeader(
            title = stringResource(R.string.dashboard_daily_spending_title),
            icon = MasroofIcons.netSpending,
        )
        MasroofCard {
            Text(
                stringResource(
                    R.string.dashboard_average_daily_spending,
                    formatLocalizedMoney(trend.averageDailySpending),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MasroofLineChart(
                values = trend.points.map { it.spending.amount },
                referenceValue = trend.averageDailySpending.amount,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    dateFormatter.format(firstDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    dateFormatter.format(middleDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    dateFormatter.format(lastDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MerchantSpendingChart(
    rows: List<MerchantSpendingRow>,
    modifier: Modifier = Modifier,
    onMerchantClick: ((MerchantSpendingRow) -> Unit)? = null,
) {
    val maxAmount = rows.maxOfOrNull { it.totalSpent.amount.max(BigDecimal.ZERO) } ?: BigDecimal.ZERO
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MasroofSpacing.listItemGap),
    ) {
        rows.forEach { row ->
            MasroofRankedBarRow(
                title = row.displayName,
                value = formatLocalizedMoney(row.totalSpent),
                progress = MasroofBarChart.progress(row.totalSpent.amount, maxAmount),
                subtitle = stringResource(
                    R.string.dashboard_merchants_transaction_count,
                    row.purchaseTransactionCount,
                ),
                style = MasroofHorizontalBarStyle.Outflow,
                onClick = onMerchantClick?.let { click -> { click(row) } },
            )
        }
    }
}
