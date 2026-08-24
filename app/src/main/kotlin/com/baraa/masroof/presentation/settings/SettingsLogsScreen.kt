package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.logging.AppLogEntry
import com.baraa.masroof.application.logging.AppLogLevel
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsLogsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onRequestExport: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val entries by viewModel.logEntries.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.logMessage?.let { resolveLogMessage(it) }

    LaunchedEffect(Unit) {
        viewModel.refreshLogs()
    }

    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.clearLogMessage()
        }
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        MasroofSecondaryScaffold(
            title = stringResource(R.string.settings_logs_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.settings_back),
        ) { contentModifier ->
            Column(
                modifier = contentModifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.settings_logs_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onRequestExport,
                        enabled = entries.isNotEmpty() && !state.exportingLogs,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.settings_logs_export))
                    }
                    OutlinedButton(
                        onClick = viewModel::clearLogs,
                        enabled = entries.isNotEmpty() && !state.exportingLogs,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.settings_logs_clear))
                    }
                }

                if (state.exportingLogs) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                if (entries.isEmpty()) {
                    MasroofCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.settings_logs_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(entries.asReversed(), key = { it.id }) { entry ->
                            LogEntryCard(entry = entry)
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun LogEntryCard(entry: AppLogEntry) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }
    val timestamp = remember(entry.timestampEpochMs) {
        formatter.format(Instant.ofEpochMilli(entry.timestampEpochMs).atZone(ZoneId.systemDefault()))
    }
    val levelColor = when (entry.level) {
        AppLogLevel.INFO -> MaterialTheme.colorScheme.primary
        AppLogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        AppLogLevel.ERROR -> MaterialTheme.colorScheme.error
    }

    MasroofCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$timestamp · ${entry.category}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            entry.level.name,
            style = MaterialTheme.typography.labelMedium,
            color = levelColor,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            entry.message,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun resolveLogMessage(message: LogMessage): String =
    when (message) {
        LogMessage.EXPORT_SUCCESS -> stringResource(R.string.settings_logs_export_success)
        LogMessage.EXPORT_FAILED -> stringResource(R.string.settings_logs_export_failed)
        LogMessage.CLEARED -> stringResource(R.string.settings_logs_cleared)
    }
