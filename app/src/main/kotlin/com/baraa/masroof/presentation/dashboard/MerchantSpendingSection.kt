package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.MerchantSpendingOverview
import com.baraa.masroof.application.dashboard.MerchantSpendingRow
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSectionHeader
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofShapes
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
            MerchantSpendingChartRow(
                row = row,
                maxAmount = maxAmount,
                onClick = onMerchantClick?.let { click -> { click(row) } },
            )
        }
    }
}

@Composable
private fun MerchantSpendingChartRow(
    row: MerchantSpendingRow,
    maxAmount: BigDecimal,
    onClick: (() -> Unit)?,
) {
    val ratio = if (maxAmount.signum() > 0) {
        row.totalSpent.amount.max(BigDecimal.ZERO).divide(maxAmount, 4, java.math.RoundingMode.HALF_UP)
            .toFloat()
    } else {
        0f
    }
    MasroofCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    formatLocalizedMoney(row.totalSpent),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MasroofSpacing.accentBarHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MasroofShapes.small),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio.coerceIn(0f, 1f))
                        .height(MasroofSpacing.accentBarHeight)
                        .background(MaterialTheme.colorScheme.primary, MasroofShapes.small),
                )
            }
            Text(
                stringResource(
                    R.string.dashboard_merchants_transaction_count,
                    row.purchaseTransactionCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
