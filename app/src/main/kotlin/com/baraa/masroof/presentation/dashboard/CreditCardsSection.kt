package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.CreditCardsOverview
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class CreditCardMetricsPresentation {
    SummaryPurchases,
    DetailSpending,
}

@Composable
fun CreditCardsSection(
    overview: CreditCardsOverview,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
) {
    if (!overview.hasContent) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_cards_summary_title),
            icon = MasroofIcons.cardPayment,
            onViewAll = onViewAll,
            viewAllLabel = stringResource(R.string.dashboard_view_all),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(overview.cards, key = { "${it.bank.id}-${it.last4}" }) { row ->
                CreditCardSummaryTile(
                    row = row,
                    salaryPeriodLabel = overview.salaryPeriodLabel,
                    zoneId = zoneId,
                    presentation = CreditCardMetricsPresentation.SummaryPurchases,
                    modifier = Modifier.width(268.dp),
                )
            }
        }
    }
}

@Composable
fun CreditCardSummaryTile(
    row: CreditCardDashboardRow,
    salaryPeriodLabel: String?,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
    presentation: CreditCardMetricsPresentation = CreditCardMetricsPresentation.SummaryPurchases,
    showBalanceAndDue: Boolean = presentation == CreditCardMetricsPresentation.SummaryPurchases,
) {
    val extended = MasroofThemeExtras.extendedColors
    val locale = LocalConfiguration.current.locales[0]
    val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", locale)

    val salaryPeriodLabelText = when (presentation) {
        CreditCardMetricsPresentation.SummaryPurchases -> {
            if (salaryPeriodLabel != null) {
                stringResource(R.string.dashboard_credit_card_salary_purchases_total, salaryPeriodLabel)
            } else {
                stringResource(R.string.dashboard_credit_card_salary_purchases_total_fallback)
            }
        }

        CreditCardMetricsPresentation.DetailSpending -> {
            if (salaryPeriodLabel != null) {
                stringResource(R.string.dashboard_credit_card_salary_spending, salaryPeriodLabel)
            } else {
                stringResource(R.string.dashboard_credit_card_salary_spending_fallback)
            }
        }
    }

    val statementLabelText = when (presentation) {
        CreditCardMetricsPresentation.SummaryPurchases -> {
            if (row.statementPeriodLabel != null) {
                stringResource(
                    R.string.dashboard_credit_card_statement_purchases_total,
                    row.statementPeriodLabel,
                )
            } else {
                stringResource(R.string.dashboard_credit_card_statement_purchases_total_fallback)
            }
        }

        CreditCardMetricsPresentation.DetailSpending -> {
            if (row.statementPeriodLabel != null) {
                stringResource(
                    R.string.dashboard_credit_card_statement_spending,
                    row.statementPeriodLabel,
                )
            } else {
                stringResource(R.string.dashboard_credit_card_statement_spending_fallback)
            }
        }
    }

    MasroofCard(modifier = modifier) {
        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CreditCardBrandBadge(last4 = row.last4)
                    Column {
                        Text(
                            stringResource(
                                R.string.dashboard_credit_card_last4,
                                formatCardLast4(row.last4),
                            ),
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
            }

            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CreditCardMetricTile(
                        label = salaryPeriodLabelText,
                        value = formatLocalizedMoney(row.salaryPeriodSpendingNet),
                        valueColor = spendingColor(row.salaryPeriodSpendingNet),
                        modifier = Modifier.weight(1f),
                    )
                    CreditCardMetricTile(
                        label = statementLabelText,
                        value = formatLocalizedMoney(row.statementSpendingNet),
                        valueColor = spendingColor(row.statementSpendingNet),
                        modifier = Modifier.weight(1f),
                    )
                }

                if (showBalanceAndDue) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CreditCardMetricTile(
                            label = stringResource(R.string.dashboard_credit_card_current_balance),
                            value = row.snapshot?.availableBalance?.let { formatLocalizedMoney(it) }
                                ?: stringResource(R.string.dashboard_value_unavailable),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        CreditCardMetricTile(
                            label = stringResource(R.string.dashboard_credit_card_card_due),
                            value = row.snapshot?.dueAmount?.let { formatLocalizedMoney(it) }
                                ?: stringResource(R.string.dashboard_value_unavailable),
                            valueColor = extended.liability,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                row.snapshot?.updatedAt?.let { at ->
                    Text(
                        stringResource(
                            R.string.dashboard_credit_card_updated,
                            formatSnapshotTime(at, zoneId, dateTimeFormatter),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun spendingColor(amount: SignedMoneyAmount): androidx.compose.ui.graphics.Color {
    val extended = MasroofThemeExtras.extendedColors
    return when {
        amount.amount.signum() > 0 -> extended.outflow
        amount.amount.signum() < 0 -> extended.inflow
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
private fun CreditCardBrandBadge(last4: String) {
    val colors = when (last4.firstOrNull()?.digitToIntOrNull()?.rem(3)) {
        0 -> listOf(androidx.compose.ui.graphics.Color(0xFFEB001B), androidx.compose.ui.graphics.Color(0xFFF79E1B))
        1 -> listOf(androidx.compose.ui.graphics.Color(0xFF1A1F71), androidx.compose.ui.graphics.Color(0xFF1A1F71))
        else -> listOf(androidx.compose.ui.graphics.Color(0xFF004D40), androidx.compose.ui.graphics.Color(0xFF00695C))
    }
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(colors),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "••",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = androidx.compose.ui.graphics.Color.White,
        )
    }
}

@Composable
private fun CreditCardMetricTile(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = extended.miniBackground,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            extended.cardBorder,
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = valueColor,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
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
    if (rows.isEmpty()) return SignedMoneyAmount.zero(com.baraa.masroof.core.money.Currency.SAR)
    var sum = java.math.BigDecimal.ZERO
    val rowCurrency = rows.first().statementSpendingNet.currency
    for (row in rows) {
        sum = sum.add(selector(row).amount)
    }
    return SignedMoneyAmount(
        sum.setScale(com.baraa.masroof.core.money.Money.SCALE, java.math.RoundingMode.HALF_EVEN),
        rowCurrency,
    )
}
