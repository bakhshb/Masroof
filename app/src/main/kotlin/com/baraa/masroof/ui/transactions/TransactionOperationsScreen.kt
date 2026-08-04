package com.baraa.masroof.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.AccountLinkConfidence
import com.baraa.masroof.ledger.TransactionPostingStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionOperationsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    val transactions by app.transactionRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    val accounts by app.financialAccountRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    val categories by app.categoryRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    val state = rememberSaveable(saver = TransactionOpsStateSaver) { TransactionOpsState() }
    var debouncedQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.query) { delay(250); debouncedQuery = state.query }
    val selectedAccounts = remember { mutableStateListOf<Long>() }
    val selectedCategories = remember { mutableStateListOf<Long>() }
    val filter = state.toFilter()
    val categoriesById = categories.associate { it.id to it.nameAr }
    val visible = remember(transactions, accounts, filter, debouncedQuery) { TransactionSearchEngine.search(transactions, accounts, categoriesById, filter.copy(query = debouncedQuery)) }
    val reviewQueue = visible.filter { it.postingStatus == TransactionPostingStatus.NEEDS_REVIEW || it.accountLinkSource.name == "UNLINKED" }
    var batchResult by remember { mutableStateOf<TransactionBatchReview.BatchOutcome?>(null) }
    var rememberBatch by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("العمليات") }, actions = {
            IconButton(onClick = { showFilters = true }) { Icon(Icons.Filled.Search, "فلترة") }
            IconButton(onClick = { scope.launch { /* trigger import */ } }) { Icon(Icons.Filled.Add, "استيراد") }
            IconButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "إخفاء التفاصيل الفنية" else "إظهار التفاصيل الفنية") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(state.query, { state.query = it; if (it.isEmpty()) debouncedQuery = "" }, label = { Text("بحث") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { state.query = ""; debouncedQuery = "" }) { Icon(Icons.Filled.Close, "مسح") } })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = state.needsReview, onClick = { state.needsReview = !state.needsReview }, label = { Text("يحتاج مراجعة") })
                FilterChip(selected = state.unlinked, onClick = { state.unlinked = !state.unlinked }, label = { Text("غير مرتبط") })
                FilterChip(selected = state.expenses, onClick = { state.expenses = !state.expenses }, label = { Text("مصروفات") })
                if (!state.isEmpty) AssistChip(onClick = { state.reset() }, label = { Text("مسح الفلاتر") })
            }
            Text("عدد النتائج: ${visible.size}")
            if (visible.isEmpty()) Text("لا توجد نتائج مطابقة", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (reviewQueue.isNotEmpty()) Text("طابور المراجعة: ${reviewQueue.size}")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                items(reviewQueue, key = { it.id }) { tx -> ReviewCard(tx, accounts, categories, showAdvanced) }
            }
            val firstCompatibleAccount = accounts.firstOrNull { acc -> reviewQueue.all { tx -> (tx.sourceAccountId == acc.id || tx.destinationAccountId == acc.id) && com.baraa.masroof.ui.accounts.ManualLinkComposer.evaluate(tx, accounts, acc).canRemember } }
            if (reviewQueue.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val outcome = TransactionBatchReview.postValidated(reviewQueue, accounts, app.ledgerRepository)
                            batchResult = outcome
                            if (rememberBatch && firstCompatibleAccount != null) {
                                val decision = com.baraa.masroof.ui.accounts.ManualLinkComposer.batchDecision(reviewQueue, firstCompatibleAccount)
                                if (decision.canRemember) reviewQueue.forEach { tx -> runCatching { app.transactionLinkingService.applyUserLink(tx, tx.sourceAccountId, tx.destinationAccountId, accounts, rememberForFuture = true) } }
                                message = if (decision.canRemember) "تم حفظ الربط وسيُستخدم تلقائيًا للعمليات المشابهة مستقبلًا" else "تم تأكيد العمليات دون حفظ قاعدة للعمليات غير المتوافقة"
                                rememberBatch = false
                            } else if (rememberBatch) {
                                message = "لا يمكن تذكر هذا الربط لأن بيانات العمليات غير متطابقة"
                                rememberBatch = false
                            }
                        }
                    }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("تأكيد المحدد") }
                    OutlinedButton(onClick = { scope.launch { reviewQueue.forEach { app.transactionRepository.delete(it) } } }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("تجاهل المحدد") }
                }
                if (firstCompatibleAccount != null) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = rememberBatch, onCheckedChange = { rememberBatch = it })
                    Text("تذكر هذا الربط للعمليات المشابهة")
                }
            }
            batchResult?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("تم اعتماد ${it.confirmed} عملية")
                        Text("تعذر اعتماد ${it.failed} عملية")
                        if (it.reviewRequired > 0) Text("تحتاج ${it.reviewRequired} عملية إلى مراجعة إضافية")
                    }
                }
            }
        }
    }
    if (showFilters) FilterSheet(state = state, accounts = accounts, categories = categories, onDismiss = { showFilters = false }, onApply = { state.snapshot(); showFilters = false })
}

@Composable
private fun ReviewCard(transaction: TransactionEntity, accounts: List<FinancialAccount>, categories: List<com.baraa.masroof.data.db.Category>, showAdvanced: Boolean) {
    val accountName = accounts.firstOrNull { it.id == transaction.sourceAccountId || it.id == transaction.destinationAccountId }?.displayName
    val categoryName = categories.firstOrNull { it.id == transaction.categoryId }?.nameAr
    val reviewReason = when {
        transaction.accountLinkSource.name == "UNLINKED" -> "تحتاج تحديد الحساب"
        transaction.postingStatus == TransactionPostingStatus.NEEDS_REVIEW -> "تحتاج مراجعة"
        else -> "جاهزة للاعتماد"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(transaction.merchantOrBeneficiary?.takeIf { it.isNotBlank() } ?: transaction.transactionType.name, fontWeight = FontWeight.SemiBold)
            Text("${transaction.amount?.toPlainString().orEmpty()} ${transaction.currency.name}")
            Text(transaction.transactionDate?.toString().orEmpty())
            if (accountName != null) Text("الحساب: $accountName")
            if (categoryName != null) Text("التصنيف: $categoryName")
            Text("الثقة: ${transaction.accountLinkConfidence}% • $reviewReason")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = {}) { Text("تأكيد") }; OutlinedButton(onClick = {}) { Text("تعديل") }; TextButton(onClick = {}) { Text("تجاهل") } }
            if (showAdvanced) {
                Text("تفاصيل فنية: parser=${transaction.transactionType} • link=${transaction.accountLinkSource.name} • status=${transaction.postingStatus.name} • tx#${transaction.id}")
                Text("journalId=${transaction.linkedJournalEntryId ?: "-"}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(state: TransactionOpsState, accounts: List<FinancialAccount>, categories: List<com.baraa.masroof.data.db.Category>, onDismiss: () -> Unit, onApply: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("هذا الشهر" to { state.fromDate = LocalDate.now().withDayOfMonth(1); state.toDate = LocalDate.now() }, "الشهر السابق" to { val prev = LocalDate.now().minusMonths(1); state.fromDate = prev.withDayOfMonth(1); state.toDate = prev.withDayOfMonth(prev.lengthOfMonth()) }, "فترة مخصصة" to {}).forEach { (label, action) -> FilterChip(selected = false, onClick = action, label = { Text(label) }) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("مصروفات" to { state.expenses = !state.expenses }, "دخل" to { state.income = !state.income }, "تحويلات داخلية" to { state.internalTransfers = !state.internalTransfers }, "استثمارات" to { state.investments = !state.investments }, "سداد بطاقات" to { state.cardPayments = !state.cardPayments }, "مستردات" to { state.refunds = !state.refunds }, "رسوم بنكية" to { state.bankFees = !state.bankFees }).forEach { (label, action) -> FilterChip(selected = false, onClick = action, label = { Text(label) }) }
            }
            Text("حساب محدد")
            accounts.forEach { account -> FilterChip(selected = state.accountId == account.id, onClick = { state.accountId = if (state.accountId == account.id) null else account.id }, label = { Text(account.displayName) }) }
            Text("تصنيف محدد")
            categories.forEach { category -> FilterChip(selected = state.categoryId == category.id, onClick = { state.categoryId = if (state.categoryId == category.id) null else category.id }, label = { Text(category.nameAr) }) }
            Button(onClick = onApply, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("تطبيق الفلاتر") }
            OutlinedButton(onClick = { state.reset() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("مسح الفلاتر") }
        }
    }
}