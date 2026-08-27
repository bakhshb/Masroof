package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.logging.AppLogEntry
import com.baraa.masroof.application.logging.AppLogLevel
import com.baraa.masroof.presentation.common.IconLabelRow
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
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
    var selectedFilter by rememberSaveable { mutableStateOf(SettingsLogFilter.ALL) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshLogs()
    }

    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.clearLogMessage()
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_logs_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_logs_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearLogs()
                    },
                ) {
                    Text(
                        stringResource(R.string.settings_logs_clear_confirm_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    val locale = remember(state.languageTag) {
        AppLocale.displayLocale(state.languageTag)
    }
    val zoneId = remember { ZoneId.systemDefault() }
    val filteredEntries = remember(entries, selectedFilter) {
        filterLogEntries(entries, selectedFilter)
    }
    val todayLabel = stringResource(R.string.settings_logs_day_today)
    val yesterdayLabel = stringResource(R.string.settings_logs_day_yesterday)
    val timestampLabels = LogTimestampLabels(
        justNow = stringResource(R.string.settings_log_time_just_now),
        minutesAgoFormat = stringResource(R.string.settings_log_time_minutes_ago),
        hoursAgoFormat = stringResource(R.string.settings_log_time_hours_ago),
    )
    val listItems = remember(filteredEntries, state.languageTag, todayLabel, yesterdayLabel) {
        groupLogEntriesByDay(
            entries = filteredEntries,
            zoneId = zoneId,
            todayLabel = todayLabel,
            yesterdayLabel = yesterdayLabel,
            dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MasroofSecondaryScaffold(
            title = stringResource(R.string.settings_logs_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.settings_back),
            actions = {
                IconButton(
                    onClick = { showClearConfirm = true },
                    enabled = entries.isNotEmpty() && !state.exportingLogs,
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.settings_logs_clear),
                        tint = if (entries.isNotEmpty()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
        ) { contentModifier ->
            Column(
                modifier = contentModifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_logs_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            stringResource(
                                R.string.settings_logs_status,
                                entries.size,
                                LOG_MAX_ENTRIES,
                                LOG_RETENTION_DAYS,
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                LogFilterChips(
                    selected = selectedFilter,
                    onSelected = { selectedFilter = it },
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
                        onClick = { showClearConfirm = true },
                        enabled = entries.isNotEmpty() && !state.exportingLogs,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
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

                when {
                    entries.isEmpty() -> LogEmptyState()
                    filteredEntries.isEmpty() -> LogFilteredEmptyState()
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(
                                items = listItems,
                                key = { item ->
                                    when (item) {
                                        is SettingsLogListItem.DayHeader -> "header-${item.label}"
                                        is SettingsLogListItem.Entry -> "entry-${item.entry.id}"
                                    }
                                },
                            ) { item ->
                                when (item) {
                                    is SettingsLogListItem.DayHeader ->
                                        Text(
                                            item.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                        )
                                    is SettingsLogListItem.Entry ->
                                        LogEntryCard(
                                            entry = item.entry,
                                            locale = locale,
                                            zoneId = zoneId,
                                            timestampLabels = timestampLabels,
                                        )
                                }
                            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogFilterChips(
    selected: SettingsLogFilter,
    onSelected: (SettingsLogFilter) -> Unit,
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsLogFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(logFilterLabel(filter)) },
                colors = chipColors,
            )
        }
    }
}

@Composable
private fun logFilterLabel(filter: SettingsLogFilter): String =
    when (filter) {
        SettingsLogFilter.ALL -> stringResource(R.string.settings_logs_filter_all)
        SettingsLogFilter.ERRORS -> stringResource(R.string.settings_logs_filter_errors)
        SettingsLogFilter.WARNINGS -> stringResource(R.string.settings_logs_filter_warnings)
        SettingsLogFilter.SMS_PIPELINE -> stringResource(R.string.settings_logs_filter_sms)
        SettingsLogFilter.UPDATES -> stringResource(R.string.settings_logs_filter_updates)
    }

@Composable
private fun LogEmptyState() {
    MasroofCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconLabelRow(
                icon = MasroofIcons.recentTransactions,
                label = stringResource(R.string.settings_logs_empty_title),
                iconTint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.settings_logs_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LogFilteredEmptyState() {
    MasroofCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.settings_logs_filter_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LogEntryCard(
    entry: AppLogEntry,
    locale: Locale,
    zoneId: ZoneId,
    timestampLabels: LogTimestampLabels,
) {
    val extendedColors = MasroofThemeExtras.extendedColors
    val accent = when (entry.level) {
        AppLogLevel.WARN -> MasroofCardAccent.Liability
        else -> MasroofCardAccent.None
    }
    val accentColor = when (entry.level) {
        AppLogLevel.INFO -> MaterialTheme.colorScheme.primary
        AppLogLevel.WARN -> extendedColors.liability
        AppLogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    val timestamp = remember(entry.timestampEpochMs, timestampLabels) {
        formatLogTimestamp(entry.timestampEpochMs, zoneId, locale, timestampLabels)
    }

    MasroofCard(
        modifier = Modifier.fillMaxWidth(),
        accent = accent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogLevelPill(level = entry.level, accentColor = accentColor)
            Text(
                timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            modifier = Modifier.padding(top = 8.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ) {
            Text(
                logCategoryLabel(entry.category),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            entry.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun LogLevelPill(
    level: AppLogLevel,
    accentColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = accentColor.copy(alpha = 0.14f),
    ) {
        Text(
            stringResource(logLevelLabelRes(level)),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = accentColor,
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
