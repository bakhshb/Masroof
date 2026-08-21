package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.common.MasroofSectionTitle

object DashboardSummaryTransactionFilter {
    fun forAccount(
        transactions: List<TransactionPreviewUi>,
        bank: Bank,
        maskedNumber: String,
    ): List<TransactionPreviewUi> {
        val containerId = FinancialContainerIdFactory.accountId(bank, maskedNumber)
        return transactions.filter { tx ->
            tx.sourceContainerId == containerId || tx.destinationContainerId == containerId
        }
    }

    fun forCard(
        transactions: List<TransactionPreviewUi>,
        last4: String,
    ): List<TransactionPreviewUi> = transactions.filter { it.cardLast4 == last4 }
}

@Composable
fun DashboardSummaryTransactionsSection(
    transactions: List<TransactionPreviewUi>,
    onOpenTransaction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MasroofSectionTitle(
            title = stringResource(R.string.dashboard_summary_transactions_title, transactions.size),
        )
        if (transactions.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_summary_transactions_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            transactions.forEach { row ->
                DashboardRecentTransactionRow(
                    row = row,
                    onClick = { onOpenTransaction(row.id) },
                )
            }
        }
    }
}
