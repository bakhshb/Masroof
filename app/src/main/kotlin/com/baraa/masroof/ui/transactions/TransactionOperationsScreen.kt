package com.baraa.masroof.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.TransactionPostingStatus
import com.baraa.masroof.ui.sms.SmsPermissionRequiredBanner
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Operations tab. Always observes Room via Flow; never caches a stale
 * balance or list. The canonical [com.baraa.masroof.data.repository.SmsImportOrchestrator]
 * is the only path that creates new transactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionOperationsScreen(
    onOpenImport: () -> Unit = {},
    onOpenReview: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    val transactions by app.transactionRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    val accounts by app.financialAccountRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    val categories by app.categoryRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    val state = rememberSaveable(saver = TransactionOpsStateSaver) { TransactionOpsState() }
    var debouncedQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.query) { delay(250); debouncedQuery = state.query }
    
    val categoriesById = categories.associate { it.id to it.nameAr }
    val visible = remember(transactions, accounts, state, debouncedQuery) {
        TransactionSearchEngine.search(transactions, accounts, categoriesById, state.toFilter().copy(query = debouncedQuery))
    }
    var showFilters by remember { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var correcting by remember { mutableStateOf<TransactionEntity?>(null) }
    var correctionError by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("العمليات") }, actions = {
            IconButton(onClick = { showFilters = true }) { Icon(Icons.Filled.Search, "فلترة") }
            IconButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "إخفاء التفاصيل الفنية" else "إظهار التفاصيل الفنية") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(label = "استيراد رسائل البنك", onClick = onOpenImport, modifier = Modifier.weight(1f))
                SecondaryButton(label = "المراجعة", onClick = onOpenReview, modifier = Modifier.weight(0.7f))
                SecondaryButton(label = "فلترة", onClick = { showFilters = true }, modifier = Modifier.weight(0.6f))
            }
            SmsPermissionRequiredBanner(onImportClick = onOpenImport, modifier = Modifier.fillMaxWidth())
            correctionError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(state.query, { state.query = it; if (it.isEmpty()) debouncedQuery = "" }, label = { Text("بحث") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { state.query = ""; debouncedQuery = "" }) { Icon(Icons.Filled.Close, "مسح") } })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = state.needsReview, onClick = { state.needsReview = !state.needsReview }, label = { Text("يحتاج مراجعة") })
                FilterChip(selected = state.unlinked, onClick = { state.unlinked = !state.unlinked }, label = { Text("غير مرتبط") })
                FilterChip(selected = state.expenses, onClick = { state.expenses = !state.expenses }, label = { Text("مصروفات") })
                if (!state.isEmpty) AssistChip(onClick = { state.reset() }, label = { Text("مسح الفلاتر") })
            }
            Text("عدد النتائج: ${visible.size}")
            if (visible.isEmpty()) Text("لا توجد نتائج مطابقة", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text("قائمة المراجعة: ${visible.count { it.postingStatus == TransactionPostingStatus.NEEDS_REVIEW }}")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                items(visible, key = { it.id }) { tx ->
                    ReviewCard(
                        transaction = tx,
                        accounts = accounts,
                        categories = categories,
                        showAdvanced = showAdvanced,
                        onOpenReview = onOpenReview,
                        onCorrect = {
                            scope.launch {
                                correctionError = null
                                runCatching {
                                    app.transactionCorrectionService.reopenForCorrection(tx)
                                }.onSuccess { reopened ->
                                    correcting = reopened
                                }.onFailure {
                                    correctionError = "تعذّر فتح التصحيح — العملية ليست مُرحّلة"
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    if (showFilters) FilterSheet(state = state, accounts = accounts, categories = categories, onDismiss = { showFilters = false }, onApply = { showFilters = false })
    correcting?.let { tx ->
        AccountChooserDialog(
            tx = tx,
            accounts = accounts,
            onDismiss = { correcting = null },
        ) { sourceId, destinationId, rememberLink, saveIdentifier, preferredAccount, treatment ->
            scope.launch {
                val candidate = preferredAccount?.let {
                    if (saveIdentifier) com.baraa.masroof.ledger.DiscoveredIdentifierProposer.propose(tx, it) else null
                }
                app.transactionLinkingService.applyUserLink(
                    transaction = tx,
                    sourceAccountId = sourceId,
                    destinationAccountId = destinationId,
                    accounts = accounts,
                    rememberForFuture = rememberLink,
                    identifierToAdd = candidate,
                    financialTreatment = treatment,
                )
                correcting = null
            }
        }
    }
}

@Composable
private fun ReviewCard(
    transaction: TransactionEntity,
    accounts: List<FinancialAccount>,
    categories: List<com.baraa.masroof.data.db.Category>,
    showAdvanced: Boolean,
    onOpenReview: () -> Unit,
    onCorrect: () -> Unit,
) {
    val accountName = accounts.firstOrNull { it.id == transaction.sourceAccountId || it.id == transaction.destinationAccountId }?.displayName
    val categoryName = categories.firstOrNull { it.id == transaction.categoryId }?.nameAr
    val reviewReason = when {
        transaction.accountLinkSource.name == "UNLINKED" -> "تحتاج تحديد الحساب"
        transaction.postingStatus == TransactionPostingStatus.NEEDS_REVIEW -> "تحتاج مراجعة"
        transaction.postingStatus == TransactionPostingStatus.POSTED -> "مُرحّلة"
        else -> "جاهزة للاعتماد"
    }
    val needsAction = transaction.needsReview || transaction.postingStatus == TransactionPostingStatus.NEEDS_REVIEW || transaction.accountLinkNeedsReview
    val canCorrect = transaction.postingStatus == TransactionPostingStatus.POSTED ||
        transaction.postingStatus == TransactionPostingStatus.REVERSED
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = { if (needsAction) onOpenReview() },
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(transaction.merchantOrBeneficiary?.takeIf { it.isNotBlank() } ?: transaction.transactionType.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${transaction.amount?.toPlainString().orEmpty()} ${transaction.currency.name}")
            }
            Text(transaction.originalSender ?: "المؤسسة غير محددة", style = MaterialTheme.typography.bodyMedium)
            Text(accountName?.let { "الحساب: $it" } ?: "الحساب غير محدد")
            transaction.accountOrCardLastFourDigits?.let { Text("المعرّف المنتهي بـ ••••$it") }
            if (categoryName != null) Text("التصنيف: $categoryName")
            Text(reviewReason, color = if (reviewReason == "مُرحّلة" || reviewReason == "جاهزة للاعتماد") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (needsAction) {
                    TextButton(onClick = onOpenReview) { Text("فتح المراجعة") }
                }
                if (canCorrect) {
                    TextButton(onClick = onCorrect) { Text("تصحيح") }
                }
            }
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