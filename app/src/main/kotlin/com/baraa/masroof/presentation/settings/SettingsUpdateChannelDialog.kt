package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.update.UpdateChannel

@Composable
fun SettingsUpdateChannelDialog(
    selectedChannel: UpdateChannel,
    onDismiss: () -> Unit,
    onSelectChannel: (UpdateChannel) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_update_channel_title)) },
        text = {
            Column {
                updateChannelOption(
                    channel = UpdateChannel.STABLE,
                    label = stringResource(R.string.settings_update_channel_stable),
                    description = stringResource(R.string.settings_update_channel_stable_hint),
                    selectedChannel = selectedChannel,
                    onSelectChannel = onSelectChannel,
                )
                updateChannelOption(
                    channel = UpdateChannel.NIGHTLY,
                    label = stringResource(R.string.settings_update_channel_nightly),
                    description = stringResource(R.string.settings_update_channel_nightly_hint),
                    selectedChannel = selectedChannel,
                    onSelectChannel = onSelectChannel,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun updateChannelOption(
    channel: UpdateChannel,
    label: String,
    description: String,
    selectedChannel: UpdateChannel,
    onSelectChannel: (UpdateChannel) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = channel == selectedChannel,
                onClick = { onSelectChannel(channel) },
                role = Role.RadioButton,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = channel == selectedChannel,
            onClick = { onSelectChannel(channel) },
        )
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .fillMaxWidth(),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
fun updateChannelLabel(channel: UpdateChannel): String =
    when (channel) {
        UpdateChannel.STABLE -> stringResource(R.string.settings_update_channel_stable)
        UpdateChannel.NIGHTLY -> stringResource(R.string.settings_update_channel_nightly)
    }
