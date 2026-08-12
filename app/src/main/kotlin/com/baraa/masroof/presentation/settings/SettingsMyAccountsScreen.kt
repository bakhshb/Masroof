package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.common.AccountOwnershipInlinePrompt
import com.baraa.masroof.presentation.common.BackNavigationIcon
import com.baraa.masroof.presentation.common.IconTextButton
import com.baraa.masroof.presentation.common.IconTextButtonOutlined
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
) {
    SettingsAccountStopConfirmDialog(
        target = state.stopConfirmAccountTarget,
        updating = state.updating,
        onDismiss = onDismissStopConfirm,
        onConfirm = onConfirmStopTracking,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_accounts_section)) },
                navigationIcon = {
                    BackNavigationIcon(
                        onClick = onBack,
                        contentDescription = stringResource(R.string.settings_back),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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
                    ManagedAccountPanel(account = account) {
                        AccountOwnershipInlinePrompt(
                            enabled = !state.updating,
                            onConfirmOwned = { onConfirmOwned(account) },
                            onMarkExternal = { onMarkExternal(account) },
                        )
                    }
                }
            }

            if (state.followedAccounts.isNotEmpty()) {
                SettingsAccountGroupTitle(stringResource(R.string.settings_accounts_followed))
                state.followedAccounts.forEach { account ->
                    ManagedAccountPanel(account = account) {
                        IconTextButtonOutlined(
                            onClick = { onRequestStopTracking(account) },
                            icon = MasroofIcons.warning,
                            text = stringResource(R.string.settings_stop_account_tracking),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (state.stoppedAccounts.isNotEmpty()) {
                SettingsAccountGroupTitle(stringResource(R.string.settings_accounts_stopped))
                state.stoppedAccounts.forEach { account ->
                    ManagedAccountPanel(account = account) {
                        IconTextButton(
                            onClick = { onResumeTracking(account) },
                            enabled = !state.updating,
                            icon = MasroofIcons.success,
                            text = stringResource(R.string.settings_resume_account_tracking),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
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
private fun SettingsAccountGroupTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun ManagedAccountPanel(
    account: ManagedAccountUi,
    actions: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MasroofIcons.externalIn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Column {
                    Text(
                        bankLabel(account.bank),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            R.string.onboarding_account_suffix,
                            account.maskedNumber,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            actions()
        }
    }
}

@Composable
private fun bankLabel(bank: Bank): String =
    if (bank == Bank.BANK_ALJAZIRA) {
        stringResource(R.string.bank_aljazira)
    } else {
        stringResource(R.string.bank_unknown)
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
