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
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
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
            AccountDetailHeroCard(
                remaining = account.accountRemaining,
                periodInflow = account.periodInflow,
                periodOutflow = account.periodOutflow,
            )
            if (summary != null) {
                DashboardFlowBreakdownCard(summary = summary)
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
private fun AccountDetailHeroCard(
    remaining: SignedMoneyAmount?,
    periodInflow: com.baraa.masroof.core.money.Money?,
    periodOutflow: com.baraa.masroof.core.money.Money?,
) {
    val extended = MasroofThemeExtras.extendedColors
    val remainingColor = when {
        remaining == null -> MaterialTheme.colorScheme.onSurfaceVariant
        remaining.amount.signum() > 0 -> extended.inflow
        remaining.amount.signum() < 0 -> extended.outflow
        else -> MaterialTheme.colorScheme.onSurface
    }

    MasroofCard(accent = MasroofCardAccent.Account) {
        Text(
            stringResource(R.string.dashboard_account_remaining_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            remaining?.let { formatLocalizedMoney(it) }
                ?: stringResource(R.string.dashboard_value_unavailable),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = remainingColor,
            ),
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(R.string.dashboard_account_remaining_calculated_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (periodInflow != null && periodOutflow != null) {
            Text(
                stringResource(
                    R.string.dashboard_remaining_formula,
                    formatLocalizedMoney(periodInflow),
                    formatLocalizedMoney(periodOutflow),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
