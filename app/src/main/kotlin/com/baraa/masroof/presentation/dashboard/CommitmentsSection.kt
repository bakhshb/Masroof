package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CommitmentDashboardRow
import com.baraa.masroof.application.dashboard.CommitmentPaymentStatus
import com.baraa.masroof.application.dashboard.CommitmentDashboardSource
import com.baraa.masroof.application.dashboard.CommitmentsOverview
import com.baraa.masroof.presentation.common.MasroofAmountRole
import com.baraa.masroof.presentation.common.MasroofAmountText
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofDonutChart
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSectionHeader
import com.baraa.masroof.presentation.common.commitmentDonutPaidFraction
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.time.format.DateTimeFormatter

@Composable
fun CommitmentsSection(
    overview: CommitmentsOverview,
    modifier: Modifier = Modifier,
) {
    if (!overview.hasContent) return

    val extended = MasroofThemeExtras.extendedColors
    val paidFraction = commitmentDonutPaidFraction(overview.total.amount, overview.paid.amount)
    val donutSize = MasroofSpacing.donutChartSizeLarge

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
        MasroofSectionHeader(
            title = stringResource(R.string.dashboard_commitments_title),
            icon = MasroofIcons.calendar,
        )

        MasroofCard {
            Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.listItemGap)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(donutSize),
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        CommitmentSummaryMetric(
                            label = stringResource(R.string.dashboard_commitments_total),
                            value = formatLocalizedMoney(overview.total),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                        )
                        CommitmentSummaryMetric(
                            label = stringResource(R.string.dashboard_commitments_paid),
                            value = formatLocalizedMoney(overview.paid),
                            valueColor = extended.inflow,
                        )
                    }
                    MasroofDonutChart(
                        paidFraction = paidFraction,
                        centerTitle = "",
                        centerValue = "",
                        showCenterContent = false,
                        size = donutSize,
                    )
                }

                DashboardSummaryTotalRow(
                    label = stringResource(R.string.dashboard_commitments_paid),
                    amount = overview.paid,
                    amountColor = extended.inflow,
                )

                DashboardSummaryTotalRow(
                    label = stringResource(R.string.dashboard_commitments_remaining),
                    amount = overview.remaining,
                    amountColor = extended.outflow,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MasroofSpacing.sectionHeaderGap),
                    verticalArrangement = Arrangement.spacedBy(MasroofSpacing.listItemGap),
                ) {
                    overview.rows.forEach { row ->
                        CommitmentDashboardRowItem(row = row)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitmentSummaryMetric(
    label: String,
    value: String,
    valueColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
        )
    }
}

@Composable
private fun CommitmentDashboardRowItem(
    row: CommitmentDashboardRow,
) {
    val extended = MasroofThemeExtras.extendedColors
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    val statusLabel = when (row.status) {
        CommitmentPaymentStatus.PAID -> stringResource(R.string.dashboard_commitments_status_paid)
        CommitmentPaymentStatus.UNPAID -> stringResource(R.string.dashboard_commitments_status_unpaid)
    }
    val statusColor = when (row.status) {
        CommitmentPaymentStatus.PAID -> extended.inflow
        CommitmentPaymentStatus.UNPAID -> extended.liability
    }
    val displayName = when (row.source) {
        CommitmentDashboardSource.CREDIT_CARD -> stringResource(
            R.string.dashboard_credit_card_last4,
            formatCardLast4(row.creditFacilityKey?.substringAfterLast(':')),
        )
        else -> row.displayName
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            row.dueDate?.let { dueDate ->
                Text(
                    stringResource(R.string.dashboard_commitments_due_date, dateFormatter.format(dueDate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            MasroofAmountText(
                amount = formatLocalizedMoney(row.amount),
                role = MasroofAmountRole.List,
                color = statusColor,
            )
            Spacer(Modifier.width(MasroofSpacing.listItemGap))
            Text(
                statusLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = statusColor,
            )
        }
    }
}
