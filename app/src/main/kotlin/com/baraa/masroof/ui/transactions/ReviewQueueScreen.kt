package com.baraa.masroof.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.TransactionPostingStatus
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import kotlinx.coroutines.launch

/** Active, Room-backed review queue. Unresolved items stay persisted here until confirmed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewQueueScreen(onBack: () -> Unit, onHome: () -> Unit) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    val transactions by app.transactionRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    val accounts by app.financialAccountRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    var chosen by remember { mutableStateOf<TransactionEntity?>(null) }
    val actionable = transactions.filter { it.postingStatus == TransactionPostingStatus.NEEDS_REVIEW || it.accountLinkNeedsReview || it.needsReview }

    Scaffold(topBar = { TopAppBar(title = { Text("مراجعة العمليات") }, navigationIcon = { IconButton(onClick = onBack) { Text("رجوع") } }) }) { padding ->
        if (actionable.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("اكتملت مراجعة العمليات", style = MaterialTheme.typography.titleLarge)
                Text("لا توجد عمليات قابلة للمراجعة حالياً.")
                PrimaryButton(label = "العودة إلى العمليات", onClick = onBack)
                SecondaryButton(label = "الرئيسية", onClick = onHome)
            }
        } else LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("مراجعة ${actionable.size} عملية", style = MaterialTheme.typography.titleLarge) }
            items(actionable, key = { it.id }) { tx ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tx.merchantOrBeneficiary ?: tx.transactionType.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${tx.amount?.toPlainString().orEmpty()} ${tx.currency.name}")
                        }
                        Text(reviewReason(tx), color = MaterialTheme.colorScheme.error)
                        tx.accountOrCardLastFourDigits?.let { Text("المعرّف المنتهي بـ ••••$it") }
                        PrimaryButton(label = "تحديد الحساب", onClick = { chosen = tx }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    chosen?.let { tx -> AccountChooserDialog(tx, accounts, onDismiss = { chosen = null }) { account, rememberLink ->
        scope.launch {
            val source = if (tx.financialTreatment in setOf(com.baraa.masroof.transaction.FinancialTreatment.EXPENSE, com.baraa.masroof.transaction.FinancialTreatment.BANK_FEE)) account.id else null
            val destination = if (source == null) account.id else null
            app.transactionLinkingService.applyUserLink(tx, source, destination, accounts, rememberForFuture = rememberLink)
            chosen = null
        }
    } }
}

@Composable
private fun AccountChooserDialog(tx: TransactionEntity, accounts: List<FinancialAccount>, onDismiss: () -> Unit, onConfirm: (FinancialAccount, Boolean) -> Unit) {
    var selected by remember { mutableStateOf<FinancialAccount?>(null) }
    var rememberLink by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تحديد الحساب") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(reviewReason(tx))
            accounts.filter { it.isActive && it.isOwnedByUser && it.systemAccountKey == null }.forEach { account ->
                FilterChip(selected = selected?.id == account.id, onClick = { selected = account }, label = { Text(account.displayName) })
            }
            Row { Checkbox(checked = rememberLink, onCheckedChange = { rememberLink = it }); Text("تذكر هذا الربط") }
        }
    }, confirmButton = { TextButton(enabled = selected != null, onClick = { selected?.let { onConfirm(it, rememberLink) } }) { Text("اعتماد") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

private fun reviewReason(tx: TransactionEntity): String = when {
    tx.accountLinkSource.name == "UNLINKED" && tx.accountOrCardLastFourDigits == null -> "لم تتضمن الرسالة رقمًا يحدد الحساب أو البطاقة"
    tx.accountLinkSource.name == "UNLINKED" -> "يحتاج تحديد الحساب"
    tx.accountLinkNeedsReview -> "يوجد أكثر من حساب مطابق"
    tx.status != com.baraa.masroof.transaction.TransactionStatus.COMPLETED -> "نوع العملية أو حالتها غير واضح"
    else -> "يحتاج مراجعة قبل الاعتماد"
}
