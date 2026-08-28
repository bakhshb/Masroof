package com.baraa.masroof.presentation.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.CreditCardsOverview
import com.baraa.masroof.presentation.common.MasroofSectionTitle
import java.time.ZoneId

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
    val followedFacilities = state.followedCreditFacilitiesForSummary()
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

    when {
        selectedCard != null -> {
            CardDetailScreen(
                row = selectedCard,
                salaryPeriodLabel = followedOverview?.salaryPeriodLabel
                    ?: followedFacilities?.facilities?.firstOrNull()?.salaryPeriodLabel,
                state = state,
                onBack = { selectedCardKey = null },
                onOpenTransaction = onOpenTransaction,
                onViewAllTransactions = {
                    onOpenAllTransactions(
                        TransactionListFilterState(cardLast4s = setOf(selectedCard.last4)),
                    )
                },
            )
        }

        else -> {
            CardsSummaryScreen(
                state = state,
                onBack = onBack,
                onManageCards = onManageCards,
                onOpenCard = { row -> selectedCardKey = ownedCardKey(row) },
                cardNetworksByLast4 = state.ownedCards.associate { CardOwnershipKey.of(it) to it.cardNetwork },
            )
        }
    }
}

private fun ownedCardKey(row: CreditCardDashboardRow): String =
    "${row.bank.id}:${row.last4}"

@Composable
fun CardsSummaryScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onManageCards: () -> Unit,
    onOpenCard: (CreditCardDashboardRow) -> Unit,
    cardNetworksByLast4: Map<String, com.baraa.masroof.domain.model.CardNetwork?>,
) {
    val followedFacilities = state.followedCreditFacilitiesForSummary()
    val followedOverview = followedCreditCardsOverview(state)
    val locale = LocalConfiguration.current.locales[0]

    DashboardSummaryScaffold(
        title = stringResource(R.string.dashboard_cards_summary_screen_title),
        state = state,
        onBack = onBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                followedFacilities != null && followedFacilities.hasContent -> {
                    DashboardSummaryHeroCard(
                        spec = creditFacilitiesSummaryHeroSpec(
                            overview = followedFacilities,
                            locale = locale,
                        ),
                    )
                    CardsSummaryHeader(
                        cardCount = followedFacilities.facilities.size,
                        onManageCards = onManageCards,
                    )
                    followedFacilities.facilities.forEach { facility ->
                        CreditFacilityCard(
                            facility = facility,
                            cardNetworksByLast4 = cardNetworksByLast4,
                            zoneId = ZoneId.systemDefault(),
                            ownedCards = state.ownedCards,
                            modifier = Modifier.fillMaxWidth(),
                            onOpenCard = onOpenCard,
                        )
                    }
                }

                followedOverview != null -> {
                    DashboardSummaryHeroCard(
                        spec = creditCardsSummaryHeroSpec(
                            overview = followedOverview,
                            locale = locale,
                        ),
                    )
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
                                ownedCards = state.ownedCards,
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
private fun CardsSummaryHeader(
    cardCount: Int,
    onManageCards: () -> Unit,
) {
    DashboardSectionHeader(
        title = stringResource(R.string.dashboard_cards_count_label, cardCount),
        trailingLabel = stringResource(R.string.dashboard_manage_cards),
        onTrailingClick = onManageCards,
    )
}

private fun followedCreditCardsOverview(state: DashboardUiState): CreditCardsOverview? =
    state.followedCreditCardsOverview()
