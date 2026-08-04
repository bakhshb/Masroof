package com.baraa.masroof.ui.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.TransactionPostingStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
    var rememberFuture by remember { mutableStateOf(false) }
    var existingRule by remember { mutableStateOf<ExistingRuleView?>(null) }
    var updatePending by remember { mutableStateOf<UpdateRequest?>(null) }
    LaunchedEffect(Unit) { app.transactionRepository.observeAll().collectLatest { transactions = it } }
    LaunchedEffect(Unit) { app.financialAccountRepository.observeAll().collectLatest { rows -> accounts = rows.filter { it.isOwnedByUser && it.isActive } } }
    val eligible = transactions.filter { it.postingStatus != TransactionPostingStatus.POSTED }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("ربط العمليات بالحسابات") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("ربط العمليات تلقائيًا", style = MaterialTheme.typography.titleMedium)
                Text("${eligible.size} عملية مؤهلة؛ القيود الجديدة ستحتاج مراجعة ولن تُعتمد تلقائيًا.")
                Button(onClick = {
                    scope.launch {
                        val owned = app.financialAccountRepository.getOwnedActive()
                        eligible.forEach { app.transactionLinkingService.linkAndGenerate(it, owned) }
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
        LaunchedEffect(choice) {
            existingRule = null
            val rule = app.accountLinkRuleRepository.findRule(choice.transaction)
            if (rule != null) {
                val account = accounts.firstOrNull { it.id == rule.accountId }
                existingRule = ruleViewOf(rule, account)
            }
        }
        AlertDialog(
            onDismissRequest = { chooser = null; rememberFuture = false; existingRule = null },
            title = { Text(if (choice.isSource) "اختيار الحساب المصدر" else "اختيار الحساب المستفيد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        OutlinedButton(onClick = {
                            scope.launch {
                                val decision = ManualLinkComposer.evaluate(choice.transaction, accounts, account)
                                if (rememberFuture && !decision.canRemember) {
                                    message = "لا يمكن حفظ هذا الربط لأن بيانات العملية غير كافية أو متعارضة"
                                    chooser = null; rememberFuture = false; return@launch
                                }
                                val existing = app.accountLinkRuleRepository.findRule(choice.transaction)
                                if (rememberFuture && decision.canRemember && existing != null && existing.accountId != account.id) {
                                    updatePending = UpdateRequest(choice, account)
                                    chooser = null
                                    rememberFuture = false
                                    return@launch
                                }
                                app.transactionLinkingService.applyUserLink(
                                    choice.transaction,
                                    sourceAccountId = if (choice.isSource) account.id else choice.transaction.sourceAccountId,
                                    destinationAccountId = if (choice.isSource) choice.transaction.destinationAccountId else account.id,
                                    accounts = accounts,
                                    rememberForFuture = rememberFuture && decision.canRemember,
                                )
                                chooser = null
                                message = if (rememberFuture && decision.canRemember) "تم ربط العملية وحفظ القاعدة للعمليات المشابهة" else "تم ربط العملية دون حفظ قاعدة مستقبلية"
                                rememberFuture = false
                            }
                        }) { Text(account.displayName) }
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = rememberFuture, onCheckedChange = { rememberFuture = it })
                        Text("تذكر هذا الربط للعمليات المشابهة مستقبلًا")
                    }
                    existingRule?.let { rule ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("يوجد ربط محفوظ لهذه العمليات", style = MaterialTheme.typography.titleSmall)
                                Text("الحساب: ${rule.targetAccountName}")
                                Text("نوع الحساب: ${rule.expectedAccountType}")
                                Text("تم تأكيده ${rule.confirmationCount} مرة")
                                Text("آخر استخدام: ${formatLastUsed(rule.lastUsedAt)}")
                                Text(if (rule.active) "الحالة: نشطة" else "الحالة: معطلة")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            app.accountLinkRuleRepository.applyExisting(choice.transaction, accounts)
                                            chooser = null
                                            message = "تم استخدام الربط المحفوظ"
                                        }
                                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("استخدام الربط المحفوظ") }
                                    OutlinedButton(onClick = { chooser = null; rememberFuture = true; existingRule = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("تحديث الربط") }
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            app.transactionLinkingService.applyUserLink(choice.transaction, choice.transaction.sourceAccountId, choice.transaction.destinationAccountId, accounts, rememberForFuture = false)
                                            chooser = null
                                            message = "تم تطبيقه على هذه العملية فقط"
                                        }
                                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("تطبيق على هذه العملية فقط") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    updatePending?.let { request ->
        val current = accounts.firstOrNull { it.id == request.existingRuleAccountId }
        AlertDialog(
            onDismissRequest = { updatePending = null },
            title = { Text("تحديث قاعدة الربط؟") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("سيتم استخدام الحساب الجديد تلقائيًا للعمليات المشابهة مستقبلًا بدلًا من الحساب المحفوظ حاليًا.")
                    Text("الحساب الحالي: ${current?.displayName ?: "غير معروف"}")
                    Text("الحساب المقترح: ${request.proposed.displayName}")
                    Text("نوع العملية: ${request.choice.transaction.transactionType}")
                    Text("المرسل / المؤسسة: ${request.choice.transaction.originalSender ?: request.proposed.institutionName ?: "غير معروف"}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.transactionLinkingService.applyUserLink(request.choice.transaction, request.choice.transaction.sourceAccountId, request.choice.transaction.destinationAccountId, accounts, rememberForFuture = true, proposedAccountId = request.proposed.id)
                        message = "تم تحديث القاعدة بنجاح"
                        updatePending = null
                    }
                }) { Text("تحديث القاعدة") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        scope.launch {
                            app.transactionLinkingService.applyUserLink(request.choice.transaction, request.choice.transaction.sourceAccountId, request.choice.transaction.destinationAccountId, accounts, rememberForFuture = false)
                            message = "تم تطبيقه على هذه العملية فقط"
                            updatePending = null
                        }
                    }) { Text("تطبيق على هذه العملية فقط") }
                    TextButton(onClick = { updatePending = null }) { Text("إلغاء") }
                }
            },
        )
    }
}

private data class LinkSelection(val transaction: TransactionEntity, val isSource: Boolean)
private data class UpdateRequest(val choice: LinkSelection, val proposed: FinancialAccount, val existingRuleAccountId: Long? = null)

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