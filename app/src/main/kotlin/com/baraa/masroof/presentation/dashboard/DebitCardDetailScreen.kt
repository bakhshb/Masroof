package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.DebitCardOverview
import com.baraa.masroof.presentation.common.formatCardLast4

@Composable
fun DebitCardDetailScreen(
    debit: DebitCardOverview,
    state: DashboardUiState,
    cardNetwork: com.baraa.masroof.domain.model.CardNetwork?,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onViewAllTransactions: () -> Unit,
) {
    val cardTransactions = DashboardSummaryTransactionFilter.forCard(
        transactions = state.allTransactions,
        bank = debit.bank,
        last4 = debit.last4,
        cardInvolvementByTransactionId = state.transactionCardInvolvement,
    )
    val title = stringResource(
        R.string.dashboard_credit_card_last4,
        formatCardLast4(debit.last4),
    )

    DashboardSummaryScaffold(
        title = title,
        state = state,
        onBack = onBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DebitCardSummaryTile(
                debit = debit,
                network = cardNetwork,
                modifier = Modifier.fillMaxWidth(),
                showNavigationIcon = false,
            )

            DashboardSummaryTransactionsSection(
                transactions = cardTransactions,
                onOpenTransaction = onOpenTransaction,
                onViewAll = onViewAllTransactions,
            )
        }
    }
}
