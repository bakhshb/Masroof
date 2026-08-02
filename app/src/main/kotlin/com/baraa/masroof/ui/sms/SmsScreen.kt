package com.baraa.masroof.ui.sms

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baraa.masroof.R
import com.baraa.masroof.sms.MatchReason
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.ui.theme.MasroofTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScreen(viewModel: SmsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    // On first composition, check the current permission state. If not granted,
    // surface the explanation screen — do NOT auto-launch the system prompt so the
    // user sees the rationale first.
    val initiallyGranted = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(initiallyGranted) {
        if (initiallyGranted) {
            viewModel.onPermissionResult(true)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(id = R.string.sms_screen_title)) },
                actions = {
                    if (state.hasPermission) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(id = R.string.action_refresh),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            !state.hasPermission -> PermissionGate(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onRequest = { permissionLauncher.launch(Manifest.permission.READ_SMS) },
                showDenied = !initiallyGranted && !state.hasPermission,
            )

            state.isLoading && state.messages.isEmpty() -> LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            else -> LoadedContent(
                state = state,
                contentPadding = innerPadding,
                onFilterChange = viewModel::setFilterMode,
                onRefresh = viewModel::refresh,
            )
        }
    }
}

@Composable
private fun LoadedContent(
    state: SmsUiState,
    contentPadding: PaddingValues,
    onFilterChange: (FilterMode) -> Unit,
    onRefresh: () -> Unit,
) {
    val displayed = state.displayedMessages
    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        FilterBar(
            current = state.filterMode,
            onFilterChange = onFilterChange,
        )
        CountLine(
            displayedCount = displayed.size,
            totalCount = state.messages.size,
            filterMode = state.filterMode,
        )
        when {
            displayed.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                onRefresh = onRefresh,
                filterMode = state.filterMode,
                totalCount = state.messages.size,
            )

            else -> MessageList(messages = displayed)
        }
    }
}

@Composable
private fun FilterBar(
    current: FilterMode,
    onFilterChange: (FilterMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = current == FilterMode.BANKS_ONLY,
            onClick = { onFilterChange(FilterMode.BANKS_ONLY) },
            label = { Text(text = stringResource(id = R.string.filter_banks_only)) },
        )
        FilterChip(
            selected = current == FilterMode.ALL,
            onClick = { onFilterChange(FilterMode.ALL) },
            label = { Text(text = stringResource(id = R.string.filter_all)) },
        )
    }
}

@Composable
private fun CountLine(
    displayedCount: Int,
    totalCount: Int,
    filterMode: FilterMode,
) {
    val text = if (filterMode == FilterMode.BANKS_ONLY && totalCount > displayedCount) {
        // Show "X of Y" so the user knows there are other messages hidden by the filter.
        pluralStringResource(
            id = R.plurals.count_messages_of_total,
            count = displayedCount,
            displayedCount,
            totalCount,
        )
    } else {
        pluralStringResource(
            id = R.plurals.count_messages,
            count = displayedCount,
            displayedCount,
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun PermissionGate(
    modifier: Modifier,
    onRequest: () -> Unit,
    showDenied: Boolean,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                id = if (showDenied) R.string.permission_denied_title
                else R.string.permission_explain_title,
            ),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(
                id = if (showDenied) R.string.permission_denied_body
                else R.string.permission_explain_body,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text(
                text = stringResource(
                    id = if (showDenied) R.string.permission_retry
                    else R.string.permission_grant,
                )
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier,
    onRefresh: () -> Unit,
    filterMode: FilterMode,
    totalCount: Int,
) {
    val isFilteredEmpty = filterMode == FilterMode.BANKS_ONLY && totalCount > 0
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                id = if (isFilteredEmpty) R.string.empty_no_bank_messages
                else R.string.empty_messages,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRefresh) {
            Text(text = stringResource(id = R.string.action_refresh))
        }
    }
}

@Composable
private fun MessageList(messages: List<SmsMessage>) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = messages, key = { it.id }) { message ->
            SmsRow(message = message, dateFormat = dateFormat)
        }
    }
}

@Composable
private fun SmsRow(
    message: SmsMessage,
    dateFormat: SimpleDateFormat,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message.sender?.takeIf { it.isNotBlank() }
                        ?: stringResource(id = R.string.unknown_sender),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text(
                    text = formatDate(message.timestamp, dateFormat),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message.body?.takeIf { it.isNotBlank() }
                    ?: stringResource(id = R.string.empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (message.matchReason != MatchReason.NONE) {
                Spacer(modifier = Modifier.height(8.dp))
                MatchReasonPill(reason = message.matchReason)
            }
        }
    }
}

@Composable
private fun MatchReasonPill(reason: MatchReason) {
    val label = stringResource(
        id = when (reason) {
            MatchReason.KNOWN_SENDER -> R.string.match_known_sender
            MatchReason.KEYWORDS -> R.string.match_keywords
            MatchReason.BOTH -> R.string.match_both
            MatchReason.NONE -> return // hidden by parent
        },
    )
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun formatDate(timestamp: Long, format: SimpleDateFormat): String {
    if (timestamp <= 0L) return ""
    return runCatching { format.format(Date(timestamp)) }.getOrDefault("")
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun SmsScreenPreview() {
    MasroofTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            SmsScreen()
        }
    }
}
