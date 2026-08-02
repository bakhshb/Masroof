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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.baraa.masroof.R
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
    // user sees the rationale first (per the "show explanation before requesting" rule).
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

            state.messages.isEmpty() -> EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onRefresh = { viewModel.refresh() },
            )

            else -> MessageList(
                messages = state.messages,
                contentPadding = innerPadding,
            )
        }
    }
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
private fun EmptyState(modifier: Modifier, onRefresh: () -> Unit) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.empty_messages),
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
private fun MessageList(
    messages: List<SmsMessage>,
    contentPadding: PaddingValues,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp + contentPadding.calculateStartPadding(LayoutDirection.Rtl),
            end = 12.dp + contentPadding.calculateEndPadding(LayoutDirection.Rtl),
            top = 8.dp + contentPadding.calculateTopPadding(),
            bottom = 8.dp + contentPadding.calculateBottomPadding(),
        ),
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
                )
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
        }
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
