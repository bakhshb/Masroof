package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.application.dashboard.CurrentAccountTransactionScope
import com.baraa.masroof.presentation.common.MasroofSectionTitle

object DashboardSummaryTransactionFilter {
    fun forAccount(
        transactions: List<TransactionPreviewUi>,
        bank: Bank,
        maskedNumber: String,
        involvementByTransactionId: Map<String, Set<String>> = emptyMap(),
    ): List<TransactionPreviewUi> {
        val containerId = FinancialContainerIdFactory.accountId(bank, maskedNumber)
        val last4s = CurrentAccountTransactionScope.ownedAccountLast4sFromMaskedNumbers(listOf(maskedNumber))
        return transactions.mapNotNull { tx ->
            val involvesAccount = matchesAccountContainer(tx.sourceContainerId, containerId, last4s) ||
                matchesAccountContainer(tx.destinationContainerId, containerId, last4s) ||
                involvementByTransactionId[tx.id].orEmpty().any { involvedId ->
                    matchesAccountContainer(involvedId, containerId, last4s)
                }
            if (!involvesAccount) return@mapNotNull null
            val direction = AccountTransactionPresentation.directionForAccount(tx, containerId, last4s)
            if (direction == tx.direction) tx else tx.copy(direction = direction)
        }
    }

    private fun matchesAccountContainer(
        containerId: String?,
        ownedContainerId: String?,
        ownedLast4s: Set<String>,
    ): Boolean {
        if (containerId == null || ownedContainerId == null) return false
        if (containerId == ownedContainerId) return true
        if (!containerId.startsWith("account:")) return false
        return containerId.substringAfterLast(':') in ownedLast4s
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
    onViewAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MasroofSectionTitle(
                title = stringResource(R.string.dashboard_summary_transactions_title, transactions.size),
            )
            if (onViewAll != null && transactions.isNotEmpty()) {
                TextButton(onClick = onViewAll) {
                    Text(stringResource(R.string.dashboard_summary_view_all_transactions))
                }
            }
        }
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
