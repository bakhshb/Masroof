package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baraa.masroof.application.dashboard.DebitCardOverview

@Composable
fun DebitCardDetailScreen(
    debit: DebitCardOverview,
    state: DashboardUiState,
    cardNetwork: com.baraa.masroof.domain.model.CardNetwork?,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onViewAllTransactions: () -> Unit,
) {
    val cardTransactions = DashboardSummaryTransactionFilter.forDebitCard(
        transactions = state.allTransactions,
        bank = debit.bank,
        last4 = debit.last4,
        debitSpendInvolvementByTransactionId = state.transactionDebitSpendInvolvement,
    )
    val title = debit.displayLabel

    DashboardSummaryScaffold(
        title = title,
        state = state,
        onBack = onBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DebitCardDetailSummaryCard(
                debit = debit,
                network = cardNetwork,
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
