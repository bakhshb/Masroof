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
import com.baraa.masroof.MasroofApplication
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.ledger.AccountBalanceService
import com.baraa.masroof.transaction.AccountNature
import java.math.BigDecimal
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Manage account setup data without calculating historical balances. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val repo = app.financialAccountRepository
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<FinancialAccount>>(emptyList()) }
    var editing by remember { mutableStateOf<FinancialAccount?>(null) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(repo) { repo.observeAll().collectLatest { accounts = it } }

    val visibleAccounts = accounts.filter { it.systemAccountKey == null }

    // Compute balances reactively from posted journals. We observe the
    // JournalDao's posted Flow + reload the postings via the
    // AccountBalanceCalculator so balance changes flow into the UI
    // without any cache.
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
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
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
                    val assets = visibleAccounts.filter { it.isActive && it.accountNature == AccountNature.ASSET }
                    val liabilities = visibleAccounts.filter { it.isActive && it.accountNature == AccountNature.LIABILITY }
                    val inactive = visibleAccounts.filterNot { it.isActive }
                    accountGroup("الأصول", assets, balances) { editing = it }
                    accountGroup("الالتزامات", liabilities, balances) { editing = it }
                    accountGroup("الحسابات غير النشطة", inactive, balances) { editing = it }
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
                    repo.add(
                        displayName = draft.displayName,
                        accountType = draft.accountType,
                        institutionName = draft.institutionName,
                        lastFourDigits = draft.lastFourDigits,
                        senderAliases = draft.senderAliases,
                        accountNature = draft.accountNature,
                        currency = draft.currency,
                        openingBalance = draft.openingBalance,
                        openingBalanceDate = draft.openingBalanceDate,
                        includeInNetWorth = draft.includeInNetWorth,
                        includeInLiquidity = draft.includeInLiquidity,
                        notes = draft.notes,
                    )
                    adding = false
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
                        lastFourDigits = draft.lastFourDigits,
                        senderAliases = draft.senderAliases,
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
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.accountGroup(
    title: String,
    accounts: List<FinancialAccount>,
    balances: Map<Long, BigDecimal>,
    onClick: (FinancialAccount) -> Unit,
) {
    if (accounts.isEmpty()) return
    item { Text(title, style = MaterialTheme.typography.titleMedium) }
    items(accounts, key = { it.id }) { AccountRow(it, balances[it.id]) { onClick(it) } }
}

@Composable
private fun AccountRow(account: FinancialAccount, calculatedBalance: BigDecimal?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onClick,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(account.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                account.lastFourDigits?.let { Text("•••• $it", style = MaterialTheme.typography.labelSmall) }
            }
            Text("${accountTypeLabel(account.accountType)} • ${account.currency.name}")
            Text("الرصيد الافتتاحي: ${account.openingBalance.toPlainString()} ر.س")
            val balanceLabel = calculatedBalance?.let { "الرصيد المحسوب اليوم: ${it.toPlainString()} ر.س" } ?: "الرصيد المحسوب: —"
            Text(balanceLabel, style = MaterialTheme.typography.titleMedium)
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
