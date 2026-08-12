package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.CreditCardsOverview
import com.baraa.masroof.presentation.common.CardOwnershipInlinePrompt
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.StopTrackingCardButton
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.common.SectionHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CreditCardsSection(
    overview: CreditCardsOverview,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
    unknownCardLast4s: Set<String> = emptySet(),
    ownedCardLast4s: Set<String> = emptySet(),
    ownershipUpdating: Boolean = false,
    onConfirmCardOwned: (String) -> Unit = {},
    onMarkCardExternal: (String) -> Unit = {},
    onStopTrackingOwnedCard: (String) -> Unit = {},
) {
    if (!overview.hasContent) return

    val dateFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ar"))
    val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale("ar"))

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_credit_cards_title),
            icon = MasroofIcons.cardPayment,
        )
        Text(
            stringResource(R.string.dashboard_credit_cards_snapshot_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        overview.aggregateDueAmount?.let { due ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.dashboard_credit_card_aggregate_due),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        MoneyUiFormatter.format(due),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    overview.aggregateDueDate?.let { dueDate ->
                        Text(
                            stringResource(
                                R.string.dashboard_credit_card_due_date,
                                dateFormatter.format(dueDate),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    overview.aggregateDueUpdatedAt?.let { at ->
                        Text(
                            stringResource(
                                R.string.dashboard_credit_card_updated,
                                formatSnapshotTime(at, zoneId, dateTimeFormatter),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        overview.cards.forEach { row ->
            CreditCardRowCard(
                row = row,
                salaryPeriodLabel = overview.salaryPeriodLabel,
                zoneId = zoneId,
                dateFormatter = dateTimeFormatter,
                needsOwnershipConfirm = row.last4 in unknownCardLast4s,
                canStopTracking = row.last4 in ownedCardLast4s,
                ownershipUpdating = ownershipUpdating,
                onConfirmOwned = { onConfirmCardOwned(row.last4) },
                onMarkExternal = { onMarkCardExternal(row.last4) },
                onStopTracking = { onStopTrackingOwnedCard(row.last4) },
            )
        }
    }
}

@Composable
private fun CreditCardRowCard(
    row: CreditCardDashboardRow,
    salaryPeriodLabel: String?,
    zoneId: ZoneId,
    dateFormatter: DateTimeFormatter,
    needsOwnershipConfirm: Boolean,
    canStopTracking: Boolean,
    ownershipUpdating: Boolean,
    onConfirmOwned: () -> Unit,
    onMarkExternal: () -> Unit,
    onStopTracking: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MasroofIcons.cardPayment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(
                        R.string.dashboard_credit_card_last4,
                        formatCardLast4(row.last4),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            SnapshotMetricRow(
                label = if (row.statementPeriodLabel != null) {
                    stringResource(
                        R.string.dashboard_credit_card_statement_spending,
                        row.statementPeriodLabel,
                    )
                } else {
                    stringResource(R.string.dashboard_credit_card_statement_spending_fallback)
                },
                value = MoneyUiFormatter.format(row.statementSpendingNet),
            )

            SnapshotMetricRow(
                label = if (salaryPeriodLabel != null) {
                    stringResource(
                        R.string.dashboard_credit_card_salary_spending,
                        salaryPeriodLabel,
                    )
                } else {
                    stringResource(R.string.dashboard_credit_card_salary_spending_fallback)
                },
                value = MoneyUiFormatter.format(row.salaryPeriodSpendingNet),
            )

            row.snapshot?.availableBalance?.let { available ->
                SnapshotMetricRow(
                    label = stringResource(R.string.dashboard_credit_card_available),
                    value = MoneyUiFormatter.format(available),
                )
            }

            row.snapshot?.dueAmount?.let { due ->
                SnapshotMetricRow(
                    label = stringResource(R.string.dashboard_credit_card_card_due),
                    value = MoneyUiFormatter.format(due),
                )
            }

            row.snapshot?.updatedAt?.let { at ->
                Text(
                    stringResource(
                        R.string.dashboard_credit_card_updated,
                        formatSnapshotTime(at, zoneId, dateFormatter),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (needsOwnershipConfirm) {
                CardOwnershipInlinePrompt(
                    enabled = !ownershipUpdating,
                    onConfirmOwned = onConfirmOwned,
                    onMarkExternal = onMarkExternal,
                )
            } else if (canStopTracking) {
                StopTrackingCardButton(
                    enabled = !ownershipUpdating,
                    onClick = onStopTracking,
                )
            }
        }
    }
}

@Composable
private fun SnapshotMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatSnapshotTime(
    instant: Instant,
    zoneId: ZoneId,
    formatter: DateTimeFormatter,
): String = formatter.format(instant.atZone(zoneId))
