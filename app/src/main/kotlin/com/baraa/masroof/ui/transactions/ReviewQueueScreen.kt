package com.baraa.masroof.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.DiscoveredIdentifierProposer
import com.baraa.masroof.ledger.IdentifierCandidate
import com.baraa.masroof.ledger.TransactionPostingStatus
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.FinancialTreatment
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
    val actionable = transactions.filter {
        it.postingStatus != TransactionPostingStatus.VOIDED &&
            it.postingStatus != TransactionPostingStatus.POSTED &&
            (it.postingStatus == TransactionPostingStatus.NEEDS_REVIEW || it.accountLinkNeedsReview || it.needsReview)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مراجعة العمليات") },
                navigationIcon = { IconButton(onClick = onBack) { Text("رجوع") } },
            )
        },
    ) { padding ->
        if (actionable.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("اكتملت مراجعة العمليات", style = MaterialTheme.typography.titleLarge)
                Text("لا توجد عمليات قابلة للمراجعة حالياً.")
                PrimaryButton(label = "العودة إلى العمليات", onClick = onBack)
                SecondaryButton(label = "الرئيسية", onClick = onHome)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Text("مراجعة ${actionable.size} عملية", style = MaterialTheme.typography.titleLarge) }
                items(actionable, key = { it.id }) { tx ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    tx.merchantOrBeneficiary ?: tx.transactionType.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("${tx.amount?.toPlainString().orEmpty()} ${tx.currency.name}")
                            }
                            Text(reviewReason(tx), color = MaterialTheme.colorScheme.error)
                            tx.accountOrCardLastFourDigits?.let { Text("المعرّف المنتهي بـ ••••$it") }
                            PrimaryButton(
                                label = if (tx.financialTreatment.requiresTwoAccounts) "تحديد الحسابين" else "تحديد الحساب",
                                onClick = { chosen = tx },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SecondaryButton(
                                    label = "إعادة التحليل",
                                    onClick = {
                                        scope.launch {
                                            app.transactionLinkingService.reanalyze(tx, accounts)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SecondaryButton(
                                    label = "تجاهل",
                                    onClick = {
                                        scope.launch {
                                            app.transactionLinkingService.ignoreTransaction(tx)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    chosen?.let { tx ->
        AccountChooserDialog(
            tx = tx,
            accounts = accounts,
            onDismiss = { chosen = null },
        ) { sourceId, destinationId, rememberLink, saveIdentifier, preferredAccount ->
            scope.launch {
                val candidate = preferredAccount?.let {
                    if (saveIdentifier) DiscoveredIdentifierProposer.propose(tx, it) else null
                }
                app.transactionLinkingService.applyUserLink(
                    transaction = tx,
                    sourceAccountId = sourceId,
                    destinationAccountId = destinationId,
                    accounts = accounts,
                    rememberForFuture = rememberLink,
                    identifierToAdd = candidate,
                )
                chosen = null
            }
        }
    }
}

@Composable
private fun AccountChooserDialog(
    tx: TransactionEntity,
    accounts: List<FinancialAccount>,
    onDismiss: () -> Unit,
    onConfirm: (Long?, Long?, Boolean, Boolean, FinancialAccount?) -> Unit,
) {
    val owned = remember(accounts) {
        accounts.filter { it.isActive && it.isOwnedByUser && it.systemAccountKey == null }
    }
    val twoSided = tx.financialTreatment.requiresTwoAccounts
    var source by remember {
        mutableStateOf(owned.firstOrNull { it.id == tx.sourceAccountId })
    }
    var destination by remember {
        mutableStateOf(owned.firstOrNull { it.id == tx.destinationAccountId })
    }
    var single by remember {
        mutableStateOf(
            owned.firstOrNull { it.id == tx.sourceAccountId || it.id == tx.destinationAccountId },
        )
    }
    var rememberLink by remember { mutableStateOf(false) }
    var saveIdentifier by remember { mutableStateOf(false) }

    val sourceOptions = when (tx.financialTreatment) {
        FinancialTreatment.CREDIT_CARD_PAYMENT -> owned.filter {
            it.accountType == AccountType.BANK_ACCOUNT ||
                it.accountType == AccountType.DIGITAL_WALLET ||
                it.accountType == AccountType.WALLET ||
                it.accountType == AccountType.CASH
        }
        else -> owned
    }
    val destinationOptions = when (tx.financialTreatment) {
        FinancialTreatment.CREDIT_CARD_PAYMENT -> owned.filter { it.accountType == AccountType.CREDIT_CARD }
        FinancialTreatment.INVESTMENT -> owned.filter {
            it.accountType == AccountType.INVESTMENT_ACCOUNT || it.accountType == AccountType.SUKUK_ACCOUNT
        }
        FinancialTreatment.CASH_WITHDRAWAL -> owned.filter { it.accountType == AccountType.CASH }
        else -> owned
    }

    val preferredForIdentifier: FinancialAccount? = if (twoSided) source ?: destination else single
    val proposed: IdentifierCandidate? = preferredForIdentifier?.let { DiscoveredIdentifierProposer.propose(tx, it) }
    val canConfirm = if (twoSided) {
        source != null && destination != null && source?.id != destination?.id
    } else {
        single != null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (twoSided) "تحديد الحسابين" else "تحديد الحساب") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(reviewReason(tx))
                if (twoSided) {
                    Text("من حساب (الخصم)", style = MaterialTheme.typography.titleSmall)
                    sourceOptions.forEach { account ->
                        FilterChip(
                            selected = source?.id == account.id,
                            onClick = {
                                source = account
                                saveIdentifier = false
                            },
                            label = { Text(account.displayName) },
                        )
                    }
                    Text("إلى حساب (الإضافة)", style = MaterialTheme.typography.titleSmall)
                    destinationOptions.forEach { account ->
                        FilterChip(
                            selected = destination?.id == account.id,
                            onClick = {
                                destination = account
                                saveIdentifier = false
                            },
                            label = { Text(account.displayName) },
                        )
                    }
                } else {
                    owned.forEach { account ->
                        FilterChip(
                            selected = single?.id == account.id,
                            onClick = {
                                single = account
                                saveIdentifier = false
                            },
                            label = { Text(account.displayName) },
                        )
                    }
                }
                Row {
                    Checkbox(checked = rememberLink, onCheckedChange = { rememberLink = it })
                    Text("تذكر هذا الربط")
                }
                if (proposed != null) {
                    Row {
                        Checkbox(checked = saveIdentifier, onCheckedChange = { saveIdentifier = it })
                        Text("حفظ المعرف المكتشف ••••${proposed.normalizedLastFour}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    if (twoSided) {
                        onConfirm(source?.id, destination?.id, rememberLink, saveIdentifier, preferredForIdentifier)
                    } else {
                        val account = single ?: return@TextButton
                        val isSource = tx.financialTreatment in setOf(
                            FinancialTreatment.EXPENSE,
                            FinancialTreatment.BANK_FEE,
                            FinancialTreatment.CASH_WITHDRAWAL,
                        )
                        onConfirm(
                            if (isSource) account.id else null,
                            if (isSource) null else account.id,
                            rememberLink,
                            saveIdentifier,
                            account,
                        )
                    }
                },
            ) { Text("اعتماد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

internal fun reviewReason(tx: TransactionEntity): String = when {
    tx.exclusionReason?.contains("محتمل تكرار") == true ->
        "محتمل تكرار لعملية موجودة"
    tx.exclusionReason?.contains("بداية المتابعة") == true ->
        "العملية قبل تاريخ بداية المتابعة"
    tx.amount == null ->
        "المبلغ غير مؤكد"
    tx.financialTreatment.requiresTwoAccounts &&
        (tx.sourceAccountId == null || tx.destinationAccountId == null) ->
        "يحتاج تحديد حساب المصدر والوجهة"
    tx.accountLinkSource.name == "UNLINKED" && tx.accountOrCardLastFourDigits == null ->
        "الرسالة لا تتضمن معرف حساب أو بطاقة"
    tx.accountLinkNeedsReview ->
        "يوجد أكثر من حساب متوافق"
    tx.accountLinkSource.name == "UNLINKED" ->
        "لم يُحدد الحساب"
    tx.status != com.baraa.masroof.transaction.TransactionStatus.COMPLETED ->
        "نوع العملية أو حالتها غير واضح"
    tx.financialTreatment == FinancialTreatment.PENDING_REVIEW ->
        "تحتاج تأكيد التصنيف أو نوع العملية"
    else -> "تحتاج مراجعة قبل الاعتماد"
}
