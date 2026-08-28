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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.baraa.masroof.application.dashboard.CardTransactionInvolvementResolver
import com.baraa.masroof.application.dashboard.cashPosition
import com.baraa.masroof.application.dashboard.DebitCardOverview
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSectionTitle
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
    var selectedDebitKey by rememberSaveable { mutableStateOf<String?>(null) }
    val cardNetworks = state.ownedCards.associate { CardOwnershipKey.of(it) to it.cardNetwork }
    val selectedAccount = selectedAccountKey?.let { key ->
        state.ownedAccounts.find { ownedAccountKey(it) == key }
    }
    val selectedDebit = selectedDebitKey?.let { key ->
        state.bankHierarchy?.banks
            ?.flatMap { bank ->
                bank.currentAccounts.flatMap { it.debitCards } + bank.unlinkedDebitCards
            }
            ?.find { CardOwnershipKey.of(it) == key }
    }

    BackHandler {
        when {
            selectedDebit != null -> selectedDebitKey = null
            selectedAccount != null -> selectedAccountKey = null
            else -> onBack()
        }
    }

    when {
        selectedDebit != null -> {
            DebitCardDetailScreen(
                debit = selectedDebit,
                state = state,
                cardNetwork = cardNetworks[CardOwnershipKey.of(selectedDebit)] ?: selectedDebit.network,
                onBack = { selectedDebitKey = null },
                onOpenTransaction = onOpenTransaction,
                onViewAllTransactions = {
                    val cardKey = CardTransactionInvolvementResolver.cardKey(
                        selectedDebit.bank.id,
                        selectedDebit.last4,
                    )
                    val spendTransactionIds = state.transactionDebitSpendInvolvement
                        .filter { (_, cardKeys) -> cardKey in cardKeys }
                        .keys
                    onOpenAllTransactions(
                        TransactionListFilterState(transactionIds = spendTransactionIds),
                    )
                },
            )
        }

        selectedAccount != null -> {
            AccountDetailScreen(
                account = selectedAccount,
                state = state,
                onBack = { selectedAccountKey = null },
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
        }

        else -> {
            AccountsSummaryScreen(
                state = state,
                onBack = onBack,
                onManageAccounts = onManageAccounts,
                onOpenAccount = { account -> selectedAccountKey = ownedAccountKey(account) },
                onOpenDebit = { debit -> selectedDebitKey = CardOwnershipKey.of(debit) },
            )
        }
    }
}

private fun ownedAccountKey(account: OwnedAccountUi): String =
    "${account.bank.id}:${account.maskedNumber}"

@Composable
fun AccountsSummaryScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onManageAccounts: () -> Unit,
    onOpenAccount: (OwnedAccountUi) -> Unit,
    onOpenDebit: (DebitCardOverview) -> Unit,
) {
    DashboardSummaryScaffold(
        title = stringResource(R.string.dashboard_accounts_summary_screen_title),
        state = state,
        onBack = onBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DashboardSummaryHeroCard(
                spec = accountsSummaryHeroSpec(
                    fleet = resolveDashboardAccountsFleet(
                        ownedAccounts = state.ownedAccounts,
                        fleet = state.accountsFleet,
                    ),
                ),
            )

            AccountsSummaryHeader(
                accountCount = state.ownedAccounts.size,
                onManageAccounts = onManageAccounts,
            )

            val hierarchy = state.bankHierarchy
            when {
                state.ownedAccounts.isEmpty() -> {
                    Text(
                        stringResource(R.string.dashboard_accounts_summary_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                hierarchy != null && hierarchy.hasContent -> {
                    hierarchy.banks.forEach { bankTree ->
                        bankTree.savingsAccounts.forEach { node ->
                            resolveOwnedAccountUi(state.ownedAccounts, node.bank, node.maskedNumber)?.let { account ->
                                AccountsSummaryAccountCard(
                                    account = account,
                                    onClick = { onOpenAccount(account) },
                                    onOpenDebit = onOpenDebit,
                                )
                            }
                        }
                        bankTree.walletAccounts.forEach { node ->
                            resolveOwnedAccountUi(state.ownedAccounts, node.bank, node.maskedNumber)?.let { account ->
                                AccountsSummaryAccountCard(
                                    account = account,
                                    onClick = { onOpenAccount(account) },
                                    onOpenDebit = onOpenDebit,
                                )
                            }
                        }
                        bankTree.currentAccounts.forEach { node ->
                            resolveOwnedAccountUi(state.ownedAccounts, node.bank, node.maskedNumber)?.let { account ->
                                AccountsSummaryAccountCard(
                                    account = account,
                                    debitCards = node.debitCards,
                                    onClick = { onOpenAccount(account) },
                                    onOpenDebit = onOpenDebit,
                                )
                            }
                        }
                        bankTree.unlinkedDebitCards.forEach { debit ->
                            AccountsSummaryDebitBranchRow(
                                label = stringResource(
                                    R.string.dashboard_account_unlinked_mada_label,
                                    debit.displayLabel,
                                ),
                                debit = debit,
                                onOpenDebit = onOpenDebit,
                            )
                        }
                    }
                }

                else -> {
                    state.ownedAccounts.forEach { account ->
                        AccountsSummaryAccountCard(
                            account = account,
                            onClick = { onOpenAccount(account) },
                            onOpenDebit = onOpenDebit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountsSummaryHeader(
    accountCount: Int,
    onManageAccounts: () -> Unit,
) {
    DashboardSectionHeader(
        title = stringResource(R.string.dashboard_accounts_count_label, accountCount),
        trailingLabel = stringResource(R.string.dashboard_manage_accounts),
        onTrailingClick = onManageAccounts,
    )
}

private fun resolveOwnedAccountUi(
    ownedAccounts: List<OwnedAccountUi>,
    bank: Bank,
    maskedNumber: String,
): OwnedAccountUi? = ownedAccounts.find { it.bank == bank && it.maskedNumber == maskedNumber }

@Composable
private fun AccountsSummaryAccountCard(
    account: OwnedAccountUi,
    debitCards: List<DebitCardOverview> = emptyList(),
    onClick: () -> Unit,
    onOpenDebit: (DebitCardOverview) -> Unit,
) {
    val extended = MasroofThemeExtras.extendedColors
    val summary = account.periodSummary
    val movement = summary?.cashPosition()
    val remaining = movement?.remaining
    val periodInflow = movement?.inflow
    val periodOutflow = movement?.outflow
    val remainingColor = when {
        remaining == null -> MaterialTheme.colorScheme.onSurfaceVariant
        remaining.amount.signum() > 0 -> extended.inflow
        remaining.amount.signum() < 0 -> extended.outflow
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column {
        MasroofCard(modifier = Modifier.clickable(onClick = onClick)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(DashboardSpacing.entityIconSize)
                        .clip(RoundedCornerShape(DashboardSpacing.entityIconRadius))
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
                        account.displayLabel(),
                        style = DashboardTextStyles.cardTitle,
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
                    DashboardAmountText(
                        amount = remaining?.let { formatLocalizedMoney(it) }
                            ?: stringResource(R.string.dashboard_value_unavailable),
                        role = DashboardAmountRole.Card,
                        color = remainingColor,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (debitCards.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            debitCards.forEach { debit ->
                AccountsSummaryDebitBranchRow(
                    label = debit.displayLabel,
                    debit = debit,
                    onOpenDebit = onOpenDebit,
                )
            }
        }
    }
}

@Composable
private fun AccountsSummaryDebitBranchRow(
    label: String,
    debit: DebitCardOverview,
    onOpenDebit: (DebitCardOverview) -> Unit,
) {
    val extended = MasroofThemeExtras.extendedColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpenDebit(debit) }
            .padding(start = 48.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatLocalizedMoney(debit.salaryPeriodSpendingNet),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = MasroofIcons.periodNext,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = extended.account,
            )
        }
    }
}
