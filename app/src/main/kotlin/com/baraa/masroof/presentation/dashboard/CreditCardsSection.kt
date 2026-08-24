package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.CreditCardsOverview
import com.baraa.masroof.application.dashboard.DashboardSectionSize
import com.baraa.masroof.application.dashboard.resolveLatestStatementDue
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.domain.model.CardNetwork
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
    cardNetworksByLast4: Map<String, com.baraa.masroof.domain.model.CardNetwork?> = emptyMap(),
    ownedCards: List<OwnedCardUi> = emptyList(),
    onViewAll: (() -> Unit)? = null,
    size: DashboardSectionSize = DashboardSectionSize.MEDIUM,
) {
    if (!overview.hasContent) return
    val metrics = dashboardSectionMetrics(size)

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
                    cardNetwork = cardNetworksByLast4[CardOwnershipKey.of(row)],
                    ownedCards = ownedCards,
                    modifier = Modifier.width(metrics.cardWidth),
                )
            }
        }
    }
}

@Composable
fun CreditCardCompactListRow(
    row: CreditCardDashboardRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardNetwork: CardNetwork? = null,
    ownedCards: List<OwnedCardUi> = emptyList(),
) {
    val extended = MasroofThemeExtras.extendedColors
    val statementLabel = if (row.statementPeriodLabel != null) {
        stringResource(
            R.string.dashboard_credit_card_statement_purchases_total,
            row.statementPeriodLabel,
        )
    } else {
        stringResource(R.string.dashboard_credit_card_statement_purchases_total_fallback)
    }
    val spendingColor = spendingColor(row.statementSpendingNet)

    MasroofCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CardNetworkBadge(network = cardNetwork, last4 = row.last4)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.displayLabel(ownedCards),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    statementLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    formatLocalizedMoney(row.statementSpendingNet),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = spendingColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = MasroofIcons.periodNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.dashboard_credit_card_open_details),
                    style = MaterialTheme.typography.labelSmall,
                    color = extended.account,
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
    cardNetwork: CardNetwork? = null,
    ownedCards: List<OwnedCardUi> = emptyList(),
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
                        modifier = Modifier
                            .weight(1f)
                            .height(creditCardMetricTileHeight),
                    )
                    CreditCardMetricTile(
                        label = statementLabelText,
                        value = formatLocalizedMoney(row.statementSpendingNet),
                        valueColor = spendingColor(row.statementSpendingNet),
                        modifier = Modifier
                            .weight(1f)
                            .height(creditCardMetricTileHeight),
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
                            modifier = Modifier
                                .weight(1f)
                                .height(creditCardMetricTileHeight),
                        )
                        CreditCardMetricTile(
                            label = stringResource(R.string.dashboard_credit_card_card_due),
                            value = row.snapshot?.dueAmount?.let { formatLocalizedMoney(it) }
                                ?: stringResource(R.string.dashboard_value_unavailable),
                            valueColor = extended.liability,
                            modifier = Modifier
                                .weight(1f)
                                .height(creditCardMetricTileHeight),
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

private val creditCardMetricTileHeight = 76.dp

@Composable
private fun CreditCardMetricTile(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        color = extended.miniBackground,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            extended.cardBorder,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                ),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatSnapshotTime(
    instant: Instant,
    zoneId: ZoneId,
    formatter: DateTimeFormatter,
): String = formatter.format(instant.atZone(zoneId))

fun CreditCardsOverview.followedOnly(ownedKeys: Set<String>): CreditCardsOverview {
    val filteredCards = cards.filter { CardOwnershipKey.of(it) in ownedKeys }
    val statementDue = resolveLatestStatementDue(filteredCards)
    return copy(
        cards = filteredCards,
        aggregatePeriodSpendingNet = sumFollowedSpending(filteredCards) { it.salaryPeriodSpendingNet },
        aggregateStatementSpendingNet = sumFollowedSpending(filteredCards) { it.statementSpendingNet },
        aggregateDueAmount = statementDue?.amount,
        aggregateDueUpdatedAt = statementDue?.updatedAt,
        aggregateDueDate = statementDue?.dueDate,
    )
}

fun CreditCardsOverview.followedSalaryPeriodSpendingTotal(ownedKeys: Set<String>): SignedMoneyAmount =
    sumFollowedSpending(cards.filter { CardOwnershipKey.of(it) in ownedKeys }) { it.salaryPeriodSpendingNet }

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
