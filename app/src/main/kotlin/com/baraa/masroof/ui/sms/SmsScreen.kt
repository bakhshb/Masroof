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
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.ParsedTransaction
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.ui.theme.MasroofTheme
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private const val PARSE_FAILED_THRESHOLD = 30

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
                onHideBodyChange = viewModel::setHideOriginalBody,
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
    onHideBodyChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    val displayed = state.displayedMessages
    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        FilterBar(
            current = state.filterMode,
            onFilterChange = onFilterChange,
            hideBody = state.hideOriginalBody,
            onHideBodyChange = onHideBodyChange,
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

            else -> MessageList(
                messages = displayed,
                hideOriginalBody = state.hideOriginalBody,
            )
        }
    }
}

@Composable
private fun FilterBar(
    current: FilterMode,
    onFilterChange: (FilterMode) -> Unit,
    hideBody: Boolean,
    onHideBodyChange: (Boolean) -> Unit,
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
        FilterChip(
            selected = hideBody,
            onClick = { onHideBodyChange(!hideBody) },
            label = {
                Text(
                    text = stringResource(
                        if (hideBody) R.string.filter_hide_body
                        else R.string.filter_show_body
                    )
                )
            },
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
private fun MessageList(
    messages: List<SmsMessage>,
    hideOriginalBody: Boolean,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = messages, key = { it.id }) { message ->
            SmsRow(
                message = message,
                dateFormat = dateFormat,
                hideOriginalBody = hideOriginalBody,
            )
        }
    }
}

@Composable
private fun SmsRow(
    message: SmsMessage,
    dateFormat: SimpleDateFormat,
    hideOriginalBody: Boolean,
) {
    val parseFailed = message.parsed == null ||
        message.parsed.amount == null ||
        message.parsed.confidence < PARSE_FAILED_THRESHOLD
    val showBody = !hideOriginalBody || parseFailed

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
            if (showBody) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message.body?.takeIf { it.isNotBlank() }
                        ?: stringResource(id = R.string.empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (message.matchReason != MatchReason.NONE) {
                Spacer(modifier = Modifier.height(8.dp))
                MatchReasonPill(reason = message.matchReason)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (parseFailed) {
                Text(
                    text = stringResource(id = R.string.tx_parse_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                TransactionCard(parsed = message.parsed!!)
            }
        }
    }
}

@Composable
private fun TransactionCard(parsed: ParsedTransaction) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            TxRow(
                label = stringResource(id = R.string.tx_type),
                value = typeLabel(parsed.transactionType),
            )
            TxRow(
                label = stringResource(id = R.string.tx_amount),
                value = formatAmount(parsed),
            )
            TxRow(
                label = stringResource(id = R.string.tx_currency),
                value = currencyLabel(parsed.currency),
            )
            TxRow(
                label = stringResource(id = R.string.tx_merchant),
                value = parsed.merchant ?: stringResource(id = R.string.tx_missing),
            )
            TxRow(
                label = stringResource(id = R.string.tx_account),
                value = parsed.accountOrCardLastFourDigits ?: stringResource(id = R.string.tx_missing),
            )
            TxRow(
                label = stringResource(id = R.string.tx_date),
                value = formatTransactionDateTime(parsed),
            )
            TxRow(
                label = stringResource(id = R.string.tx_status),
                value = statusLabel(parsed.status),
            )
            TxRow(
                label = stringResource(id = R.string.tx_confidence),
                value = stringResource(
                    id = R.string.tx_confidence,
                ).let { "${parsed.confidence}%" },
            )
        }
    }
}

@Composable
private fun TxRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun MatchReasonPill(reason: MatchReason) {
    val label = stringResource(
        id = when (reason) {
            MatchReason.KNOWN_SENDER -> R.string.match_known_sender
            MatchReason.KEYWORDS -> R.string.match_keywords
            MatchReason.BOTH -> R.string.match_both
            MatchReason.NONE -> return
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

// -- formatting helpers -------------------------------------------------------

private fun formatDate(timestamp: Long, format: SimpleDateFormat): String {
    if (timestamp <= 0L) return ""
    return runCatching { format.format(Date(timestamp)) }.getOrDefault("")
}

private val TX_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.getDefault())
private val TX_DATE_ONLY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.getDefault())

private fun formatTransactionDateTime(parsed: ParsedTransaction): String {
    val date = parsed.transactionDate
    val time = parsed.transactionTime
    return when {
        date != null && time != null -> runCatching {
            TX_DATE_FORMATTER.format(date.atTime(time))
        }.getOrDefault("")
        date != null -> runCatching {
            TX_DATE_ONLY_FORMATTER.format(date)
        }.getOrDefault("")
        else -> "" // will render as tx_missing via the caller
    }
}

@Composable
private fun formatAmount(parsed: ParsedTransaction): String {
    val amount = parsed.amount ?: return "—"
    val s = amount.toPlainString()
    val currency = currencyLabel(parsed.currency)
    return if (currency.isNotEmpty() && currency != stringResource(id = R.string.currency_unknown)) {
        "$s $currency"
    } else {
        s
    }
}

@Composable
private fun typeLabel(type: TransactionType): String = stringResource(
    id = when (type) {
        TransactionType.PURCHASE -> R.string.type_purchase
        TransactionType.ONLINE_PURCHASE -> R.string.type_online_purchase
        TransactionType.CASH_WITHDRAWAL -> R.string.type_cash_withdrawal
        TransactionType.TRANSFER_OUT -> R.string.type_transfer_out
        TransactionType.TRANSFER_IN -> R.string.type_transfer_in
        TransactionType.CARD_PAYMENT -> R.string.type_card_payment
        TransactionType.REFUND -> R.string.type_refund
        TransactionType.SALARY -> R.string.type_salary
        TransactionType.DEPOSIT -> R.string.type_deposit
        TransactionType.BANK_FEE -> R.string.type_bank_fee
        TransactionType.INTERNAL_TRANSFER -> R.string.type_internal_transfer
        TransactionType.INVESTMENT_TRANSFER -> R.string.type_investment_transfer
        TransactionType.DECLINED -> R.string.type_declined
        TransactionType.UNKNOWN -> R.string.type_unknown
    },
)

@Composable
private fun statusLabel(status: TransactionStatus): String = stringResource(
    id = when (status) {
        TransactionStatus.COMPLETED -> R.string.status_completed
        TransactionStatus.PENDING -> R.string.status_pending
        TransactionStatus.DECLINED -> R.string.status_declined
        TransactionStatus.REVERSED -> R.string.status_reversed
        TransactionStatus.UNKNOWN -> R.string.status_unknown
    },
)

@Composable
private fun currencyLabel(currency: Currency): String = stringResource(
    id = when (currency) {
        Currency.SAR -> R.string.currency_sar
        Currency.USD -> R.string.currency_usd
        Currency.EUR -> R.string.currency_eur
        Currency.UNKNOWN -> R.string.currency_unknown
    },
)

@Preview(showBackground = true, locale = "ar")
@Composable
private fun SmsScreenPreview() {
    MasroofTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            SmsScreen()
        }
    }
}
