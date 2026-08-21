package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CurrentAccountSummary
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

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
                AccountDetailHeroCard(summary = summary)
                DashboardFlowBreakdownCard(summary = summary)
            } else {
                Text(
                    stringResource(R.string.dashboard_account_detail_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun AccountDetailHeroCard(summary: CurrentAccountSummary) {
    val extended = MasroofThemeExtras.extendedColors
    val net = summary.netMovement
    val netColor = when {
        net.amount.signum() > 0 -> extended.inflow
        net.amount.signum() < 0 -> extended.outflow
        else -> MaterialTheme.colorScheme.primary
    }

    MasroofCard(accent = MasroofCardAccent.Account) {
        Text(
            stringResource(R.string.dashboard_account_period_net_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatLocalizedMoney(net),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = netColor,
            ),
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(
                R.string.dashboard_remaining_formula,
                formatLocalizedMoney(summary.totalInflow),
                formatLocalizedMoney(summary.totalOutflow),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
