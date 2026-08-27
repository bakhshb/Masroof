package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.AccountType
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.common.AccountOwnershipInlinePrompt
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBankAccountsScreen(
    bank: Bank,
    state: SettingsUiState,
    onBack: () -> Unit,
    onConfirmOwned: (ManagedAccountUi) -> Unit,
    onMarkExternal: (ManagedAccountUi) -> Unit,
    onRequestStopTracking: (ManagedAccountUi) -> Unit,
    onResumeTracking: (ManagedAccountUi) -> Unit,
    onDismissStopConfirm: () -> Unit,
    onConfirmStopTracking: () -> Unit,
    onRenameAccount: (ManagedAccountUi) -> Unit,
    onDismissRenameAccount: () -> Unit,
    onSaveAccountName: (String) -> Unit,
    onPickAccountType: (ManagedAccountUi) -> Unit,
    onDismissAccountType: () -> Unit,
    onSelectAccountType: (com.baraa.masroof.domain.model.AccountType) -> Unit,
) {
    val unregisteredAccounts = state.unregisteredAccounts.filter { it.bank == bank }
    val followedAccounts = state.followedAccounts.filter { it.bank == bank }
    val stoppedAccounts = state.stoppedAccounts.filter { it.bank == bank }
    val bankTree = state.bankTree(bank.id)

    SettingsAccountStopConfirmDialog(
        target = state.stopConfirmAccountTarget,
        updating = state.updating,
        onDismiss = onDismissStopConfirm,
        onConfirm = onConfirmStopTracking,
    )
    SettingsRenameAccountDialog(
        target = state.renameAccountTarget,
        updating = state.updating,
        onDismiss = onDismissRenameAccount,
        onSave = onSaveAccountName,
    )
    SettingsAccountTypeDialog(
        target = state.accountTypeTarget,
        updating = state.updating,
        onDismiss = onDismissAccountType,
        onSelect = onSelectAccountType,
    )

    MasroofSecondaryScaffold(
        title = stringResource(R.string.settings_bank_accounts_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                settingsBankLabel(bank),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.settings_accounts_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (followedAccounts.isEmpty() && unregisteredAccounts.isEmpty() && stoppedAccounts.isEmpty()) {
                Text(
                    stringResource(R.string.settings_accounts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (unregisteredAccounts.isNotEmpty()) {
                SettingsAccountGroupTitle(stringResource(R.string.settings_accounts_unregistered))
                unregisteredAccounts.forEach { account ->
                    SettingsRegistryItemCard(
                        icon = MasroofIcons.externalIn,
                        bank = account.bank,
                        title = account.displayLabel,
                        showBankLabel = false,
                        footer = {
                            AccountOwnershipInlinePrompt(
                                enabled = !state.updating,
                                onConfirmOwned = { onConfirmOwned(account) },
                                onMarkExternal = { onMarkExternal(account) },
                            )
                        },
                    )
                }
            }

            if (followedAccounts.isNotEmpty()) {
                SettingsAccountGroupTitle(stringResource(R.string.settings_accounts_followed))
                if (bankTree != null) {
                    bankTree.currentAccountNodes.forEach { node ->
                        SettingsRegistryAccountRow(
                            account = node.account,
                            debitCards = node.debitCards,
                            updating = state.updating,
                            onRequestStopTracking = onRequestStopTracking,
                            onRenameAccount = onRenameAccount,
                            onPickAccountType = onPickAccountType,
                        )
                    }
                    bankTree.savingsAccounts.forEach { account ->
                        SettingsRegistryAccountRow(
                            account = account,
                            updating = state.updating,
                            onRequestStopTracking = onRequestStopTracking,
                            onRenameAccount = onRenameAccount,
                            onPickAccountType = onPickAccountType,
                        )
                    }
                    bankTree.walletAccounts.forEach { account ->
                        SettingsRegistryAccountRow(
                            account = account,
                            updating = state.updating,
                            onRequestStopTracking = onRequestStopTracking,
                            onRenameAccount = onRenameAccount,
                            onPickAccountType = onPickAccountType,
                        )
                    }
                } else {
                    followedAccounts.forEach { account ->
                        SettingsRegistryAccountRow(
                            account = account,
                            updating = state.updating,
                            onRequestStopTracking = onRequestStopTracking,
                            onRenameAccount = onRenameAccount,
                            onPickAccountType = onPickAccountType,
                        )
                    }
                }
            }

            if (stoppedAccounts.isNotEmpty()) {
                SettingsAccountGroupTitle(stringResource(R.string.settings_accounts_stopped))
                stoppedAccounts.forEach { account ->
                    SettingsRegistryItemCard(
                        icon = MasroofIcons.externalIn,
                        bank = account.bank,
                        title = account.displayLabel,
                        showBankLabel = false,
                        endAction = {
                            SettingsResumeTrackingButton(
                                onClick = { onResumeTracking(account) },
                                enabled = !state.updating,
                            )
                        },
                    )
                }
            }

            state.error?.let {
                Text(
                    stringResource(R.string.settings_update_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun SettingsRegistryAccountRow(
    account: ManagedAccountUi,
    debitCards: List<ManagedCardUi> = emptyList(),
    updating: Boolean,
    onRequestStopTracking: (ManagedAccountUi) -> Unit,
    onRenameAccount: (ManagedAccountUi) -> Unit,
    onPickAccountType: (ManagedAccountUi) -> Unit,
) {
    SettingsRegistryItemCard(
        icon = MasroofIcons.externalIn,
        bank = account.bank,
        title = account.displayLabel,
        showBankLabel = false,
        endAction = {
            SettingsStopTrackingButton(
                onClick = { onRequestStopTracking(account) },
                enabled = !updating,
                contentDescription = stringResource(R.string.settings_stop_account_tracking),
            )
        },
        footer = {
            debitCards.forEach { debit ->
                Text(
                    stringResource(R.string.dashboard_account_linked_mada_label, debit.displayLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onRenameAccount(account) },
                    enabled = !updating,
                ) {
                    Text(stringResource(R.string.settings_action_rename))
                }
                TextButton(
                    onClick = { onPickAccountType(account) },
                    enabled = !updating,
                ) {
                    Text(accountTypeLabel(account.accountType))
                }
            }
        },
    )
}

@Composable
internal fun SettingsAccountGroupTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun accountTypeLabel(accountType: AccountType): String =
    when (accountType) {
        AccountType.CURRENT -> stringResource(R.string.settings_account_type_current)
        AccountType.SAVINGS -> stringResource(R.string.settings_account_type_savings)
        AccountType.WALLET -> stringResource(R.string.settings_account_type_wallet)
    }
