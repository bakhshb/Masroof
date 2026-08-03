package com.baraa.masroof.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.TransactionPostingStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Review-only account linking. Generated journals remain NEEDS_REVIEW until the user posts them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountLinkingScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    var transactions by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<FinancialAccount>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var chooser by remember { mutableStateOf<LinkSelection?>(null) }
    LaunchedEffect(Unit) { app.transactionRepository.observeAll().collectLatest { transactions = it } }
    LaunchedEffect(Unit) { app.financialAccountRepository.observeAll().collectLatest { rows -> accounts = rows.filter { it.isOwnedByUser && it.isActive } } }
    val eligible = transactions.filter { it.postingStatus != TransactionPostingStatus.POSTED }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("ربط العمليات بالحسابات") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("ربط العمليات تلقائيًا", style = MaterialTheme.typography.titleMedium)
                Text("${eligible.size} عملية مؤهلة؛ القيود الجديدة ستحتاج مراجعة ولن تُعتمد تلقائيًا.")
                Button(onClick = {
                    scope.launch {
                        val accounts = app.financialAccountRepository.getOwnedActive()
                        eligible.forEach { app.transactionLinkingService.linkAndGenerate(it, accounts) }
                        message = "تم إنشاء مقترحات الربط للمراجعة"
                    }
                }) { Text("ربط العمليات تلقائيًا") }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            linkGroup("مرتبطة وجاهزة للاعتماد", transactions.filter { it.linkedJournalEntryId != null && it.postingStatus != TransactionPostingStatus.POSTED }) { tx ->
                LinkCard(tx, onSource = { chooser = LinkSelection(tx, true) }, onDestination = { chooser = LinkSelection(tx, false) }, onPost = {
                    scope.launch {
                        val journalId = tx.linkedJournalEntryId ?: return@launch
                        message = if (app.ledgerRepository.post(journalId).valid) "تم اعتماد القيد وتحديث رصيد الحساب" else "تعذر اعتماد القيد"
                    }
                })
            }
            linkGroup("تحتاج تحديد الحساب", transactions.filter { it.accountLinkSource.name == "UNLINKED" && it.postingStatus != TransactionPostingStatus.POSTED }) { tx -> LinkCard(tx, { chooser = LinkSelection(tx, true) }, { chooser = LinkSelection(tx, false) }) }
            linkGroup("ربط غير مؤكد", transactions.filter { it.accountLinkNeedsReview && it.accountLinkSource.name != "UNLINKED" && it.postingStatus != TransactionPostingStatus.POSTED }) { tx -> LinkCard(tx, { chooser = LinkSelection(tx, true) }, { chooser = LinkSelection(tx, false) }) }
            linkGroup("قيود معتمدة", transactions.filter { it.postingStatus == TransactionPostingStatus.POSTED }) { LinkCard(it) }
        }
    }
    chooser?.let { choice ->
        AlertDialog(
            onDismissRequest = { chooser = null },
            title = { Text(if (choice.isSource) "اختيار الحساب المصدر" else "اختيار الحساب المستفيد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    accounts.forEach { account ->
                        OutlinedButton(onClick = {
                            scope.launch {
                                app.transactionLinkingService.applyUserLink(
                                    choice.transaction,
                                    sourceAccountId = if (choice.isSource) account.id else choice.transaction.sourceAccountId,
                                    destinationAccountId = if (choice.isSource) choice.transaction.destinationAccountId else account.id,
                                    accounts = accounts,
                                )
                                chooser = null
                                message = "تم تأكيد الربط؛ راجع القيد قبل اعتماده"
                            }
                        }) { Text(account.displayName) }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

private data class LinkSelection(val transaction: TransactionEntity, val isSource: Boolean)

private fun androidx.compose.foundation.lazy.LazyListScope.linkGroup(
    title: String,
    transactions: List<TransactionEntity>,
    card: @Composable (TransactionEntity) -> Unit,
) {
    if (transactions.isEmpty()) return
    item { Text(title, style = MaterialTheme.typography.titleMedium) }
    items(transactions, key = { it.id }) { card(it) }
}

@Composable
private fun LinkCard(
    transaction: TransactionEntity,
    onSource: (() -> Unit)? = null,
    onDestination: (() -> Unit)? = null,
    onPost: (() -> Unit)? = null,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${transaction.transactionType} • ${transaction.amount?.toPlainString().orEmpty()} ${transaction.currency.name}")
            Text("${transaction.transactionDate ?: "تاريخ الرسالة"} • ${transaction.financialTreatment}")
            Text("الثقة في الربط: ${transaction.accountLinkConfidence}%")
            Text("معاينة القيد: ${transaction.financialTreatment}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onSource?.let { OutlinedButton(onClick = it) { Text("اختيار الحساب المصدر") } }
                onDestination?.let { OutlinedButton(onClick = it) { Text("اختيار الحساب المستفيد") } }
                OutlinedButton(onClick = {}) { Text("مراجعة لاحقًا") }
                onPost?.let { Button(onClick = it) { Text("اعتماد القيد") } }
            }
        }
    }
}
