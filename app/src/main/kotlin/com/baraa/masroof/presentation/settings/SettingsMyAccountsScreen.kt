package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.AccountType
import com.baraa.masroof.presentation.common.AccountOwnershipInlinePrompt
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
import com.baraa.masroof.presentation.common.MasroofIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMyAccountsScreen(
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
        title = stringResource(R.string.settings_accounts_section),
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
                stringResource(R.string.settings_accounts_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (
                state.followedAccounts.isEmpty() &&
                state.unregisteredAccounts.isEmpty() &&
                state.stoppedAccounts.isEmpty()
            ) {
                Text(
                    stringResource(R.string.settings_accounts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.unregisteredAccounts.isNotEmpty()) {
                SettingsAccountGroupTitle(stringResource(R.string.settings_accounts_unregistered))
                state.unregisteredAccounts.forEach { account ->
                    SettingsRegistryItemCard(
                        icon = MasroofIcons.externalIn,
                        bank = account.bank,
                        title = account.displayLabel,
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

            if (state.followedAccounts.isNotEmpty()) {
                SettingsAccountGroupTitle(stringResource(R.string.settings_accounts_followed))
                state.followedAccounts.forEach { account ->
                    SettingsRegistryItemCard(
                        icon = MasroofIcons.externalIn,
                        bank = account.bank,
                        title = account.displayLabel,
                        endAction = {
                            SettingsStopTrackingButton(
                                onClick = { onRequestStopTracking(account) },
                                enabled = !state.updating,
                                contentDescription = stringResource(R.string.settings_stop_account_tracking),
                            )
                        },
                        footer = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { onRenameAccount(account) },
                                    enabled = !state.updating,
                                ) {
                                    Text(stringResource(R.string.settings_action_rename))
                                }
                                TextButton(
                                    onClick = { onPickAccountType(account) },
                                    enabled = !state.updating,
                                ) {
                                    Text(accountTypeLabel(account.accountType))
                                }
                            }
                        },
                    )
                }
            }

            if (state.stoppedAccounts.isNotEmpty()) {
                SettingsAccountGroupTitle(stringResource(R.string.settings_accounts_stopped))
                state.stoppedAccounts.forEach { account ->
                    SettingsRegistryItemCard(
                        icon = MasroofIcons.externalIn,
                        bank = account.bank,
                        title = account.displayLabel,
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
private fun accountTypeLabel(accountType: AccountType): String =
    when (accountType) {
        AccountType.CURRENT -> stringResource(R.string.settings_account_type_current)
        AccountType.SAVINGS -> stringResource(R.string.settings_account_type_savings)
        AccountType.WALLET -> stringResource(R.string.settings_account_type_wallet)
    }

@Composable
fun SettingsAccountTypeDialog(
    target: ManagedAccountUi?,
    updating: Boolean,
    onDismiss: () -> Unit,
    onSelect: (AccountType) -> Unit,
) {
    target ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_account_type_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AccountType.entries.forEach { type ->
                    TextButton(
                        onClick = { onSelect(type) },
                        enabled = !updating && type != target.accountType,
                    ) {
                        Text(accountTypeLabel(type))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !updating) {
                Text(stringResource(R.string.settings_action_cancel))
            }
        },
    )
}

@Composable
private fun SettingsAccountGroupTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
fun SettingsAccountStopConfirmDialog(
    target: ManagedAccountUi?,
    updating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    target ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = MasroofIcons.warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.settings_stop_account_confirm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.settings_stop_account_confirm_body,
                    target.maskedNumber,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !updating) {
                Text(stringResource(R.string.settings_stop_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
