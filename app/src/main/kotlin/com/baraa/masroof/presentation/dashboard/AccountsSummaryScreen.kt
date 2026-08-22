package com.baraa.masroof.presentation.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.AccountsSummary
import com.baraa.masroof.application.dashboard.OwnedAccount
import com.baraa.masroof.application.dashboard.externalMovement
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSectionTitle
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@Composable
fun AccountsSummaryRoute(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onManageAccounts: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenAllTransactions: (TransactionListFilterState) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var selectedAccountKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedAccount = selectedAccountKey?.let { key ->
        state.ownedAccounts.find { ownedAccountKey(it) == key }
    }

    BackHandler {
        if (selectedAccount != null) {
            selectedAccountKey = null
        } else {
            onBack()
        }
    }

    if (selectedAccount != null) {
        AccountDetailScreen(
            account = selectedAccount,
            state = state,
            onBack = { selectedAccountKey = null },
            onPrevious = viewModel::goToPreviousPeriod,
            onNext = viewModel::goToNextPeriod,
            onCurrent = viewModel::goToCurrentPeriod,
            onOpenTransaction = onOpenTransaction,
            onViewAllTransactions = {
                onOpenAllTransactions(
                    TransactionListFilterState(
                        accountContainerIds = setOf(
                            FinancialContainerIdFactory.accountId(
                                selectedAccount.bank,
                                selectedAccount.maskedNumber,
                            ),
                        ),
                    ),
                )
            },
        )
    } else {
        AccountsSummaryScreen(
            state = state,
            onBack = onBack,
            onPrevious = viewModel::goToPreviousPeriod,
            onNext = viewModel::goToNextPeriod,
            onCurrent = viewModel::goToCurrentPeriod,
            onManageAccounts = onManageAccounts,
            onOpenAccount = { account -> selectedAccountKey = ownedAccountKey(account) },
        )
    }
}

private fun ownedAccountKey(account: OwnedAccountUi): String =
    "${account.bank.id}:${account.maskedNumber}"

@Composable
fun AccountsSummaryScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
    onManageAccounts: () -> Unit,
    onOpenAccount: (OwnedAccountUi) -> Unit,
) {
    DashboardSummaryScaffold(
        title = stringResource(R.string.dashboard_accounts_summary_screen_title),
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
            AccountsSummaryHeroCard(ownedAccounts = state.ownedAccounts)

            AccountsSummaryHeader(
                accountCount = state.ownedAccounts.size,
                onManageAccounts = onManageAccounts,
            )

            if (state.ownedAccounts.isEmpty()) {
                Text(
                    stringResource(R.string.dashboard_accounts_summary_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.ownedAccounts.forEach { account ->
                    AccountsSummaryAccountCard(
                        account = account,
                        onClick = { onOpenAccount(account) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountsSummaryHeroCard(ownedAccounts: List<OwnedAccountUi>) {
    val extended = MasroofThemeExtras.extendedColors
    val fleet = AccountsSummary(
        accounts = ownedAccounts.mapNotNull { account ->
            account.periodSummary?.let { summary ->
                OwnedAccount.from(
                    bank = account.bank,
                    maskedNumber = account.maskedNumber,
                    summary = summary,
                )
            }
        },
    )
    val totalRemaining = fleet.totalRemaining
    val totalInflow = fleet.totalInflow
    val totalOutflow = fleet.totalOutflow
    val remainingColor = when {
        totalRemaining == null -> MaterialTheme.colorScheme.onSurfaceVariant
        totalRemaining.amount.signum() > 0 -> extended.inflow
        totalRemaining.amount.signum() < 0 -> extended.outflow
        else -> MaterialTheme.colorScheme.onSurface
    }

    MasroofCard(accent = MasroofCardAccent.Account) {
        Text(
            stringResource(R.string.dashboard_accounts_remaining_total_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (totalRemaining != null) {
                formatLocalizedMoney(totalRemaining)
            } else {
                stringResource(R.string.dashboard_value_unavailable)
            },
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = remainingColor,
            ),
            modifier = Modifier.padding(top = 8.dp),
        )
        if (totalInflow != null && totalOutflow != null) {
            Text(
                stringResource(
                    R.string.dashboard_remaining_formula,
                    formatLocalizedMoney(totalInflow),
                    formatLocalizedMoney(totalOutflow),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            stringResource(R.string.dashboard_accounts_fleet_total_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun AccountsSummaryHeader(
    accountCount: Int,
    onManageAccounts: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MasroofSectionTitle(
            title = stringResource(R.string.dashboard_accounts_count_label, accountCount),
        )
        TextButton(onClick = onManageAccounts) {
            Text(stringResource(R.string.dashboard_manage_accounts))
        }
    }
}

@Composable
private fun AccountsSummaryAccountCard(
    account: OwnedAccountUi,
    onClick: () -> Unit,
) {
    val extended = MasroofThemeExtras.extendedColors
    val summary = account.periodSummary
    val movement = summary?.externalMovement()
    val remaining = movement?.remaining
    val periodInflow = movement?.inflow
    val periodOutflow = movement?.outflow
    val remainingColor = when {
        remaining == null -> MaterialTheme.colorScheme.onSurfaceVariant
        remaining.amount.signum() > 0 -> extended.inflow
        remaining.amount.signum() < 0 -> extended.outflow
        else -> MaterialTheme.colorScheme.onSurface
    }

    MasroofCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(extended.accountSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MasroofIcons.moneyMovement,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = extended.account,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(
                        R.string.dashboard_account_item,
                        formatCardLast4(account.maskedNumber),
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.dashboard_account_remaining_calculated_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (periodInflow != null && periodOutflow != null) {
                    Text(
                        stringResource(
                            R.string.dashboard_account_period_in_out,
                            formatLocalizedMoney(periodInflow),
                            formatLocalizedMoney(periodOutflow),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.dashboard_account_remaining_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    remaining?.let { formatLocalizedMoney(it) }
                        ?: stringResource(R.string.dashboard_value_unavailable),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = remainingColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
