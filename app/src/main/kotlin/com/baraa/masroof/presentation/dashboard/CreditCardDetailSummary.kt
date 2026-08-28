package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CreditCardDetailSummaryCard(
    row: CreditCardDashboardRow,
    salaryPeriodLabel: String?,
    zoneId: ZoneId,
    cardNetwork: CardNetwork? = null,
    ownedCards: List<OwnedCardUi> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", locale)

    val salaryPeriodLabelText = if (salaryPeriodLabel != null) {
        stringResource(R.string.dashboard_credit_card_salary_spending, salaryPeriodLabel)
    } else {
        stringResource(R.string.dashboard_credit_card_salary_spending_fallback)
    }
    val statementLabelText = if (row.statementPeriodLabel != null) {
        stringResource(R.string.dashboard_credit_card_statement_spending, row.statementPeriodLabel)
    } else {
        stringResource(R.string.dashboard_credit_card_statement_spending_fallback)
    }

    val metrics = buildCreditCardDetailMetrics(
        row = row,
        salaryPeriodLabelText = salaryPeriodLabelText,
        statementLabelText = statementLabelText,
    )

    MasroofCard(modifier = modifier, accent = MasroofCardAccent.Credit) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardNetworkBadge(network = cardNetwork, last4 = row.last4)
            Column {
                Text(
                    row.displayLabel(ownedCards),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.dashboard_credit_card_credit_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DashboardSummaryMetricGrid(
            metrics = metrics,
            modifier = Modifier.padding(top = 12.dp),
        )

        row.snapshot?.updatedAt?.let { at ->
            Text(
                stringResource(
                    R.string.dashboard_credit_card_updated,
                    formatSnapshotTime(at, zoneId, dateTimeFormatter),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
internal fun CreditSupplementaryRow(
    row: CreditCardDashboardRow,
    salaryPeriodLabel: String?,
    cardNetwork: CardNetwork?,
    ownedCards: List<OwnedCardUi>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val extended = MasroofThemeExtras.extendedColors
    val salaryLabel = if (salaryPeriodLabel != null) {
        stringResource(R.string.dashboard_credit_card_salary_purchases_total, salaryPeriodLabel)
    } else {
        stringResource(R.string.dashboard_credit_card_salary_purchases_total_fallback)
    }
    val statementLabel = if (row.statementPeriodLabel != null) {
        stringResource(R.string.dashboard_credit_card_statement_purchases_total, row.statementPeriodLabel)
    } else {
        stringResource(R.string.dashboard_credit_card_statement_purchases_total_fallback)
    }

    MasroofCard(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            CardNetworkBadge(network = cardNetwork, last4 = row.last4)
            Column {
                Text(
                    row.displayLabel(ownedCards),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    stringResource(R.string.dashboard_credit_card_credit_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            salaryLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            formatLocalizedMoney(row.salaryPeriodSpendingNet),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = extended.outflow,
        )

        Text(
            statementLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            formatLocalizedMoney(row.statementSpendingNet),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = extended.outflow,
        )
    }
}

@Composable
private fun buildCreditCardDetailMetrics(
    row: CreditCardDashboardRow,
    salaryPeriodLabelText: String,
    statementLabelText: String,
): List<DashboardSummaryMetricItem> = listOf(
    DashboardSummaryMetricItem(
        title = salaryPeriodLabelText,
        amount = formatLocalizedMoney(row.salaryPeriodSpendingNet),
        tone = spendingMetricTone(row.salaryPeriodSpendingNet),
    ),
    DashboardSummaryMetricItem(
        title = statementLabelText,
        amount = formatLocalizedMoney(row.statementSpendingNet),
        tone = spendingMetricTone(row.statementSpendingNet),
    ),
    DashboardSummaryMetricItem(
        title = stringResource(R.string.dashboard_credit_card_current_balance),
        amount = row.snapshot?.availableBalance?.let { formatLocalizedMoney(it) }
            ?: stringResource(R.string.dashboard_value_unavailable),
        tone = DashboardMetricTone.Neutral,
    ),
    DashboardSummaryMetricItem(
        title = stringResource(R.string.dashboard_credit_card_card_due),
        amount = row.snapshot?.dueAmount?.let { formatLocalizedMoney(it) }
            ?: stringResource(R.string.dashboard_value_unavailable),
        tone = DashboardMetricTone.Liability,
    ),
)

private fun formatSnapshotTime(
    instant: Instant,
    zoneId: ZoneId,
    formatter: DateTimeFormatter,
): String = formatter.format(instant.atZone(zoneId))
