package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.presentation.common.formatCardLast4
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
    val title = stringResource(
        R.string.dashboard_credit_card_last4,
        formatCardLast4(row.last4),
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
            CreditCardSummaryTile(
                row = row,
                salaryPeriodLabel = salaryPeriodLabel,
                zoneId = ZoneId.systemDefault(),
                presentation = CreditCardMetricsPresentation.DetailSpending,
                showBalanceAndDue = true,
                modifier = Modifier.fillMaxWidth(),
            )

            DashboardSummaryTransactionsSection(
                transactions = cardTransactions,
                onOpenTransaction = onOpenTransaction,
                onViewAll = onViewAllTransactions,
            )
        }
    }
}
