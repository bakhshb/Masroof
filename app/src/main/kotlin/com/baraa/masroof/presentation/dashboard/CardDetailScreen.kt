package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import java.time.ZoneId

@Composable
fun CardDetailScreen(
    row: CreditCardDashboardRow,
    salaryPeriodLabel: String?,
    state: DashboardUiState,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onViewAllTransactions: () -> Unit,
) {
    val cardTransactions = DashboardSummaryTransactionFilter.forCard(
        transactions = state.allTransactions,
        bank = row.bank,
        last4 = row.last4,
        cardInvolvementByTransactionId = state.transactionCardInvolvement,
    )
    val title = row.displayLabel(state.ownedCards)

    DashboardSummaryScaffold(
        title = title,
        state = state,
        onBack = onBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CreditCardSummaryTile(
                row = row,
                salaryPeriodLabel = salaryPeriodLabel,
                zoneId = ZoneId.systemDefault(),
                presentation = CreditCardMetricsPresentation.DetailSpending,
                showBalanceAndDue = true,
                ownedCards = state.ownedCards,
                modifier = Modifier.fillMaxWidth(),
            )

            DashboardSummaryTransactionsSection(
                transactions = cardTransactions,
                onOpenTransaction = onOpenTransaction,
                onViewAll = onViewAllTransactions,
                ownedCards = state.ownedCards,
            )
        }
    }
}
