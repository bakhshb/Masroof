package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baraa.masroof.application.dashboard.LoanOverview

@Composable
fun LoanDetailScreen(
    loan: LoanOverview,
    state: DashboardUiState,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onViewAllTransactions: () -> Unit,
) {
    val loanTransactions = DashboardSummaryTransactionFilter.forLoan(
        transactions = state.allTransactions,
        bank = loan.bank,
        loanType = loan.loanType,
        involvementByTransactionId = state.transactionLoanInvolvement,
    )

    DashboardSummaryScaffold(
        title = loan.displayLabel,
        state = state,
        onBack = onBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoanDetailSummaryCard(
                loan = loan,
                modifier = Modifier.fillMaxWidth(),
            )

            DashboardSummaryTransactionsSection(
                transactions = loanTransactions,
                onOpenTransaction = onOpenTransaction,
                onViewAll = onViewAllTransactions,
                ownedCards = state.ownedCards,
            )
        }
    }
}
