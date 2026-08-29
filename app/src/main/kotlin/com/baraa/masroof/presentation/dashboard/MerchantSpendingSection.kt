package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.MerchantSpendingOverview
import com.baraa.masroof.application.dashboard.MerchantSpendingRow
import com.baraa.masroof.presentation.common.MasroofBarChart
import com.baraa.masroof.presentation.common.MasroofHorizontalBarStyle
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofRankedBarRow
import com.baraa.masroof.presentation.common.MasroofSectionHeader
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofSpacing
import java.math.BigDecimal

@Composable
fun MerchantSpendingSection(
    overview: MerchantSpendingOverview,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap),
    ) {
        MasroofSectionHeader(
            title = stringResource(R.string.dashboard_merchants_title),
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
