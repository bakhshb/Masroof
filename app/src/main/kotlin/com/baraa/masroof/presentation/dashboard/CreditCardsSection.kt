package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.CreditCardsOverview
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.common.MasroofBadge
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent
import com.baraa.masroof.presentation.common.MasroofCycleChip
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofMiniCard
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
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

    val extended = MasroofThemeExtras.extendedColors
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
        MasroofCycleChip(
            text = stringResource(R.string.dashboard_credit_card_statement_day_hint),
        )

        overview.aggregateDueAmount?.let { due ->
            MasroofCard(accent = MasroofCardAccent.Liability) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.dashboard_credit_card_aggregate_due),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.dashboard_credit_card_liability_subtitle),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    MasroofBadge(
                        text = stringResource(R.string.dashboard_credit_card_liability_badge),
                        accent = MasroofCardAccent.Liability,
                    )
                }
                Text(
                    formatLocalizedMoney(due),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = extended.liability,
                    ),
                    modifier = Modifier.padding(top = 8.dp),
                )
                overview.aggregateDueDate?.let { dueDate ->
                    Text(
                        stringResource(
                            R.string.dashboard_credit_card_due_date,
                            dateFormatter.format(dueDate),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
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

        overview.cards.forEach { row ->
            CreditCardRowCard(
                row = row,
                salaryPeriodLabel = overview.salaryPeriodLabel,
                zoneId = zoneId,
                dateFormatter = dateTimeFormatter,
            )
        }

        if (overview.cards.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AggregateSpendingMiniCard(
                    title = if (overview.aggregateStatementPeriodLabel != null) {
                        stringResource(
                            R.string.dashboard_credit_cards_aggregate_statement_spending,
                            overview.aggregateStatementPeriodLabel,
                        )
                    } else {
                        stringResource(R.string.dashboard_credit_cards_aggregate_statement_spending_fallback)
                    },
                    amount = overview.aggregateStatementSpendingNet,
                    modifier = Modifier.weight(1f),
                )
                AggregateSpendingMiniCard(
                    title = if (overview.salaryPeriodLabel != null) {
                        stringResource(
                            R.string.dashboard_credit_cards_aggregate_period_spending,
                            overview.salaryPeriodLabel,
                        )
                    } else {
                        stringResource(R.string.dashboard_credit_cards_aggregate_period_spending_fallback)
                    },
                    amount = overview.aggregatePeriodSpendingNet,
                    modifier = Modifier.weight(1f),
                )
            }
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
    val extended = MasroofThemeExtras.extendedColors
    MasroofCard(accent = MasroofCardAccent.Credit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(
                        R.string.dashboard_credit_card_last4,
                        formatCardLast4(row.last4),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.dashboard_credit_card_spending_subtitle),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
            MasroofBadge(
                text = stringResource(R.string.dashboard_credit_card_credit_badge),
                accent = MasroofCardAccent.Credit,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MasroofMiniCard(
                label = if (row.statementPeriodLabel != null) {
                    stringResource(
                        R.string.dashboard_credit_card_statement_spending,
                        row.statementPeriodLabel,
                    )
                } else {
                    stringResource(R.string.dashboard_credit_card_statement_spending_fallback)
                },
                value = formatLocalizedMoney(row.statementSpendingNet),
                valueColor = extended.card,
                modifier = Modifier.weight(1f),
            )
            MasroofMiniCard(
                label = if (salaryPeriodLabel != null) {
                    stringResource(
                        R.string.dashboard_credit_card_salary_spending,
                        salaryPeriodLabel,
                    )
                } else {
                    stringResource(R.string.dashboard_credit_card_salary_spending_fallback)
                },
                value = formatLocalizedMoney(row.salaryPeriodSpendingNet),
                valueColor = extended.card,
                modifier = Modifier.weight(1f),
            )
        }

        row.snapshot?.let { snapshot ->
            Spacer(Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                snapshot.dueAmount?.let { due ->
                    MasroofMiniCard(
                        label = stringResource(R.string.dashboard_credit_card_card_due),
                        value = formatLocalizedMoney(due),
                        valueColor = extended.liability,
                        modifier = Modifier.weight(1f),
                    )
                }
                snapshot.availableBalance?.let { available ->
                    MasroofMiniCard(
                        label = stringResource(R.string.dashboard_credit_card_available),
                        value = formatLocalizedMoney(available),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            snapshot.updatedAt?.let { at ->
                Text(
                    stringResource(
                        R.string.dashboard_credit_card_updated,
                        formatSnapshotTime(at, zoneId, dateFormatter),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AggregateSpendingMiniCard(
    title: String,
    amount: SignedMoneyAmount,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    MasroofMiniCard(
        label = title,
        value = formatLocalizedMoney(amount),
        valueColor = extended.card,
        modifier = modifier,
    )
}

private fun formatSnapshotTime(
    instant: Instant,
    zoneId: ZoneId,
    formatter: DateTimeFormatter,
): String = formatter.format(instant.atZone(zoneId))

fun CreditCardsOverview.followedOnly(ownedLast4s: Set<String>): CreditCardsOverview {
    val filteredCards = cards.filter { it.last4 in ownedLast4s }
    return copy(
        cards = filteredCards,
        aggregatePeriodSpendingNet = sumFollowedSpending(filteredCards) { it.salaryPeriodSpendingNet },
        aggregateStatementSpendingNet = sumFollowedSpending(filteredCards) { it.statementSpendingNet },
    )
}

fun CreditCardsOverview.followedSalaryPeriodSpendingTotal(ownedLast4s: Set<String>): SignedMoneyAmount =
    sumFollowedSpending(cards.filter { it.last4 in ownedLast4s }) { it.salaryPeriodSpendingNet }

private fun sumFollowedSpending(
    rows: List<CreditCardDashboardRow>,
    selector: (CreditCardDashboardRow) -> SignedMoneyAmount,
): SignedMoneyAmount {
    if (rows.isEmpty()) return SignedMoneyAmount.zero(Currency.SAR)
    var sum = java.math.BigDecimal.ZERO
    val rowCurrency = rows.first().statementSpendingNet.currency
    for (row in rows) {
        sum = sum.add(selector(row).amount)
    }
    return SignedMoneyAmount(
        sum.setScale(Money.SCALE, java.math.RoundingMode.HALF_EVEN),
        rowCurrency,
    )
}
