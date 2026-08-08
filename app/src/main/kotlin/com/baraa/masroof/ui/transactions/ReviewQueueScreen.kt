package com.baraa.masroof.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.baraa.masroof.ledger.LinkPatternSuggestion
import com.baraa.masroof.ledger.TransactionPostingStatus
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.ui.TransactionTypeVisuals
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Active review queue: Room-backed rows + optional in-memory import session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewQueueScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onImport: () -> Unit = onBack,
    onBankMessages: () -> Unit = onBack,
) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    val transactions by app.transactionRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    val accounts by app.financialAccountRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    val importSession by app.importSessionStore.session.collectAsStateWithLifecycle(null)
    var chosen by remember { mutableStateOf<TransactionEntity?>(null) }
    var linkSaveState by remember { mutableStateOf<ReviewLinkSaveState>(ReviewLinkSaveState.Idle) }
    var materializing by remember { mutableStateOf(false) }
    var materializeError by remember { mutableStateOf<String?>(null) }
    val actionable = remember(transactions) {
        transactions.filter {
            it.postingStatus != TransactionPostingStatus.VOIDED &&
                it.postingStatus != TransactionPostingStatus.POSTED &&
                (it.postingStatus == TransactionPostingStatus.NEEDS_REVIEW || it.accountLinkNeedsReview || it.needsReview)
        }
    }
    val sessionMessageReview = remember(importSession) {
        importSession?.preview?.perTransaction.orEmpty().filter {
            com.baraa.masroof.data.repository.ScanPreview.isMessageReviewDisposition(it.disposition)
        }
    }
    val sessionPatternGates = remember(importSession) {
        importSession?.preview?.perTransaction.orEmpty().filter {
            com.baraa.masroof.data.repository.ScanPreview.isPatternApprovalDisposition(it.disposition)
        }
    }
    val sessionReady = importSession?.readyToImport ?: 0
    var patternSuggestions by remember { mutableStateOf<Map<Long, LinkPatternSuggestion>>(emptyMap()) }
    var smsBodies by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    // Precompute pattern suggestions off the UI thread — no LLM.
    LaunchedEffect(actionable.map { it.id }, accounts.map { it.id }) {
        patternSuggestions = withContext(Dispatchers.IO) {
            runCatching {
                app.linkPatternSuggester.suggestAll(actionable, accounts)
            }.getOrDefault(emptyMap())
        }
    }
    LaunchedEffect(actionable.map { it.id }) {
        smsBodies = withContext(Dispatchers.IO) {
            val out = LinkedHashMap<Long, String>()
            for (tx in actionable) {
                resolveReviewSmsBody(app, tx)?.let { out[tx.id] = it }
            }
            out
        }
    }

    fun materializeSessionMessageReview() {
        val session = importSession ?: return
        if (sessionMessageReview.isEmpty() || materializing) return
        materializing = true
        materializeError = null
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    app.importOrchestrator.commit(
                        scanPreview = session.preview,
                        trackingStartDate = session.trackingStartDate,
                        importedSms = session.messages,
                        mode = com.baraa.masroof.data.repository.SmsImportCommitMode.MESSAGE_REVIEW_ONLY,
                    )
                }
            }
            materializing = false
            outcome.onFailure {
                materializeError = it.message ?: "تعذر تجهيز قائمة المراجعة"
                android.util.Log.e("ReviewQueue", "materialize failed", it)
            }
            outcome.onSuccess { result ->
                // Refresh session counters from a re-scan without clearing navigation state.
                val refreshed = withContext(Dispatchers.IO) {
                    app.importOrchestrator.scan(session.messages, session.trackingStartDate, session.mode)
                }
                app.importSessionStore.replace(session.withPreview(refreshed))
                if (result.importedTransactions == 0 && result.needsReviewTransactions == 0) {
                    materializeError = "لم تُحفظ أي رسالة للمراجعة — تحقق من الربط والأنماط."
                }
            }
        }
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
                when {
                    sessionMessageReview.isNotEmpty() -> {
                        Text("رسائل بانتظار المراجعة", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${sessionMessageReview.size} رسالة من الفحص الحالي تحتاج ربط حساب أو تصنيفاً.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        sessionMessageReview.take(8).forEach { item ->
                            Text(
                                "• ${com.baraa.masroof.data.repository.ImportMessageLabels.dispositionAr(item.disposition)}" +
                                    (item.amount?.let { " — $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (sessionMessageReview.size > 8) {
                            Text("… و${sessionMessageReview.size - 8} أخرى")
                        }
                        materializeError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        PrimaryButton(
                            label = if (materializing) "جارٍ التجهيز…" else "حفظ في قائمة المراجعة",
                            enabled = !materializing,
                            onClick = { materializeSessionMessageReview() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "الفتح وحده لا يغيّر الحالة — الحفظ ينقل الرسائل إلى قائمة المراجعة.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    sessionPatternGates.isNotEmpty() -> {
                        Text("أنماط تحتاج اعتماد", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${sessionPatternGates.size} رسالة غير جاهزة للمراجعة كعمليات — اعتمد الأنماط أولاً من «رسائل البنوك».",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PrimaryButton(
                            label = "فتح رسائل البنوك",
                            onClick = onBankMessages,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    sessionReady > 0 -> {
                        Text("اكتملت المراجعة", style = MaterialTheme.typography.titleLarge)
                        Text("$sessionReady عملية جاهزة للاستيراد")
                        PrimaryButton(
                            label = "استيراد $sessionReady عملية",
                            onClick = onImport,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> {
                        Text("اكتملت مراجعة العمليات", style = MaterialTheme.typography.titleLarge)
                        Text("لا توجد عمليات قابلة للمراجعة حالياً.")
                        Text(
                            "بعد اعتماد النوع والحساب تُرحَّل القيود ويتغيّر صافي الثروة.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SecondaryButton(label = "العودة", onClick = onBack, modifier = Modifier.fillMaxWidth())
                SecondaryButton(label = "الرئيسية", onClick = onHome, modifier = Modifier.fillMaxWidth())
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text("مراجعة ${actionable.size} عملية", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "اختر نوع العملية ثم الحساب. الاقتراحات من أنماط الرسائل وتأكيداتك السابقة — بدون نموذج ذكاء.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(actionable, key = { it.id }) { tx ->
                    val hint = patternSuggestions[tx.id]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    tx.merchantOrBeneficiary?.takeIf { it.isNotBlank() }
                                        ?: ReviewClassification.friendlyType(tx),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("${tx.amount?.toPlainString().orEmpty()} ${tx.currency.name}")
                            }
                            Text(
                                "نوع الرسالة: ${ReviewClassification.friendlyType(tx)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "التصنيف الحالي: ${ReviewClassification.treatmentLabel(tx.financialTreatment)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (tx.financialTreatment == FinancialTreatment.PENDING_REVIEW) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            hint?.let { s ->
                                Text(
                                    "اقتراح جاهز (${s.confidence}%): ${ReviewClassification.treatmentLabel(s.treatment)} — ${s.reasonAr}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            tx.transactionDate?.let { date ->
                                val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ar"))
                                Text("التاريخ: ${date.format(fmt)}")
                            }
                            tx.accountOrCardLastFourDigits?.let { Text("المعرّف في الرسالة: ••••$it") }
                            Text(
                                "سبب المراجعة: ${reviewReason(tx)}",
                                color = MaterialTheme.colorScheme.error,
                            )
                            if (tx.originalSender != null) {
                                Text(
                                    "المرسل: ${tx.originalSender} — إن وُجد حسابان بنفس المرسل اعتمد على آخر 4 أرقام.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            ReviewSmsBodyBlock(body = smsBodies[tx.id])
                            PrimaryButton(
                                label = "تصنيف وربط الحساب",
                                onClick = {
                                    linkSaveState = ReviewLinkSaveState.Idle
                                    chosen = tx
                                },
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
                                            when (
                                                val result = withContext(Dispatchers.IO) {
                                                    app.transactionLinkingService.ignoreTransaction(tx)
                                                }
                                            ) {
                                                is com.baraa.masroof.ledger.LinkApplyResult.Success -> Unit
                                                is com.baraa.masroof.ledger.LinkApplyResult.ValidationError -> {
                                                    linkSaveState = ReviewLinkSaveState.ValidationError(result.messageAr)
                                                }
                                                is com.baraa.masroof.ledger.LinkApplyResult.Failure -> {
                                                    linkSaveState = ReviewLinkSaveState.Failure(result.messageAr)
                                                }
                                            }
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
            smsBody = smsBodies[tx.id],
            patternSuggestion = patternSuggestions[tx.id],
            saveState = linkSaveState,
            onDismiss = {
                if (linkSaveState !is ReviewLinkSaveState.Saving) {
                    chosen = null
                    linkSaveState = ReviewLinkSaveState.Idle
                }
            },
        ) { sourceId, destinationId, rememberLink, saveIdentifier, preferredAccount, treatment, selectedType ->
            if (linkSaveState is ReviewLinkSaveState.Saving) return@AccountChooserDialog
            linkSaveState = ReviewLinkSaveState.Saving
            scope.launch {
                val candidate = preferredAccount?.let {
                    if (saveIdentifier) DiscoveredIdentifierProposer.propose(tx, it) else null
                }
                val result = withContext(Dispatchers.IO) {
                    app.transactionLinkingService.applyUserLink(
                        transaction = tx,
                        sourceAccountId = sourceId,
                        destinationAccountId = destinationId,
                        accounts = accounts,
                        rememberForFuture = rememberLink,
                        identifierToAdd = candidate,
                        financialTreatment = treatment,
                        transactionType = selectedType,
                    )
                }
                when (result) {
                    is com.baraa.masroof.ledger.LinkApplyResult.Success -> {
                        linkSaveState = ReviewLinkSaveState.Success(
                            conflictWarning = result.identifierOutcome?.message,
                        )
                        chosen = null
                        linkSaveState = ReviewLinkSaveState.Idle
                    }
                    is com.baraa.masroof.ledger.LinkApplyResult.ValidationError -> {
                        linkSaveState = ReviewLinkSaveState.ValidationError(result.messageAr)
                    }
                    is com.baraa.masroof.ledger.LinkApplyResult.Failure -> {
                        android.util.Log.e("ReviewQueue", "link save failed", result.cause)
                        linkSaveState = ReviewLinkSaveState.Failure(result.messageAr)
                    }
                }
            }
        }
    }
}

/** Explicit persistence state for the review classify/link dialog. */
sealed class ReviewLinkSaveState {
    data object Idle : ReviewLinkSaveState()
    data object Saving : ReviewLinkSaveState()
    data class Success(val conflictWarning: String? = null) : ReviewLinkSaveState()
    data class ValidationError(val messageAr: String) : ReviewLinkSaveState()
    data class Failure(val messageAr: String) : ReviewLinkSaveState()
}

@Composable
internal fun AccountChooserDialog(
    tx: TransactionEntity,
    accounts: List<FinancialAccount>,
    onDismiss: () -> Unit,
    patternSuggestion: LinkPatternSuggestion? = null,
    smsBody: String? = null,
    saveState: ReviewLinkSaveState = ReviewLinkSaveState.Idle,
    onConfirm: (Long?, Long?, Boolean, Boolean, FinancialAccount?, FinancialTreatment, com.baraa.masroof.transaction.TransactionType) -> Unit,
) {
    val owned = remember(accounts) {
        accounts.filter { it.isActive && it.isOwnedByUser && it.systemAccountKey == null }
    }
    var selectedChoice by remember(tx.id, patternSuggestion) {
        val fromPattern = patternSuggestion?.let { s ->
            ReviewClassification.choosableChoices.firstOrNull { it.treatment == s.treatment }
        }
        mutableStateOf(fromPattern ?: ReviewClassification.suggestedChoice(tx))
    }
    val treatment = selectedChoice.treatment
    val twoSided = treatment.requiresTwoAccounts
    var source by remember(tx.id, patternSuggestion) {
        mutableStateOf(
            patternSuggestion?.sourceAccountId?.let { id -> owned.firstOrNull { it.id == id } }
                ?: owned.firstOrNull { it.id == tx.sourceAccountId },
        )
    }
    var destination by remember(tx.id, patternSuggestion) {
        mutableStateOf(
            patternSuggestion?.destinationAccountId?.let { id -> owned.firstOrNull { it.id == id } }
                ?: owned.firstOrNull { it.id == tx.destinationAccountId },
        )
    }
    var single by remember(tx.id, patternSuggestion) {
        val fromPattern = patternSuggestion?.let { s ->
            if (s.treatment.requiresTwoAccounts) null
            else owned.firstOrNull { it.id == s.sourceAccountId || it.id == s.destinationAccountId }
        }
        mutableStateOf(
            fromPattern
                ?: owned.firstOrNull { it.id == tx.sourceAccountId || it.id == tx.destinationAccountId },
        )
    }
    var rememberLink by remember { mutableStateOf(true) }
    var saveIdentifier by remember { mutableStateOf(false) }

    val lastFour = tx.accountOrCardLastFourDigits?.takeLast(4)

    val sourceOptions = when (treatment) {
        FinancialTreatment.CREDIT_CARD_PAYMENT -> owned.filter {
            it.accountType == AccountType.BANK_ACCOUNT ||
                it.accountType == AccountType.DIGITAL_WALLET ||
                it.accountType == AccountType.WALLET ||
                it.accountType == AccountType.CASH
        }
        else -> owned
    }
    val destinationOptions = when (treatment) {
        FinancialTreatment.CREDIT_CARD_PAYMENT -> owned.filter { it.accountType == AccountType.CREDIT_CARD }
        FinancialTreatment.INVESTMENT -> owned.filter {
            it.accountType == AccountType.INVESTMENT_ACCOUNT || it.accountType == AccountType.SUKUK_ACCOUNT
        }
        FinancialTreatment.CASH_WITHDRAWAL -> owned
        else -> owned
    }

    val preferredForIdentifier: FinancialAccount? = if (twoSided) source ?: destination else single
    val proposed: IdentifierCandidate? = preferredForIdentifier?.let { DiscoveredIdentifierProposer.propose(tx, it) }
    val saving = saveState is ReviewLinkSaveState.Saving
    val canConfirm = !saving &&
        treatment != FinancialTreatment.PENDING_REVIEW &&
        if (twoSided) {
            source != null && destination != null && source?.id != destination?.id
        } else {
            single != null
        }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("تصنيف وربط العملية") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("من الرسالة: ${ReviewClassification.friendlyType(tx)}")
                Text(reviewReason(tx))
                ReviewSmsBodyBlock(body = smsBody, compact = false)
                lastFour?.let {
                    Text(
                        "آخر 4 في الرسالة: ••••$it — اختر الحساب المطابق إن وُجد أكثر من حساب لنفس البنك.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                patternSuggestion?.let { s ->
                    Text(
                        "اقتراح من نمط الرسالة / تأكيد سابق (${s.confidence}%): ${s.reasonAr}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                when (saveState) {
                    is ReviewLinkSaveState.ValidationError -> Text(
                        saveState.messageAr,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is ReviewLinkSaveState.Failure -> Text(
                        saveState.messageAr,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is ReviewLinkSaveState.Saving -> Text(
                        "جارٍ الحفظ…",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> Unit
                }
                Text("1) ما نوع هذه العملية؟", style = MaterialTheme.typography.titleSmall)
                ReviewClassification.choosableChoices.forEach { option ->
                    FilterChip(
                        selected = selectedChoice.id == option.id,
                        enabled = !saving,
                        onClick = {
                            selectedChoice = option
                            saveIdentifier = false
                            if (option.treatment.requiresTwoAccounts) {
                                single = null
                            } else {
                                source = null
                                destination = null
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = TransactionTypeVisuals.icon(option.type),
                                contentDescription = null,
                            )
                        },
                        label = { Text(option.label) },
                    )
                }
                if (treatment != FinancialTreatment.PENDING_REVIEW) {
                    Text(
                        ReviewClassification.directionLabel(selectedChoice.direction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        selectedChoice.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (twoSided) {
                    Text("2) من حساب (الخصم)", style = MaterialTheme.typography.titleSmall)
                    sourceOptions.forEach { account ->
                        FilterChip(
                            selected = source?.id == account.id,
                            enabled = !saving,
                            onClick = {
                                source = account
                                saveIdentifier = false
                            },
                            label = { Text(accountChipLabel(account)) },
                        )
                    }
                    Text("إلى حساب (الإضافة)", style = MaterialTheme.typography.titleSmall)
                    destinationOptions.forEach { account ->
                        FilterChip(
                            selected = destination?.id == account.id,
                            enabled = !saving,
                            onClick = {
                                destination = account
                                saveIdentifier = false
                            },
                            label = { Text(accountChipLabel(account)) },
                        )
                    }
                    if (destinationOptions.isEmpty() && treatment == FinancialTreatment.CREDIT_CARD_PAYMENT) {
                        Text(
                            "لا توجد بطاقة ائتمانية. أضف البطاقة أو اختر نوعًا آخر.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else if (treatment != FinancialTreatment.PENDING_REVIEW) {
                    Text("2) أي حساب؟", style = MaterialTheme.typography.titleSmall)
                    owned.forEach { account ->
                        FilterChip(
                            selected = single?.id == account.id,
                            enabled = !saving,
                            onClick = {
                                single = account
                                saveIdentifier = false
                            },
                            label = { Text(accountChipLabel(account)) },
                        )
                    }
                }
                Row {
                    Checkbox(
                        checked = rememberLink,
                        enabled = !saving,
                        onCheckedChange = { rememberLink = it },
                    )
                    Text("تذكر هذا الربط للمرات القادمة")
                }
                if (proposed != null) {
                    Row {
                        Checkbox(
                            checked = saveIdentifier,
                            enabled = !saving,
                            onCheckedChange = { saveIdentifier = it },
                        )
                        Text("حفظ المعرف المكتشف ••••${proposed.normalizedLastFour} (${proposed.identifierType.name})")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    if (saving) return@TextButton
                    if (twoSided) {
                        onConfirm(source?.id, destination?.id, rememberLink, saveIdentifier, preferredForIdentifier, treatment, selectedChoice.type)
                    } else {
                        val account = single ?: return@TextButton
                        val isSource = ReviewClassification.isSourceSide(treatment)
                        onConfirm(
                            if (isSource) account.id else null,
                            if (isSource) null else account.id,
                            rememberLink,
                            saveIdentifier,
                            account,
                            treatment,
                            selectedChoice.type,
                        )
                    }
                },
            ) { Text(if (saving) "جارٍ الحفظ…" else "اعتماد وترحيل") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

private fun accountChipLabel(account: FinancialAccount): String {
    val typeHint = when (account.accountType) {
        AccountType.CREDIT_CARD -> "بطاقة"
        AccountType.BANK_ACCOUNT -> "حساب"
        AccountType.DIGITAL_WALLET, AccountType.WALLET -> "محفظة"
        AccountType.CASH -> "نقد"
        else -> account.accountType.name
    }
    return "${account.displayName} ($typeHint)"
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
        "الرسالة لا تتضمن معرف حساب أو بطاقة — ومرسل مشترك لا يكفي وحده"
    tx.accountLinkNeedsReview ->
        "يوجد أكثر من حساب لنفس المرسل — اختر بالحساب وآخر 4 أرقام"
    tx.accountLinkSource.name == "UNLINKED" ->
        "لم يُحدد الحساب"
    tx.status != com.baraa.masroof.transaction.TransactionStatus.COMPLETED ->
        "نوع العملية أو حالتها غير واضح"
    tx.financialTreatment == FinancialTreatment.PENDING_REVIEW ->
        "تحتاج اختيار النوع: مصروف، حوالة صادرة/واردة خارجية، تحويل داخلي، أو سداد بطاقة"
    else -> "تحتاج مراجعة قبل الاعتماد"
}

/** Load stored SMS body; recover from inbox for older imports. Never logs the body. */
internal suspend fun resolveReviewSmsBody(
    app: MasroofApplication,
    tx: TransactionEntity,
): String? {
    val stored = app.transactionSmsBodyRepository.getBody(tx.id)?.trim()?.takeIf { it.isNotEmpty() }
    if (stored != null) return stored
    val recovered = runCatching {
        app.smsRepository.findBodyBySenderAndTimestamp(
            sender = tx.originalSender,
            timestampMillis = tx.smsTimestamp,
        )
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    if (recovered != null) {
        runCatching { app.transactionSmsBodyRepository.save(tx.id, recovered) }
    }
    return recovered
}

@Composable
private fun ReviewSmsBodyBlock(body: String?, compact: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("نص الرسالة", style = MaterialTheme.typography.titleSmall)
        if (body.isNullOrBlank()) {
            Text(
                "نص الرسالة غير متوفر لهذه العملية. إن كانت قديمة فأعد الاستيراد مع صلاحية الرسائل لاستعادته من الجهاز.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                SelectionContainer {
                    val scroll = rememberScrollState()
                    Text(
                        text = body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (compact) {
                                    Modifier
                                        .heightIn(max = 240.dp)
                                        .verticalScroll(scroll)
                                } else {
                                    Modifier
                                },
                            )
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
