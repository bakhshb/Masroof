package com.baraa.masroof.presentation.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent
import com.baraa.masroof.presentation.common.MasroofSectionTitle
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
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
    val followedOverview = followedCreditCardsOverview(state)
    val selectedCard = selectedCardKey?.let { key ->
        followedOverview?.cards?.find { ownedCardKey(it) == key }
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
            salaryPeriodLabel = followedOverview?.salaryPeriodLabel,
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
) {
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
            followedOverview?.let { overview ->
                CardsSummaryHeroCard(overview = overview)
            }

            CardsSummaryHeader(
                cardCount = followedOverview?.cards?.size ?: 0,
                onManageCards = onManageCards,
            )

            val cards = followedOverview?.cards.orEmpty()
            if (cards.isEmpty()) {
                Text(
                    stringResource(R.string.dashboard_cards_summary_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                cards.forEach { row ->
                    CreditCardSummaryTile(
                        row = row,
                        salaryPeriodLabel = followedOverview?.salaryPeriodLabel,
                        zoneId = ZoneId.systemDefault(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCard(row) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CardsSummaryHeroCard(overview: CreditCardsOverview) {
    val extended = MasroofThemeExtras.extendedColors
    val aggregateDue = sumFollowedCardDue(overview.cards)
    val periodSpending = overview.aggregatePeriodSpendingNet
    val periodSpendingColor = when {
        periodSpending.amount.signum() > 0 -> extended.outflow
        periodSpending.amount.signum() < 0 -> extended.inflow
        else -> MaterialTheme.colorScheme.onSurface
    }
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

private fun followedCreditCardsOverview(state: DashboardUiState): CreditCardsOverview? {
    val overview = state.creditCards ?: return null
    val ownedLast4s = state.ownedCards.map { it.last4 }.toSet()
    return overview.followedOnly(ownedLast4s)
}

private fun sumFollowedCardDue(
    cards: List<com.baraa.masroof.application.dashboard.CreditCardDashboardRow>,
): Money? {
    val dues = cards.mapNotNull { it.snapshot?.dueAmount }
    if (dues.isEmpty()) return null
    return dues.reduce { acc, due -> acc + due }
}
