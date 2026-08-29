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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.MerchantSpendingRow
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofSectionTitle
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@Composable
fun MerchantSpendingSummaryRoute(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenAllTransactions: (TransactionListFilterState) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var selectedMerchantKey by rememberSaveable { mutableStateOf<String?>(null) }
    val overview = state.merchantSpending
    val selected = selectedMerchantKey?.let { key -> overview.merchants.find { it.merchantKey == key } }

    BackHandler {
        if (selected != null) selectedMerchantKey = null else onBack()
    }

    if (selected != null) {
        MerchantSpendingDetailScreen(
            row = selected,
            state = state,
            onBack = { selectedMerchantKey = null },
            onOpenTransaction = onOpenTransaction,
            onOpenAllTransactions = {
                onOpenAllTransactions(TransactionListFilterState(transactionIds = selected.transactionIds))
            },
        )
    } else {
        MerchantSpendingSummaryScreen(
            state = state,
            onBack = onBack,
            onOpenMerchant = { selectedMerchantKey = it.merchantKey },
        )
    }
}

@Composable
fun MerchantSpendingSummaryScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onOpenMerchant: (MerchantSpendingRow) -> Unit,
) {
    val overview = state.merchantSpending
    DashboardSummaryScaffold(
        title = stringResource(R.string.dashboard_merchants_summary_title),
        state = state,
        onBack = onBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap),
        ) {
            MasroofSectionTitle(
                title = stringResource(R.string.dashboard_merchants_summary_title),
            )
            if (!overview.hasContent) {
                Text(
                    stringResource(R.string.dashboard_merchants_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.dashboard_merchants_qualifying_count, overview.merchants.size),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                overview.merchants.forEach { row ->
                    MerchantSpendingListRow(row = row, onClick = { onOpenMerchant(row) })
                }
            }
        }
    }
}

@Composable
private fun MerchantSpendingListRow(
    row: MerchantSpendingRow,
    onClick: () -> Unit,
) {
    MasroofCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        R.string.dashboard_merchants_transaction_count,
                        row.purchaseTransactionCount,
                    ),
                    modifier = Modifier.padding(top = MasroofSpacing.inlineGap),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                formatLocalizedMoney(row.totalSpent),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MasroofThemeExtras.extendedColors.outflow,
            )
        }
    }
}

@Composable
private fun MerchantSpendingDetailScreen(
    row: MerchantSpendingRow,
    state: DashboardUiState,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenAllTransactions: () -> Unit,
) {
    val transactions = state.allTransactions.filter { it.id in row.transactionIds }
    DashboardSummaryScaffold(
        title = row.displayName,
        state = state,
        onBack = onBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap),
        ) {
            MerchantSpendingListRow(row = row, onClick = {})
            DashboardSummaryTransactionsSection(
                transactions = transactions,
                onOpenTransaction = onOpenTransaction,
                onViewAll = onOpenAllTransactions,
                ownedCards = state.ownedCards,
            )
        }
    }
}
