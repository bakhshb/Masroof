package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.AccountType
import com.baraa.masroof.presentation.common.MasroofIcons

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

@Composable
private fun accountTypeLabel(accountType: AccountType): String =
    when (accountType) {
        AccountType.CURRENT -> stringResource(R.string.settings_account_type_current)
        AccountType.SAVINGS -> stringResource(R.string.settings_account_type_savings)
        AccountType.WALLET -> stringResource(R.string.settings_account_type_wallet)
    }
