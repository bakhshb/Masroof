package com.baraa.masroof.ui.transactions

import android.Manifest
import android.content.pm.PackageManager
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.R
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.ui.theme.MasroofTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    viewModel: TransactionViewModel = viewModel(),
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val count by viewModel.transactionCount.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    var showAccountLinks by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var showMerchants by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showTestData by remember { mutableStateOf(false) }
    var showReleaseNotes by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    var showAiSuggestions by remember { mutableStateOf(false) }
    var showAiBatch by remember { mutableStateOf(false) }
    var showAiBatchDisabled by remember { mutableStateOf(false) }
    var showAiBatchPlan by remember { mutableStateOf<com.baraa.masroof.ai.BatchPlan?>(null) }
    var aiMinimumConfidence by remember { mutableStateOf(80) }
    var aiEnabled by remember { mutableStateOf(false) }
    var showFinancialSetup by remember { mutableStateOf(false) }
    var showFinancialHistory by remember { mutableStateOf(false) }
    var showAccountLinkRules by remember { mutableStateOf(false) }

    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        app.aiSettingsRepository.load().let {
            aiEnabled = it.enabled
            aiMinimumConfidence = it.minimumConfidence
        }
        showFinancialSetup = !app.financialSetupRepository.load().setupCompleted
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startImport()
        }
    }

    fun ensurePermissionAndImport() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startImport()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.transactions_title)) },
                actions = {
                    IconButton(onClick = { ensurePermissionAndImport() }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(id = R.string.action_import),
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(id = R.string.settings_title),
                        )
                    }
                    IconButton(onClick = { showAccounts = true }) {
                        Icon(
                            imageVector = Icons.Filled.AccountBox,
                            contentDescription = stringResource(id = R.string.accounts_title),
                        )
                    }
                    if (count > 0) {
                        IconButton(onClick = { showDeleteAll = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(id = R.string.action_delete_all),
                            )
                        }
                    }
                    IconButton(onClick = { showDiagnostics = true }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = stringResource(id = R.string.diagnostics_title),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            CountLine(count = count)
            if (aiEnabled && count > 0) {
                com.baraa.masroof.ui.ai.AiBatchActionCard(
                    onClick = {
                        if (aiEnabled) {
                            scope.launch {
                                val plan = app.aiBatchService.plan()
                                if (plan.eligible > 0) {
                                    showAiBatchPlan = plan
                                } else {
                                    showAiBatchDisabled = true
                                }
                            }
                        } else {
                            showAiBatchDisabled = true
                        }
                    }
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                if (transactions.isEmpty()) {
                    EmptyState()
                } else {
                    TransactionList(
                        items = transactions,
                        onClick = { editing = it },
                    )
                }
            }
        }
    }

    // -- Dialogs (kept outside the Scaffold so they overlay the whole screen) --

    editing?.let { entity ->
        EditTransactionDialog(
            entity = entity,
            onDismiss = { editing = null },
            onSave = { updated ->
                viewModel.updateTransaction(updated)
                editing = null
            },
            onConfirmAndRememberMerchant = { updated ->
                viewModel.confirmAndRemember(updated, rememberMerchant = true)
                editing = null
            },
            onConfirmDelete = {
                viewModel.deleteTransaction(entity)
                editing = null
            },
        )
    }

    if (showFinancialSetup) {
        com.baraa.masroof.ui.onboarding.OnboardingScreen(onFinished = { showFinancialSetup = false })
        return
    }

    if (showFinancialHistory) {
        com.baraa.masroof.ui.history.FinancialHistoryScreen(onClose = { showFinancialHistory = false })
        return
    }
    if (showAccountLinkRules) {
        com.baraa.masroof.ui.accounts.AccountLinkRulesScreen(onClose = { showAccountLinkRules = false })
        return
    }

    if (showAccountLinks) {
        com.baraa.masroof.ui.accounts.AccountLinkingScreen(onClose = { showAccountLinks = false })
        return
    }

    if (showAccounts) {
        com.baraa.masroof.ui.accounts.AccountListScreen(onClose = { showAccounts = false })
        return
    }

    if (showCategories) {
        com.baraa.masroof.ui.categories.CategoryListScreen(onClose = { showCategories = false })
        return
    }

    if (showMerchants) {
        com.baraa.masroof.ui.merchants.MerchantMemoryScreen(onClose = { showMerchants = false })
        return
    }

    if (showSettings) {
        com.baraa.masroof.ui.settings.SettingsScreen(
            onClose = { showSettings = false },
            onCategories = { showCategories = true; showSettings = false },
            onMerchants = { showMerchants = true; showSettings = false },
            onAccounts = { showAccounts = true; showSettings = false },
            onAccountLinks = { showAccountLinks = true; showSettings = false },
            onFinancialHistory = { showFinancialHistory = true; showSettings = false },
            onAccountLinkRules = { showAccountLinkRules = true; showSettings = false },
            onAi = { showAi = true; showSettings = false },
            onAiSuggestions = { showAiSuggestions = true; showSettings = false },
            onAiBatch = { showAiBatch = true; showSettings = false },
            onDiagnostics = { showDiagnostics = true; showSettings = false },
            onTestData = { showTestData = true; showSettings = false },
            onReleaseNotes = { showReleaseNotes = true; showSettings = false },
        )
        return
    }

    if (showAi) {
        com.baraa.masroof.ui.ai.AiSettingsScreen(onClose = { showAi = false })
        return
    }

    if (showAiSuggestions) {
        com.baraa.masroof.ui.ai.AiSuggestionsScreen(
            onClose = { showAiSuggestions = false },
            minimumConfidence = aiMinimumConfidence,
        )
        return
    }

    if (showAiBatchPlan != null) {
        val plan = showAiBatchPlan!!
        com.baraa.masroof.ui.ai.AiBatchDialog(
            plan = plan,
            providerLabel = app.aiSettingsRepository.let {
                kotlinx.coroutines.runBlocking { it.load() }.providerLabel
            },
            modelName = app.aiSettingsRepository.let {
                kotlinx.coroutines.runBlocking { it.load() }.modelName
            },
            onDismiss = { showAiBatchPlan = null },
            batchService = app.aiBatchService,
        )
        return
    }

    if (showAiBatchDisabled) {
        com.baraa.masroof.ui.ai.AiBatchDisabledDialog(
            onDismiss = { showAiBatchDisabled = false },
            onOpenSettings = { showAiBatchDisabled = false; showAi = true },
        )
        return
    }

    if (showDeleteAll) {
        DeleteAllDialog(
            onConfirm = {
                showDeleteAll = false
                viewModel.deleteAllTransactions()
            },
            onCancel = { showDeleteAll = false },
        )
    }

    if (showDiagnostics) {
        com.baraa.masroof.ui.diagnostics.DiagnosticsScreen(onClose = { showDiagnostics = false })
        return
    }
    if (showTestData) {
        com.baraa.masroof.ui.diagnostics.TestDataModeScreen(onClose = { showTestData = false })
        return
    }
    if (showReleaseNotes) {
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("?")
        com.baraa.masroof.ui.diagnostics.ReleaseNotesScreen(
            versionName = versionName,
            onClose = { showReleaseNotes = false },
        )
        return
    }

    when (val state = importState) {
        is ImportState.PreviewReady -> {
            ImportPreviewDialog(
                preview = state.result.preview,
                onConfirm = { viewModel.confirmImport(state.result) },
                onCancel = { viewModel.cancelImport() },
                onReviewDuplicates = { viewModel.openDuplicateReview() },
            )
        }
        is ImportState.ReviewingDuplicates -> {
            val possibleItems = state.result.items.filter {
                it.status == com.baraa.masroof.data.repository.ImportItemStatus.POSSIBLE_DUPLICATE
            }
            DuplicateReviewDialog(
                items = possibleItems,
                onDecisions = { decisions ->
                    viewModel.applyDecisionsAndCommit(state.result, decisions)
                },
                onCancel = { viewModel.cancelImport() },
            )
        }
        ImportState.Scanning -> {
            CenteredProgressDialog(text = stringResource(id = R.string.loading))
        }
        ImportState.Importing -> {
            CenteredProgressDialog(text = stringResource(id = R.string.loading))
        }
        is ImportState.Done -> {
            ImportSummaryDialog(
                summary = state.summary,
                onDismiss = { viewModel.dismissImportFeedback() },
            )
        }
        is ImportState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissImportFeedback() },
                title = { Text(stringResource(id = R.string.error_load_sms)) },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissImportFeedback() }) {
                        Text(stringResource(id = R.string.action_continue))
                    }
                },
            )
        }
        ImportState.Idle -> Unit
    }
}

@Composable
private fun CountLine(count: Int) {
    Text(
        text = pluralStringResource(
            id = R.plurals.count_transactions,
            count = count,
            count,
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.transactions_empty),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.transactions_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TransactionList(
    items: List<TransactionEntity>,
    onClick: (TransactionEntity) -> Unit,
) {
    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = items, key = { it.id }) { txn ->
            TransactionCard(
                entity = txn,
                dateFormat = dateFmt,
                onClick = { onClick(txn) },
            )
        }
    }
}

@Composable
private fun TransactionCard(
    entity: TransactionEntity,
    dateFormat: DateTimeFormatter,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    text = typeLabel(entity.transactionType),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatAmount(entity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entity.merchantOrBeneficiary?.takeIf { it.isNotBlank() }
                    ?: stringResource(id = R.string.tx_missing),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDateTime(entity, dateFormat),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = entity.accountOrCardLastFourDigits
                        ?.let { "**** $it" }
                        ?: stringResource(id = R.string.tx_missing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(status = entity.status)
                Text(
                    text = "${entity.confidence}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: TransactionStatus) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = statusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CenteredProgressDialog(text: String) {
    AlertDialog(
        onDismissRequest = { /* not user-cancellable */ },
        title = { Text(text) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        },
        confirmButton = {},
    )
}

/**
 * Two-step "delete all" dialog. The user must type the Arabic word "حذف" to
 * enable the destructive button. The dialog never reaches into the SMS store.
 */
@Composable
private fun DeleteAllDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val required = stringResource(id = R.string.delete_all_typed_hint)
    val canConfirm = typed.trim() == required
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(id = R.string.delete_all_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(id = R.string.delete_all_body))
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(id = R.string.delete_all_typed_prompt))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = { Text(required) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) {
                Text(
                    text = stringResource(id = R.string.action_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(id = R.string.action_cancel))
            }
        },
    )
}

// -- formatting helpers -------------------------------------------------------

private val TX_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.getDefault())
private val TX_DATE_ONLY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.getDefault())

private fun formatDateTime(entity: TransactionEntity, fallback: DateTimeFormatter): String {
    val d = entity.transactionDate
    val t = entity.transactionTime
    return when {
        d != null && t != null -> runCatching { TX_DATE_FORMATTER.format(d.atTime(t)) }.getOrDefault("")
        d != null -> runCatching { TX_DATE_ONLY_FORMATTER.format(d) }.getOrDefault("")
        else -> ""
    }
}

@Composable
private fun formatAmount(entity: TransactionEntity): String {
    val a = entity.amount ?: return "—"
    val s = a.toPlainString()
    val cur = currencyLabel(entity.currency)
    val unknownLabel = currencyLabel(Currency.UNKNOWN)
    return if (cur.isNotEmpty() && cur != unknownLabel) "$s $cur" else s
}

@Composable
private fun typeLabel(t: TransactionType): String = stringResource(
    id = when (t) {
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
private fun statusLabel(s: TransactionStatus): String = stringResource(
    id = when (s) {
        TransactionStatus.COMPLETED -> R.string.status_completed
        TransactionStatus.PENDING -> R.string.status_pending
        TransactionStatus.DECLINED -> R.string.status_declined
        TransactionStatus.REVERSED -> R.string.status_reversed
        TransactionStatus.UNKNOWN -> R.string.status_unknown
        TransactionStatus.NEEDS_REVIEW -> R.string.status_needs_review
    },
)

@Composable
private fun currencyLabel(c: Currency): String = stringResource(
    id = when (c) {
        Currency.SAR -> R.string.currency_sar
        Currency.USD -> R.string.currency_usd
        Currency.EUR -> R.string.currency_eur
        Currency.UNKNOWN -> R.string.currency_unknown
    },
)

@Preview(showBackground = true, locale = "ar")
@Composable
private fun TransactionListScreenPreview() {
    MasroofTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            TransactionListScreen()
        }
    }
}
