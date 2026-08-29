package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CommitmentDashboardRow
import com.baraa.masroof.application.dashboard.CommitmentPaymentStatus
import com.baraa.masroof.application.dashboard.CommitmentsOverview
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofDonutChart
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofMoneyRow
import com.baraa.masroof.presentation.common.MasroofMoneyRowStyle
import com.baraa.masroof.presentation.common.MasroofSectionHeader
import com.baraa.masroof.presentation.common.commitmentDonutPaidFraction
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
        MasroofSectionHeader(
            title = stringResource(R.string.dashboard_commitments_title),
            icon = MasroofIcons.calendar,
        )

        MasroofCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MasroofSpacing.listItemGap),
                ) {
                    MasroofMoneyRow(
                        label = stringResource(R.string.dashboard_commitments_total),
                        value = formatLocalizedMoney(overview.total),
                        style = MasroofMoneyRowStyle.Neutral,
                    )
                    MasroofMoneyRow(
                        label = stringResource(R.string.dashboard_commitments_paid),
                        value = formatLocalizedMoney(overview.paid),
                        style = MasroofMoneyRowStyle.Inflow,
                    )
                    MasroofMoneyRow(
                        label = stringResource(R.string.dashboard_commitments_remaining),
                        value = formatLocalizedMoney(overview.remaining),
                        style = MasroofMoneyRowStyle.Outflow,
                    )
                }
                MasroofDonutChart(
                    paidFraction = commitmentDonutPaidFraction(overview.total.amount, overview.paid.amount),
                    centerTitle = stringResource(R.string.dashboard_commitments_total),
                    centerValue = formatLocalizedMoney(overview.total),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MasroofSpacing.sectionGap),
                verticalArrangement = Arrangement.spacedBy(MasroofSpacing.listItemGap),
            ) {
                overview.rows.forEach { row ->
                    CommitmentDashboardRowItem(row = row)
                }
            }
        }
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.displayName,
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
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatLocalizedMoney(row.amount),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                statusLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = statusColor,
            )
        }
    }
}
