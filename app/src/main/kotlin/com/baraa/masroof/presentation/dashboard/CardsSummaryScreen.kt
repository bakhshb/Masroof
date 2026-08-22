package com.baraa.masroof.presentation.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.CreditCardsOverview
import com.baraa.masroof.application.dashboard.CreditFacilitiesOverview
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.application.dashboard.resolveLatestStatementDue
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent
import com.baraa.masroof.presentation.common.MasroofSectionTitle
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofExtendedColors
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CardsSummaryRoute(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onManageCards: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenAllTransactions: (TransactionListFilterState) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var selectedCardKey by rememberSaveable { mutableStateOf<String?>(null) }
    val cardNetworks = state.ownedCards.associate { CardOwnershipKey.of(it) to it.cardNetwork }
    val followedOverview = followedCreditCardsOverview(state)
    val followedFacilities = state.followedCreditFacilities()
    val selectedCard = selectedCardKey?.let { key ->
        followedFacilities?.facilities
            ?.flatMap { it.allCards }
            ?.find { ownedCardKey(it) == key }
            ?: followedOverview?.cards?.find { ownedCardKey(it) == key }
    }

    BackHandler {
        if (selectedCard != null) {
            selectedCardKey = null
        } else {
            onBack()
        }
    }

    if (selectedCard != null) {
        CardDetailScreen(
            row = selectedCard,
            salaryPeriodLabel = followedOverview?.salaryPeriodLabel
                ?: followedFacilities?.facilities?.firstOrNull()?.salaryPeriodLabel,
            state = state,
            onBack = { selectedCardKey = null },
            onPrevious = viewModel::goToPreviousPeriod,
            onNext = viewModel::goToNextPeriod,
            onCurrent = viewModel::goToCurrentPeriod,
            onOpenTransaction = onOpenTransaction,
            onViewAllTransactions = {
                onOpenAllTransactions(
                    TransactionListFilterState(cardLast4s = setOf(selectedCard.last4)),
                )
            },
        )
    } else {
        CardsSummaryScreen(
            state = state,
            onBack = onBack,
            onPrevious = viewModel::goToPreviousPeriod,
            onNext = viewModel::goToNextPeriod,
            onCurrent = viewModel::goToCurrentPeriod,
            onManageCards = onManageCards,
            onOpenCard = { row -> selectedCardKey = ownedCardKey(row) },
            cardNetworksByLast4 = cardNetworks,
        )
    }
}

private fun ownedCardKey(row: CreditCardDashboardRow): String =
    "${row.bank.id}:${row.last4}"

@Composable
fun CardsSummaryScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
    onManageCards: () -> Unit,
    onOpenCard: (CreditCardDashboardRow) -> Unit,
    cardNetworksByLast4: Map<String, com.baraa.masroof.domain.model.CardNetwork?>,
) {
    val followedFacilities = state.followedCreditFacilities()
    val followedOverview = followedCreditCardsOverview(state)

    DashboardSummaryScaffold(
        title = stringResource(R.string.dashboard_cards_summary_screen_title),
        state = state,
        onBack = onBack,
        onPrevious = onPrevious,
        onNext = onNext,
        onCurrent = onCurrent,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                followedFacilities != null && followedFacilities.hasContent -> {
                    FacilitiesSummaryHeroCard(overview = followedFacilities)
                    CardsSummaryHeader(
                        cardCount = followedFacilities.facilities.size + followedFacilities.debitCards.size,
                        onManageCards = onManageCards,
                    )
                    followedFacilities.facilities.forEach { facility ->
                        CreditFacilityCard(
                            facility = facility,
                            cardNetworksByLast4 = cardNetworksByLast4,
                            zoneId = ZoneId.systemDefault(),
                            modifier = Modifier.fillMaxWidth(),
                            onOpenCard = onOpenCard,
                        )
                    }
                    followedFacilities.debitCards.forEach { debit ->
                        DebitCardOverviewRow(
                            debit = debit,
                            network = cardNetworksByLast4[CardOwnershipKey.of(debit)] ?: debit.network,
                        )
                    }
                }

                followedOverview != null -> {
                    CardsSummaryHeroCard(overview = followedOverview)
                    CardsSummaryHeader(
                        cardCount = followedOverview.cards.size,
                        onManageCards = onManageCards,
                    )
                    if (followedOverview.cards.isEmpty()) {
                        Text(
                            stringResource(R.string.dashboard_cards_summary_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        followedOverview.cards.forEach { row ->
                            CreditCardCompactListRow(
                                row = row,
                                cardNetwork = cardNetworksByLast4[CardOwnershipKey.of(row)],
                                onClick = { onOpenCard(row) },
                            )
                        }
                    }
                }

                else -> {
                    Text(
                        stringResource(R.string.dashboard_cards_summary_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FacilitiesSummaryHeroCard(overview: CreditFacilitiesOverview) {
    val extended = MasroofThemeExtras.extendedColors
    val primaryRows = overview.facilities.flatMap { it.allCards }
    val due = resolveLatestStatementDue(primaryRows)?.amount
    val periodSpending = sumSignedAmounts(overview.facilities.map { it.facilitySalaryPeriodSpending })
    val statementSpending = sumSignedAmounts(overview.facilities.map { it.facilityStatementSpending })
    val periodSpendingColor = spendingAmountColor(periodSpending, extended)
    val statementSpendingColor = spendingAmountColor(statementSpending, extended)
    val aggregateStatementLabel = overview.facilities.firstOrNull()?.aggregateStatementPeriodLabel
    val salaryPeriodLabel = overview.facilities.firstOrNull()?.salaryPeriodLabel

    MasroofCard(accent = MasroofCardAccent.Credit) {
        Text(
            stringResource(R.string.dashboard_credit_card_aggregate_due),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            due?.let { formatLocalizedMoney(it) }
                ?: stringResource(R.string.dashboard_value_unavailable),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = extended.liability,
            ),
            modifier = Modifier.padding(top = 8.dp),
        )

        Text(
            if (salaryPeriodLabel != null) {
                stringResource(R.string.dashboard_credit_cards_aggregate_period_spending, salaryPeriodLabel)
            } else {
                stringResource(R.string.dashboard_credit_cards_aggregate_period_spending_fallback)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            formatLocalizedMoney(periodSpending),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = periodSpendingColor,
            ),
            modifier = Modifier.padding(top = 4.dp),
        )

        Text(
            if (aggregateStatementLabel != null) {
                stringResource(
                    R.string.dashboard_credit_cards_aggregate_statement_spending,
                    aggregateStatementLabel,
                )
            } else {
                stringResource(R.string.dashboard_credit_cards_aggregate_statement_spending_fallback)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            formatLocalizedMoney(statementSpending),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = statementSpendingColor,
            ),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun sumSignedAmounts(amounts: List<SignedMoneyAmount>): SignedMoneyAmount {
    if (amounts.isEmpty()) return SignedMoneyAmount.zero(Currency.SAR)
    var sum = BigDecimal.ZERO
    val currency = amounts.first().currency
    amounts.forEach { sum = sum.add(it.amount) }
    return SignedMoneyAmount(sum.setScale(Money.SCALE, RoundingMode.HALF_EVEN), currency)
}

@Composable
private fun CardsSummaryHeroCard(overview: CreditCardsOverview) {
    val extended = MasroofThemeExtras.extendedColors
    val aggregateDue = overview.aggregateDueAmount
    val periodSpending = overview.aggregatePeriodSpendingNet
    val statementSpending = overview.aggregateStatementSpendingNet
    val periodSpendingColor = spendingAmountColor(periodSpending, extended)
    val statementSpendingColor = spendingAmountColor(statementSpending, extended)
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)

    MasroofCard(accent = MasroofCardAccent.Credit) {
        Text(
            stringResource(R.string.dashboard_credit_card_aggregate_due),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            aggregateDue?.let { formatLocalizedMoney(it) }
                ?: stringResource(R.string.dashboard_value_unavailable),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = extended.liability,
            ),
            modifier = Modifier.padding(top = 8.dp),
        )

        Text(
            if (overview.salaryPeriodLabel != null) {
                stringResource(
                    R.string.dashboard_credit_cards_aggregate_period_spending,
                    overview.salaryPeriodLabel,
                )
            } else {
                stringResource(R.string.dashboard_credit_cards_aggregate_period_spending_fallback)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            formatLocalizedMoney(periodSpending),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = periodSpendingColor,
            ),
            modifier = Modifier.padding(top = 4.dp),
        )

        Text(
            if (overview.aggregateStatementPeriodLabel != null) {
                stringResource(
                    R.string.dashboard_credit_cards_aggregate_statement_spending,
                    overview.aggregateStatementPeriodLabel,
                )
            } else {
                stringResource(R.string.dashboard_credit_cards_aggregate_statement_spending_fallback)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            formatLocalizedMoney(statementSpending),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = statementSpendingColor,
            ),
            modifier = Modifier.padding(top = 4.dp),
        )

        overview.aggregateDueDate?.let { dueDate ->
            Text(
                stringResource(
                    R.string.dashboard_credit_card_due_date,
                    dateFormatter.format(dueDate),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun CardsSummaryHeader(
    cardCount: Int,
    onManageCards: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MasroofSectionTitle(
            title = stringResource(R.string.dashboard_cards_count_label, cardCount),
        )
        TextButton(onClick = onManageCards) {
            Text(stringResource(R.string.dashboard_manage_cards))
        }
    }
}

private fun followedCreditCardsOverview(state: DashboardUiState): CreditCardsOverview? =
    state.followedCreditCardsOverview()

@Composable
private fun spendingAmountColor(
    amount: SignedMoneyAmount,
    extended: MasroofExtendedColors,
): androidx.compose.ui.graphics.Color =
    when {
        amount.amount.signum() > 0 -> extended.outflow
        amount.amount.signum() < 0 -> extended.inflow
        else -> MaterialTheme.colorScheme.onSurface
    }
