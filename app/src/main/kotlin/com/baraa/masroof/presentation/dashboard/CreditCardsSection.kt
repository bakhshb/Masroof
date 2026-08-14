package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.CreditCardsOverview
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CreditCardsSection(
    overview: CreditCardsOverview,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
) {
    if (!overview.hasContent) return

    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = DateTimeFormatter.ofPattern("d MMMM", locale)
    val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", locale)

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
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                stringResource(R.string.dashboard_credit_card_statement_day_hint),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        overview.aggregateDueAmount?.let { due ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.dashboard_credit_card_aggregate_due),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatLocalizedMoney(due),
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
                value = formatLocalizedMoney(row.statementSpendingNet),
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
                value = formatLocalizedMoney(row.salaryPeriodSpendingNet),
            )

            row.snapshot?.availableBalance?.let { available ->
                SnapshotMetricRow(
                    label = stringResource(R.string.dashboard_credit_card_available),
                    value = formatLocalizedMoney(available),
                )
            }

            row.snapshot?.dueAmount?.let { due ->
                SnapshotMetricRow(
                    label = stringResource(R.string.dashboard_credit_card_card_due),
                    value = formatLocalizedMoney(due),
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

fun CreditCardsOverview.followedOnly(ownedLast4s: Set<String>): CreditCardsOverview =
    copy(cards = cards.filter { it.last4 in ownedLast4s })

fun CreditCardsOverview.followedSalarySpendingTotal(ownedLast4s: Set<String>): SignedMoneyAmount {
    val followed = cards.filter { it.last4 in ownedLast4s }
    if (followed.isEmpty()) return SignedMoneyAmount.zero(currency)
    var sum = java.math.BigDecimal.ZERO
    for (row in followed) {
        sum = sum.add(row.salaryPeriodSpendingNet.amount)
    }
    return SignedMoneyAmount(
        sum.setScale(Money.SCALE, java.math.RoundingMode.HALF_EVEN),
        currency,
    )
}
