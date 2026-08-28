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
import com.baraa.masroof.application.dashboard.CardTransactionInvolvementResolver
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.DebitCardOverview
import com.baraa.masroof.presentation.common.MasroofSectionTitle
import java.time.ZoneId

@Composable
fun CardsSummaryRoute(
    viewModel: DashboardViewModel,
    initialSelectedCardKey: String? = null,
    initialSelectedDebitKey: String? = null,
    onInitialSelectionConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onManageCards: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenAllTransactions: (TransactionListFilterState) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var selectedCardKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDebitKey by rememberSaveable { mutableStateOf<String?>(null) }
    val followedFacilities = state.followedCreditFacilities()
    val cardNetworks = state.ownedCards.associate { CardOwnershipKey.of(it) to it.cardNetwork }

    androidx.compose.runtime.LaunchedEffect(initialSelectedCardKey, initialSelectedDebitKey) {
        when {
            initialSelectedCardKey != null -> {
                selectedCardKey = initialSelectedCardKey
                onInitialSelectionConsumed()
            }
            initialSelectedDebitKey != null -> {
                selectedDebitKey = initialSelectedDebitKey
                onInitialSelectionConsumed()
            }
        }
    }

    val selectedCard = selectedCardKey?.let { key ->
        followedFacilities?.facilities
            ?.flatMap { it.allCards }
            ?.find { CardOwnershipKey.of(it) == key }
    }
    val selectedDebit = selectedDebitKey?.let { key ->
        followedFacilities?.debitCards?.find { CardOwnershipKey.of(it) == key }
    }

    BackHandler {
        when {
            selectedDebit != null -> selectedDebitKey = null
            selectedCard != null -> selectedCardKey = null
            else -> onBack()
        }
    }

    when {
        selectedDebit != null -> {
            DebitCardDetailScreen(
                debit = selectedDebit,
                state = state,
                cardNetwork = cardNetworks[CardOwnershipKey.of(selectedDebit)] ?: selectedDebit.network,
                onBack = { selectedDebitKey = null },
                onOpenTransaction = onOpenTransaction,
                onViewAllTransactions = {
                    val cardKey = CardTransactionInvolvementResolver.cardKey(
                        selectedDebit.bank.id,
                        selectedDebit.last4,
                    )
                    val spendTransactionIds = state.transactionDebitSpendInvolvement
                        .filter { (_, cardKeys) -> cardKey in cardKeys }
                        .keys
                    onOpenAllTransactions(
                        TransactionListFilterState(transactionIds = spendTransactionIds),
                    )
                },
            )
        }

        selectedCard != null -> {
            CardDetailScreen(
                row = selectedCard,
                salaryPeriodLabel = followedFacilities?.facilities
                    ?.firstOrNull { facility ->
                        facility.allCards.any { CardOwnershipKey.of(it) == selectedCardKey }
                    }
                    ?.salaryPeriodLabel,
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
                onOpenCard = { row -> selectedCardKey = CardOwnershipKey.of(row) },
                onOpenDebit = { debit -> selectedDebitKey = CardOwnershipKey.of(debit) },
                cardNetworksByLast4 = cardNetworks,
            )
        }
    }
}

@Composable
fun CardsSummaryScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onManageCards: () -> Unit,
    onOpenCard: (CreditCardDashboardRow) -> Unit,
    onOpenDebit: (DebitCardOverview) -> Unit,
    cardNetworksByLast4: Map<String, com.baraa.masroof.domain.model.CardNetwork?>,
) {
    val followedFacilities = state.followedCreditFacilities()
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
            if (followedFacilities == null || !followedFacilities.hasContent) {
                Text(
                    stringResource(R.string.dashboard_cards_summary_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DashboardSummaryHeroCard(
                    spec = creditFacilitiesSummaryHeroSpec(
                        overview = followedFacilities,
                        locale = locale,
                    ),
                )
                CardsSummaryHeader(
                    cardCount = followedFacilities.facilities.size + followedFacilities.debitCards.size,
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
                followedFacilities.debitCards.forEach { debit ->
                    DebitCardSummaryTile(
                        debit = debit,
                        network = cardNetworksByLast4[CardOwnershipKey.of(debit)] ?: debit.network,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenDebit(debit) },
                        presentation = DebitCardTilePresentation.List,
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
