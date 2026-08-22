package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold

@Composable
fun SettingsIgnoredMessagesScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    onClearRestoreMessage: () -> Unit,
) {
    MasroofSecondaryScaffold(
        title = stringResource(R.string.settings_ignored_messages_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.settings_ignored_messages_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.restoreMessage?.let { message ->
                Text(
                    stringResource(
                        when (message) {
                            SettingsRestoreMessage.SUCCESS -> R.string.settings_restore_success
                            SettingsRestoreMessage.FAILED -> R.string.settings_restore_failed
                        },
                    ),
                    color = if (message == SettingsRestoreMessage.SUCCESS) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onClearRestoreMessage) {
                    Text(stringResource(R.string.settings_dismiss_message))
                }
            }

            if (state.ignoredMessages.isEmpty()) {
                Text(
                    stringResource(R.string.settings_ignored_messages_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.ignoredMessages.forEach { message ->
                    MasroofCard {
                        Text(
                            message.preview,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = { onRestore(message.rawSmsId) },
                            enabled = !state.updating,
                        ) {
                            Text(stringResource(R.string.settings_restore_message_action))
                        }
                    }
                }
            }
        }
    }
}
