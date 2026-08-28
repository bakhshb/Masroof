package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.theme.MasroofShapes
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.math.BigDecimal

internal val dashboardSummaryMetricTileHeight = DashboardSpacing.metricTileHeight

@Composable
fun DashboardSummaryPrimaryMetric(
    title: String,
    amount: String,
    tone: DashboardMetricTone,
    modifier: Modifier = Modifier,
    hint: String? = null,
    signedAmount: BigDecimal? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DashboardSpacing.cardInnerGap),
    ) {
        Text(
            title,
            style = DashboardTextStyles.breakdownLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DashboardAmountText(
            amount = amount,
            role = DashboardAmountRole.Hero,
            color = resolveMetricToneColor(tone, signedAmount),
        )
        hint?.let {
            Text(
                it,
                style = DashboardTextStyles.hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DashboardSummaryMetricGrid(
    metrics: List<DashboardSummaryMetricItem>,
    modifier: Modifier = Modifier,
) {
    if (metrics.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DashboardSpacing.cardInnerGap),
    ) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.cardInnerGap),
            ) {
                rowMetrics.forEach { metric ->
                    DashboardSummaryMetricTile(
                        label = metric.title,
                        value = metric.amount,
                        valueColor = resolveMetricToneColor(metric.tone, metric.signedAmount),
                        modifier = Modifier
                            .weight(1f)
                            .height(dashboardSummaryMetricTileHeight),
                    )
                }
                if (rowMetrics.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun DashboardSummaryMetricTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        shape = MasroofShapes.small,
        color = extended.miniBackground,
        border = BorderStroke(1.dp, extended.cardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(DashboardSpacing.cardInnerGap),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = DashboardTextStyles.metricTileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                value,
                style = DashboardTextStyles.metricTileAmount,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun DashboardSummaryBreakdownHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        modifier = modifier,
        style = DashboardTextStyles.breakdownLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun DashboardSummaryTotalRow(
    label: String,
    amount: Money,
    modifier: Modifier = Modifier,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DashboardTextStyles.breakdownLabel, color = MaterialTheme.colorScheme.onSurface)
        Text(
            com.baraa.masroof.presentation.locale.formatLocalizedMoney(amount),
            style = DashboardTextStyles.breakdownTotal,
            color = amountColor,
        )
    }
}

@Composable
fun DashboardSummaryCardDivider(
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = DashboardSpacing.cardInnerGap),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
