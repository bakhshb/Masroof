package com.baraa.masroof.presentation.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.formatCardLast4

@Composable
fun SettingsStopConfirmDialog(
    target: ManagedCardUi?,
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
        title = { Text(stringResource(R.string.settings_stop_confirm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.settings_stop_confirm_body,
                    formatCardLast4(target.last4),
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
