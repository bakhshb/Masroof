package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.formatCardLast4

@Composable
fun AccountDetailScreen(
    account: OwnedAccountUi,
    state: DashboardUiState,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onViewAllTransactions: () -> Unit,
) {
    val summary = account.periodSummary
    val accountTransactions = DashboardSummaryTransactionFilter.forAccount(
        transactions = state.allTransactions,
        bank = account.bank,
        maskedNumber = account.maskedNumber,
    )
    val title = stringResource(
        R.string.dashboard_account_item,
        formatCardLast4(account.maskedNumber),
    )

    DashboardSummaryScaffold(
        title = title,
        state = state,
        onBack = onBack,
        onPrevious = onPrevious,
        onNext = onNext,
        onCurrent = onCurrent,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (summary != null) {
                CurrentAccountSection(
                    summary = summary,
                    accountBadge = formatCardLast4(account.maskedNumber),
                    presentationMode = AccountFlowPresentationMode.ExternalMovement,
                    showSectionHeader = false,
                )
            }

            DashboardSummaryTransactionsSection(
                transactions = accountTransactions,
                onOpenTransaction = onOpenTransaction,
                onViewAll = onViewAllTransactions,
            )
        }
    }
}
