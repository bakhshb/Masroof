package com.baraa.masroof.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.ledger.AccountBalanceService
import com.baraa.masroof.ledger.TransactionPostingStatus
import com.baraa.masroof.ui.theme.PrimaryButton
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Manage account setup data without calculating historical balances. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(
    onClose: (() -> Unit)? = null,
    onOpenImport: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val repo = app.financialAccountRepository
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<FinancialAccount>>(emptyList()) }
    var editing by remember { mutableStateOf<FinancialAccount?>(null) }
    var adding by remember { mutableStateOf(false) }
    var relinking by remember { mutableStateOf(false) }
    var relinkSummary by remember { mutableStateOf<String?>(null) }
    val identifiers by app.accountIdentifierRepository.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val identifierLabels = remember(identifiers) {
        identifiers
            .filter { it.isActive }
            .groupBy { it.accountId }
            .mapValues { (_, rows) ->
                AccountIdentifierLabels.formatLastFours(rows.map { it.normalizedValue })
            }
    }
    val transactions by app.transactionRepository.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val awaitingPostCounts = remember(transactions) {
        transactions
            .filter {
                it.postingStatus != TransactionPostingStatus.POSTED &&
                    it.linkedJournalEntryId == null
            }
            .flatMap { tx ->
                listOfNotNull(tx.sourceAccountId, tx.destinationAccountId)
            }
            .groupingBy { it }
            .eachCount()
    }
    val totalAwaitingPost = remember(transactions) {
        transactions.count {
            it.postingStatus != TransactionPostingStatus.POSTED && it.linkedJournalEntryId == null
        }
    }

    LaunchedEffect(repo) { repo.observeAll().collectLatest { accounts = it } }

    val visibleAccounts = accounts.filter { it.systemAccountKey == null }
    val bindSenderHint = com.baraa.masroof.ui.senders.ImportSessionHints.peekPreferredSender()

    val postedJournals by app.database.journalDao().observePosted().collectAsStateWithLifecycle(initialValue = emptyList())
    val allJournals = remember(postedJournals.size) {
        runCatching {
            kotlinx.coroutines.runBlocking { app.database.journalDao().getAllForRecalculation() }
        }.getOrElse { emptyList() }
    }
    val balances: Map<Long, BigDecimal> = remember(visibleAccounts, postedJournals.size) {
        val today = LocalDate.now()
        AccountBalanceService.balances(visibleAccounts, allJournals, today)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("إدارة الحسابات") },
                navigationIcon = {
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة حساب")
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (visibleAccounts.isEmpty()) {
                EmptyAccountsState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (bindSenderHint != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "أضف المرسل «$bindSenderHint» كمعرّف مرسل",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        "افتح أحد حساباتك ثم أكّد إضافة اسم المرسل، ثم أعد استيراد الرسائل.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        RelinkAndPostCard(
                            awaitingCount = totalAwaitingPost,
                            busy = relinking,
                            summary = relinkSummary,
                            onRelink = {
                                scope.launch {
                                    relinking = true
                                    relinkSummary = null
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            app.historicalAccountRelinkService.relinkUnposted(dryRun = false)
                                        }
                                        relinkSummary = formatRelinkSummary(result)
                                    } catch (t: Throwable) {
                                        relinkSummary = "تعذر الترحيل: ${t.message ?: t::class.java.simpleName}"
                                    } finally {
                                        relinking = false
                                    }
                                }
                            },
                        )
                    }
                    val active = visibleAccounts.filter { it.isActive }
                    val inactive = visibleAccounts.filterNot { it.isActive }
                    fun ofType(vararg types: com.baraa.masroof.transaction.AccountType) =
                        active.filter { it.accountType in types }
                    accountGroup(
                        "الحسابات البنكية",
                        ofType(com.baraa.masroof.transaction.AccountType.BANK_ACCOUNT),
                        balances,
                        identifierLabels,
                        awaitingPostCounts,
                    ) { editing = it }
                    accountGroup(
                        "بطاقات الائتمان",
                        ofType(com.baraa.masroof.transaction.AccountType.CREDIT_CARD),
                        balances,
                        identifierLabels,
                        awaitingPostCounts,
                    ) { editing = it }
                    accountGroup(
                        "المحافظ الرقمية",
                        ofType(
                            com.baraa.masroof.transaction.AccountType.DIGITAL_WALLET,
                            com.baraa.masroof.transaction.AccountType.WALLET,
                        ),
                        balances,
                        identifierLabels,
                        awaitingPostCounts,
                    ) { editing = it }
                    accountGroup(
                        "النقد",
                        ofType(com.baraa.masroof.transaction.AccountType.CASH),
                        balances,
                        identifierLabels,
                        awaitingPostCounts,
                    ) { editing = it }
                    accountGroup(
                        "الاستثمارات",
                        ofType(
                            com.baraa.masroof.transaction.AccountType.INVESTMENT_ACCOUNT,
                            com.baraa.masroof.transaction.AccountType.SUKUK_ACCOUNT,
                        ),
                        balances,
                        identifierLabels,
                        awaitingPostCounts,
                    ) { editing = it }
                    accountGroup(
                        "الالتزامات الأخرى",
                        ofType(
                            com.baraa.masroof.transaction.AccountType.LOAN,
                            com.baraa.masroof.transaction.AccountType.OTHER_LIABILITY,
                        ),
                        balances,
                        identifierLabels,
                        awaitingPostCounts,
                    ) { editing = it }
                    accountGroup(
                        "أصول أخرى",
                        ofType(
                            com.baraa.masroof.transaction.AccountType.OTHER_ASSET,
                            com.baraa.masroof.transaction.AccountType.OTHER,
                        ),
                        balances,
                        identifierLabels,
                        awaitingPostCounts,
                    ) { editing = it }
                    accountGroup(
                        "الحسابات غير النشطة",
                        inactive,
                        balances,
                        identifierLabels,
                        awaitingPostCounts,
                    ) { editing = it }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (adding) {
        AccountEditDialog(
            existing = null,
            existingAccounts = accounts,
            onDismiss = { adding = false },
            onSave = { draft ->
                scope.launch {
                    val id = repo.add(
                        displayName = draft.displayName,
                        accountType = draft.accountType,
                        institutionName = draft.institutionName,
                        accountNature = draft.accountNature,
                        currency = draft.currency,
                        openingBalance = draft.openingBalance,
                        openingBalanceDate = draft.openingBalanceDate,
                        includeInNetWorth = draft.includeInNetWorth,
                        includeInLiquidity = draft.includeInLiquidity,
                        notes = draft.notes,
                    )
                    adding = false
                    editing = repo.getById(id)
                }
            },
        )
    }

    editing?.let { account ->
        AccountEditDialog(
            existing = account,
            existingAccounts = accounts,
            onDismiss = { editing = null },
            onSave = { draft ->
                scope.launch {
                    repo.update(account.copy(
                        displayName = draft.displayName,
                        institutionName = draft.institutionName,
                        accountType = draft.accountType,
                        accountNature = draft.accountNature,
                        currency = draft.currency,
                        openingBalance = draft.openingBalance,
                        openingBalanceDate = draft.openingBalanceDate,
                        includeInNetWorth = draft.includeInNetWorth,
                        includeInLiquidity = draft.includeInLiquidity,
                        isActive = draft.isActive,
                        notes = draft.notes,
                    ))
                    editing = null
                }
            },
            onDelete = {
                scope.launch { repo.delete(account); editing = null }
            },
            onImportAfterBind = {
                editing = null
                onOpenImport?.invoke()
            },
        )
    }
}

@Composable
private fun RelinkAndPostCard(
    awaitingCount: Int,
    busy: Boolean,
    summary: String?,
    onRelink: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "الرصيد المحسوب = الافتتاحي + العمليات المرحّلة فقط.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                if (awaitingCount > 0) "عمليات بانتظار الترحيل: $awaitingCount"
                else "لا توجد عمليات معلّقة للترحيل.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            PrimaryButton(
                label = "إعادة ربط وترحيل المطابق",
                onClick = onRelink,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            summary?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(
                "بعد إضافة كل آخر 4 أرقام للحساب، شغّل الزر أعلاه لترحيل العمليات المطابقة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.accountGroup(
    title: String,
    accounts: List<FinancialAccount>,
    balances: Map<Long, BigDecimal>,
    identifierLabels: Map<Long, String?>,
    awaitingPostCounts: Map<Long, Int>,
    onClick: (FinancialAccount) -> Unit,
) {
    if (accounts.isEmpty()) return
    item { Text(title, style = MaterialTheme.typography.titleMedium) }
    items(accounts, key = { it.id }) {
        AccountRow(
            account = it,
            calculatedBalance = balances[it.id],
            identifierLastFours = identifierLabels[it.id],
            awaitingPostCount = awaitingPostCounts[it.id] ?: 0,
            onClick = { onClick(it) },
        )
    }
}

@Composable
private fun AccountRow(
    account: FinancialAccount,
    calculatedBalance: BigDecimal?,
    identifierLastFours: String?,
    awaitingPostCount: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onClick,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(account.displayName, style = MaterialTheme.typography.titleMedium)
            identifierLastFours?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${accountTypeLabel(account.accountType)} • ${account.currency.name}")
            Text("الرصيد الافتتاحي: ${account.openingBalance.toPlainString()} ر.س")
            val balanceLabel = calculatedBalance?.let { "الرصيد المحسوب اليوم: ${it.toPlainString()} ر.س" } ?: "الرصيد المحسوب: —"
            Text(balanceLabel, style = MaterialTheme.typography.titleMedium)
            calculatedBalance?.let { bal ->
                val delta = bal.subtract(account.openingBalance)
                val deltaText = when {
                    delta.signum() < 0 -> "التغيّر منذ الافتتاحي: −${delta.abs().toPlainString()} ر.س"
                    delta.signum() > 0 -> "التغيّر منذ الافتتاحي: +${delta.toPlainString()} ر.س"
                    else -> "التغيّر منذ الافتتاحي: ٠ ر.س"
                }
                Text(
                    deltaText,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        delta.signum() < 0 -> MaterialTheme.colorScheme.error
                        delta.signum() > 0 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (awaitingPostCount > 0) {
                Text(
                    "عمليات بانتظار الترحيل: $awaitingPostCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!account.isActive) Text("غير نشط", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EmptyAccountsState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("لا توجد حسابات بعد", style = MaterialTheme.typography.titleMedium)
        Text("أضف حسابًا لتسجيل الرصيد الافتتاحي فقط.")
    }
}

internal fun formatRelinkSummary(result: com.baraa.masroof.ledger.HistoricalAccountRelinkService.Result): String {
    if (result.posted == 0 && result.updated == 0) {
        return "لم يُرحَّل شيء: لا تطابق برقم، أو العمليات تحتاج مراجعة يدوية (مثل التحويلات)."
    }
    if (result.posted == 0 && result.linkedConfirmed > 0) {
        return "رُبط ${result.linkedConfirmed} بدون ترحيل (علاج غير جاهز أو قيد مفقود). محدّث ${result.updated} · مراجعة ${result.linkedNeedsReview}"
    }
    return "محدّث ${result.updated} · مؤكد ${result.linkedConfirmed} · مرحّل ${result.posted} · مراجعة ${result.linkedNeedsReview}"
}
