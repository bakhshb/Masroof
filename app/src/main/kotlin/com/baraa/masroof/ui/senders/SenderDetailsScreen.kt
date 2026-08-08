package com.baraa.masroof.ui.senders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.BuildConfig
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.data.repository.SenderProfile
import com.baraa.masroof.data.repository.TemplateStatusLabels
import com.baraa.masroof.sms.FinancialMessageSummary
import com.baraa.masroof.sms.PatternDraftFactory
import com.baraa.masroof.sms.PatternDiscoveryService
import com.baraa.masroof.sms.PatternFamilyRuntimeState
import com.baraa.masroof.sms.PatternRuntimeEligibility
import com.baraa.masroof.sms.SenderNormalizer
import com.baraa.masroof.sms.SmsImportRange
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.sms.patternFamilyRuntimeState
import com.baraa.masroof.transaction.TransactionType
import java.time.LocalDate
import com.baraa.masroof.transaction.TransactionTypeTaxonomy
import com.baraa.masroof.ui.TransactionTypeVisuals
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tabs:
 * - القوالب → APPROVED templates only
 * - تحتاج اعتماد → UNKNOWN candidates only (never approved)
 * - الرسائل → SMS-linked pattern history
 *
 */
private enum class SenderDetailsTab {
    TEMPLATES,
    CANDIDATES,
    MESSAGES,
}

internal fun senderPatternFamilyStatusAr(variants: List<MessagePattern>): String =
    when (patternFamilyRuntimeState(variants)) {
        PatternFamilyRuntimeState.APPROVED_CURRENT -> "معتمد"
        PatternFamilyRuntimeState.APPROVED_STALE -> "نمط قديم — يحتاج إعادة بناء"
        PatternFamilyRuntimeState.NOT_APPROVED -> "يحتاج اعتماد"
    }

private fun logPatternDiscovery(
    senderProfileId: Long,
    result: com.baraa.masroof.sms.PatternDiscoveryResult,
) {
    if (!BuildConfig.DEBUG) return
    android.util.Log.d(
        "PatternDiscovery",
        "senderProfileId=$senderProfileId inputMessages=${result.inputMessages} " +
            "processedMessages=${result.processedMessages} skippedOtp=${result.skippedOtp} " +
            "skippedNonFinancial=${result.skippedNonFinancial} " +
            "coreFailed=${result.coreFailedMessages} " +
            "optionalStageFailures=${result.optionalStageFailureCount} " +
            "reconciled=${result.isReconciled()}",
    )
    result.failureBreakdown().forEach { breakdown ->
        android.util.Log.w(
            "PatternDiscovery",
            "stage=${breakdown.stage} exception=${breakdown.exceptionClass} " +
                "count=${breakdown.count} optional=${breakdown.optional} " +
                "sampleHashes=${breakdown.sampleBodyHashes}",
        )
    }
}

@Composable
fun SenderDetailsScreen(
    senderProfileId: Long,
    onBack: () -> Unit,
    onTemplateClick: (Long) -> Unit,
    onReturnToImport: (() -> Unit)? = null,
    onOpenDraftEditor: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val returnToImport by app.importSessionStore.returnToImportAfterTemplates
        .collectAsStateWithLifecycle(initialValue = false)
    var profile by remember(senderProfileId) { mutableStateOf<SenderProfile?>(null) }
    var patterns by remember(senderProfileId) { mutableStateOf<List<MessagePattern>>(emptyList()) }
    var tab by remember { mutableStateOf(SenderDetailsTab.TEMPLATES) }
    var expandedFamilyIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var runningPatternAction by remember { mutableStateOf<String?>(null) }
    var ignoreTarget by remember { mutableStateOf<MessagePattern?>(null) }

    // Manual financial picker state.
    var pickerSummaries by remember { mutableStateOf<List<FinancialMessageSummary>>(emptyList()) }
    var pickerLoading by remember { mutableStateOf<Long?>(null) }
    var pickerError by remember { mutableStateOf<String?>(null) }
    var draftFailure by remember { mutableStateOf<String?>(null) }

    // Zero-discovery diagnostic state.
    var zeroDiscovery by remember { mutableStateOf<com.baraa.masroof.sms.PatternDiscoveryResult?>(null) }

    fun showSnackbar(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                withDismissAction = actionLabel == null,
            )
            if (result == SnackbarResult.ActionPerformed && onAction != null) onAction()
        }
    }

    fun reload() {
        scope.launch {
            try {
                profile = withContext(Dispatchers.IO) {
                    app.senderProfileRepository.getById(senderProfileId)
                }
                patterns = withContext(Dispatchers.IO) {
                    app.messagePatternRepository.getForSender(senderProfileId)
                }.filter { it.definition.deprecatedAt == null }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (failure is VirtualMachineError) throw failure
                if (BuildConfig.DEBUG) {
                    android.util.Log.e(
                        "PatternDiscovery",
                        "senderProfileId=$senderProfileId action=reload failure=${failure.javaClass.simpleName}",
                    )
                }
                showSnackbar("تعذر تحديث قائمة الأنماط")
            }
        }
    }

    fun launchPatternAction(
        actionName: String,
        failureMessage: String,
        action: suspend () -> Unit,
    ) {
        if (runningPatternAction != null) return
        scope.launch {
            runningPatternAction = actionName
            try {
                action()
                reload()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (failure is VirtualMachineError) throw failure
                if (BuildConfig.DEBUG) {
                    android.util.Log.e(
                        "PatternDiscovery",
                        "senderProfileId=$senderProfileId action=$actionName failure=${failure.javaClass.simpleName}",
                    )
                }
                showSnackbar(failureMessage)
            } finally {
                runningPatternAction = null
            }
        }
    }

    DisposableEffect(lifecycleOwner, senderProfileId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reload()
                PatternActionResultHolder.consume()?.let { result ->
                    showSnackbar(
                        message = result.message,
                        actionLabel = result.reviewPatternId?.let { "مراجعة النمط" },
                        onAction = result.reviewPatternId?.let { id -> { onTemplateClick(id) } },
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        reload()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val title = profile?.displayInstitutionName ?: profile?.displaySender ?: "تفاصيل المرسل"
    val approvedTemplates = patterns.filter {
        TemplateStatusLabels.isApprovedTemplate(it.definition.status)
    }
    val candidatePatterns = patterns.filter {
        TemplateStatusLabels.isCandidate(it.definition.status) &&
            !it.definition.templateText.isNullOrBlank()
    }
    val approvedFamilies = approvedTemplates.groupBy {
        it.family?.id ?: -it.definition.id
    }
    val currentApprovedFamilyCount = approvedFamilies.values.count {
        patternFamilyRuntimeState(it) == PatternFamilyRuntimeState.APPROVED_CURRENT
    }
    val messageCount = patterns.sumOf { it.definition.exampleCount.coerceAtLeast(0) }

    LaunchedEffect(candidatePatterns.size, returnToImport) {
        if (returnToImport && candidatePatterns.isNotEmpty() && tab == SenderDetailsTab.TEMPLATES) {
            tab = SenderDetailsTab.CANDIDATES
        }
    }

    Scaffold(
        topBar = { MasroofTopAppBar(title, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.x4)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            if (returnToImport && onReturnToImport != null) {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = FinancialShapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        Modifier.padding(Spacing.x3),
                        verticalArrangement = Arrangement.spacedBy(Spacing.x2),
                    ) {
                        Text(
                            "جلسة استيراد نشطة — اعتمد الأنماط ثم عد لمتابعة الاستيراد.",
                            style = FinancialTypography.merchant,
                        )
                        PrimaryButton(
                            "العودة إلى الاستيراد",
                            onClick = {
                                app.importSessionStore.markTemplatesChanged()
                                onReturnToImport()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryValue(label = "قالب معتمد", value = currentApprovedFamilyCount)
                SummaryValue(label = "رسالة", value = messageCount)
                SummaryValue(label = "نمط يحتاج اعتماد", value = candidatePatterns.size)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                FilterChip(
                    selected = tab == SenderDetailsTab.TEMPLATES,
                    onClick = { tab = SenderDetailsTab.TEMPLATES },
                    label = { Text("القوالب (${approvedTemplates.size})") },
                )
                FilterChip(
                    selected = tab == SenderDetailsTab.CANDIDATES,
                    onClick = { tab = SenderDetailsTab.CANDIDATES },
                    label = { Text("تحتاج اعتماد (${candidatePatterns.size})") },
                )
                FilterChip(
                    selected = tab == SenderDetailsTab.MESSAGES,
                    onClick = { tab = SenderDetailsTab.MESSAGES },
                    label = { Text("الرسائل ($messageCount)") },
                )
            }

            when (tab) {
                SenderDetailsTab.TEMPLATES -> {
                    if (approvedTemplates.isEmpty()) {
                        Text("لا توجد قوالب معتمدة بعد.", style = FinancialTypography.metadata)
                    } else {
                        approvedFamilies
                            .toSortedMap()
                            .forEach { (familyId, variants) ->
                                val familyName = variants.first().family?.displayName
                                    ?: variants.first().definition.userFriendlyName
                                val messages = variants.sumOf { it.definition.exampleCount }
                                val runtimeState = patternFamilyRuntimeState(variants)
                                Surface(
                                    Modifier.fillMaxWidth().clickable {
                                        expandedFamilyIds = if (familyId in expandedFamilyIds) {
                                            expandedFamilyIds - familyId
                                        } else expandedFamilyIds + familyId
                                    },
                                    shape = FinancialShapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Column(Modifier.padding(Spacing.x3)) {
                                        Text(familyName, style = FinancialTypography.merchant)
                                        Text(
                                            "$messages رسالة · ${senderPatternFamilyStatusAr(variants)}",
                                            style = FinancialTypography.metadata,
                                        )
                                        if (runtimeState == PatternFamilyRuntimeState.APPROVED_STALE) {
                                            SecondaryButton(
                                                "تحديث النمط",
                                                onClick = {
                                                    scope.launch {
                                                        val result = app.smsRepository.loadInboxResult(
                                                            SmsImportRange.lastDays(LocalDate.now(), 30),
                                                        )
                                                        val inbox = (
                                                            result as? com.baraa.masroof.sms.SmsInboxLoadResult.Success
                                                            )?.messages.orEmpty()
                                                        val senderMessages = inbox.filter {
                                                            SenderNormalizer.normalize(it.sender) ==
                                                                profile?.normalizedSenderKey
                                                        }
                                                        val repair = app.messagePatternRepository
                                                            .rebuildStaleForSender(
                                                                senderProfileId,
                                                                senderMessages,
                                                            )
                                                        showSnackbar(
                                                            if (repair.rebuildSucceeded) "تم تحديث النمط"
                                                            else "تعذر التحديث تلقائيًا — حاول بعد توفر رسائل حديثة",
                                                        )
                                                        app.importSessionStore.markTemplatesChanged()
                                                        reload()
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        } else if (familyId in expandedFamilyIds) {
                                            val pattern = variants
                                                .filter(PatternRuntimeEligibility::isEligible)
                                                .maxBy { it.definition.version }
                                            ApprovedTemplateCard(
                                                pattern = pattern,
                                                onEdit = { onTemplateClick(pattern.definition.id) },
                                                onDisable = {
                                                    scope.launch {
                                                        app.messagePatternRepository.setStatus(
                                                            pattern.definition.id,
                                                            MessagePatternStatus.IGNORED,
                                                        )
                                                        app.importSessionStore.markTemplatesChanged()
                                                        showSnackbar("تم تعطيل النمط")
                                                        reload()
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }
                SenderDetailsTab.CANDIDATES -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                        SecondaryButton(
                            if (runningPatternAction == "rebuild") "جارٍ إعادة البناء…"
                            else "إعادة بناء الأنماط",
                            enabled = runningPatternAction == null,
                            onClick = {
                                launchPatternAction(
                                    actionName = "rebuild",
                                    failureMessage = "تعذر إعادة بناء الأنماط. لم يتم حذف أي بيانات.",
                                ) {
                                    val result = app.smsRepository.loadInboxResult(
                                        SmsImportRange.lastDays(LocalDate.now(), 30),
                                    )
                                    val inbox = when (result) {
                                        is com.baraa.masroof.sms.SmsInboxLoadResult.Success -> result.messages
                                        is com.baraa.masroof.sms.SmsInboxLoadResult.PermissionDenied ->
                                            error("SMS_PERMISSION_DENIED")
                                        is com.baraa.masroof.sms.SmsInboxLoadResult.Failed ->
                                            error("SMS_LOAD_FAILED")
                                    }
                                    val senderMessages = inbox.filter {
                                        SenderNormalizer.normalize(it.sender) == profile?.normalizedSenderKey
                                    }
                                    val summary = app.messagePatternRepository.rebuildForSender(
                                        senderProfileId,
                                        senderMessages,
                                    )
                                    summary.discovery?.let { logPatternDiscovery(senderProfileId, it) }
                                    app.importSessionStore.markTemplatesChanged()
                                    if (summary.discovery?.patterns.isNullOrEmpty() &&
                                        senderMessages.isNotEmpty()
                                    ) {
                                        zeroDiscovery = summary.discovery
                                    } else {
                                        showSnackbar(
                                            "تم تحديث ${summary.rebuiltApprovedFamilies} أنماط معتمدة — " +
                                                "${summary.newCandidateFamilies} جديدة تحتاج اعتماد",
                                        )
                                        tab = SenderDetailsTab.CANDIDATES
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(Spacing.x2))
                    PrimaryButton(
                        if (runningPatternAction == "discover") "جارٍ اكتشاف الأنماط…"
                        else "اكتشاف أنماط جديدة من آخر 30 يومًا",
                        enabled = runningPatternAction == null,
                        onClick = {
                            launchPatternAction(
                                actionName = "discover",
                                failureMessage = "تعذر حفظ الأنماط. لم يتم حذف أي بيانات.",
                            ) {
                                val result = app.smsRepository.loadInboxResult(
                                    SmsImportRange.lastDays(LocalDate.now(), 30),
                                )
                                val inbox = when (result) {
                                    is com.baraa.masroof.sms.SmsInboxLoadResult.Success -> result.messages
                                    is com.baraa.masroof.sms.SmsInboxLoadResult.PermissionDenied ->
                                        error("SMS_PERMISSION_DENIED")
                                    is com.baraa.masroof.sms.SmsInboxLoadResult.Failed ->
                                        error("SMS_LOAD_FAILED")
                                }
                                val senderMessages = inbox.filter {
                                    SenderNormalizer.normalize(it.sender) == profile?.normalizedSenderKey
                                }
                                val existing = withContext(Dispatchers.IO) {
                                    app.messagePatternRepository.getForSender(senderProfileId)
                                        .map { it.definition }
                                }
                                val discovery = withContext(Dispatchers.Default) {
                                    PatternDiscoveryService.discoverSafely(senderMessages, existing)
                                }
                                logPatternDiscovery(senderProfileId, discovery)
                                if (discovery.patterns.isEmpty()) {
                                    if (senderMessages.isNotEmpty()) {
                                        zeroDiscovery = discovery
                                    } else {
                                        showSnackbar("لا توجد رسائل حديثة لهذا المرسل")
                                    }
                                } else {
                                    app.messagePatternRepository.saveDiscoveredBatch(
                                        senderProfileId = senderProfileId,
                                        discovered = discovery.patterns,
                                        status = MessagePatternStatus.UNKNOWN,
                                    )
                                    app.importSessionStore.markTemplatesChanged()
                                    tab = SenderDetailsTab.CANDIDATES
                                    val families = candidateFamiliesCount(discovery.patterns)
                                    showSnackbar("تم اكتشاف $families نمط — تحتاج اعتماد")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.x2))
                    SecondaryButton(
                        if (runningPatternAction == "manual-load") "جارٍ تحميل الرسائل…"
                        else "إنشاء نمط من رسالة",
                        enabled = runningPatternAction == null,
                        onClick = {
                            launchPatternAction(
                                actionName = "manual-load",
                                failureMessage = "تعذر تحميل الرسائل. لم يتم حذف أي بيانات.",
                            ) {
                                val result = app.smsRepository.loadInboxResult(
                                    SmsImportRange.lastDays(LocalDate.now(), 30),
                                )
                                val inbox = when (result) {
                                    is com.baraa.masroof.sms.SmsInboxLoadResult.Success -> result.messages
                                    is com.baraa.masroof.sms.SmsInboxLoadResult.PermissionDenied ->
                                        error("SMS_PERMISSION_DENIED")
                                    is com.baraa.masroof.sms.SmsInboxLoadResult.Failed ->
                                        error("SMS_LOAD_FAILED")
                                }
                                val senderMessages = inbox.filter {
                                    SenderNormalizer.normalize(it.sender) == profile?.normalizedSenderKey
                                }
                                val summaries = senderMessages.mapNotNull { PatternDraftFactory.summarize(it) }
                                pickerSummaries = summaries
                                pickerError = if (summaries.isEmpty()) {
                                    "لا توجد رسائل مالية حديثة لهذا المرسل لإنشاء نمط منها."
                                } else {
                                    null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (candidatePatterns.isEmpty()) {
                        Text(
                            "لا توجد أنماط بانتظار الاعتماد. الأنماط المعتمدة تظهر في تبويب القوالب.",
                            style = FinancialTypography.metadata,
                        )
                    } else {
                        val candidateFamilies = candidatePatterns.groupBy {
                            it.family?.id ?: -it.definition.id
                        }
                        Text(
                            "${candidateFamilies.size} نمطاً مرشحاً — لم تُعتمد بعد ولا تُستخدم في الاستيراد.",
                            style = FinancialTypography.metadata,
                        )
                        candidateFamilies.values
                            .sortedByDescending { rows -> rows.sumOf { it.definition.exampleCount } }
                            .forEach { rows ->
                                val base = rows.maxBy { it.definition.version }
                                val pattern = base.copy(
                                    definition = base.definition.copy(
                                        exampleCount = rows.sumOf { it.definition.exampleCount },
                                    ),
                                )
                                CandidatePatternCard(
                                    pattern = pattern,
                                    senderLabel = profile?.displaySender.orEmpty(),
                                    onReview = { onTemplateClick(pattern.definition.id) },
                                    onIgnore = { ignoreTarget = pattern },
                                    onUseOnce = {
                                        app.importSessionStore.markUseOncePattern(pattern.definition.id)
                                        showSnackbar("استخدام لمرة واحدة — ارجع للاستيراد لإعادة المطابقة دون حفظ قالب دائم.")
                                        if (returnToImport && onReturnToImport != null) {
                                            onReturnToImport()
                                        }
                                    },
                                )
                            }
                    }
                }
                SenderDetailsTab.MESSAGES -> {
                    Text(
                        "$messageCount رسالة مرتبطة بأنماط هذا المرسل",
                        style = FinancialTypography.merchant,
                    )
                    patterns.sortedByDescending { it.definition.exampleCount }.forEach { pattern ->
                        val type = TransactionTypeTaxonomy.parse(pattern.definition.transactionType)
                            ?: TransactionType.OTHER_FINANCIAL
                        Surface(
                            Modifier.fillMaxWidth().clickable {
                                onTemplateClick(pattern.definition.id)
                            },
                            shape = FinancialShapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                Modifier.padding(Spacing.x3),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(TransactionTypeVisuals.icon(type), contentDescription = null)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        pattern.definition.userFriendlyName.ifBlank {
                                            TransactionTypeVisuals.label(type)
                                        },
                                        style = FinancialTypography.merchant,
                                    )
                                    Text(
                                        "${pattern.definition.exampleCount} رسالة · ${
                                            if (
                                                pattern.definition.status == MessagePatternStatus.APPROVED &&
                                                !PatternRuntimeEligibility.isEligible(pattern)
                                            ) {
                                                "يحتاج تحديث"
                                            } else {
                                                TemplateStatusLabels.statusAr(pattern.definition.status)
                                            }
                                        }",
                                        style = FinancialTypography.metadata,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    ignoreTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { ignoreTarget = null },
            title = { Text("تجاهل هذا النوع مستقبلاً؟") },
            text = {
                Text(
                    "اختر نطاق التجاهل. لن تُحذف الرسائل التاريخية.",
                    style = FinancialTypography.metadata,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            app.messagePatternRepository.setStatus(
                                target.definition.id,
                                MessagePatternStatus.IGNORED,
                            )
                            app.importSessionStore.markTemplatesChanged()
                            showSnackbar("تم تجاهل النمط مستقبلاً")
                            ignoreTarget = null
                            reload()
                        }
                    },
                ) { Text("تجاهل النمط مستقبلاً") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { ignoreTarget = null }) { Text("إلغاء") }
                    TextButton(
                        onClick = {
                            showSnackbar("تم تخطي هذا المرشح الآن — ما زال يحتاج اعتماد لاحقاً")
                            ignoreTarget = null
                        },
                    ) { Text("هذه الرسائل فقط") }
                }
            },
        )
    }

    // Financial picker dialog — compact summaries only, never raw SMS.
    if (pickerSummaries.isNotEmpty() || pickerError != null) {
        FinancialMessagePickerDialog(
            summaries = pickerSummaries,
            error = pickerError,
            loadingSmsId = pickerLoading,
            onDismiss = {
                pickerSummaries = emptyList()
                pickerError = null
            },
            onPick = { summary ->
                if (pickerLoading != null) return@FinancialMessagePickerDialog
                pickerLoading = summary.sms.id
                scope.launch {
                    val result = PatternDraftFactory.fromSms(summary.sms, senderProfileId)
                    pickerLoading = null
                    when (result) {
                        is com.baraa.masroof.sms.PatternDraftResult.Ready,
                        is com.baraa.masroof.sms.PatternDraftResult.NeedsTypeSelection -> {
                            val draft = when (result) {
                                is com.baraa.masroof.sms.PatternDraftResult.Ready -> result.draft
                                is com.baraa.masroof.sms.PatternDraftResult.NeedsTypeSelection ->
                                    result.draft
                                else -> error("unreachable")
                            }
                            PatternDraftHolder.set(draft)
                            pickerSummaries = emptyList()
                            pickerError = null
                            onOpenDraftEditor()
                        }
                        is com.baraa.masroof.sms.PatternDraftResult.NonFinancial ->
                            draftFailure = "هذه الرسالة غير مالية (${result.reason}) — اختر رسالة أخرى"
                        is com.baraa.masroof.sms.PatternDraftResult.Failed ->
                            draftFailure = "تعذر تحليل هذه الرسالة (${result.stage}) — اختر رسالة أخرى"
                    }
                }
            },
        )
    }

    // Immediate, blocking error for a message that could not become a draft.
    draftFailure?.let { message ->
        AlertDialog(
            onDismissRequest = { draftFailure = null },
            title = { Text("تعذر تحليل هذه الرسالة") },
            text = { Text(message, style = FinancialTypography.metadata) },
            confirmButton = {
                TextButton(onClick = { draftFailure = null }) { Text("اختيار رسالة أخرى") }
            },
        )
    }

    // Zero-discovery diagnostic: financial SMS existed but no pattern could be built.
    zeroDiscovery?.let { discovery ->
        ZeroDiscoveryDialog(
            result = discovery,
            onDismiss = { zeroDiscovery = null },
            onManualCreate = {
                zeroDiscovery = null
                // Trigger the manual picker the same way the button does.
                launchPatternAction(
                    actionName = "manual-load",
                    failureMessage = "تعذر تحميل الرسائل. لم يتم حذف أي بيانات.",
                ) {
                    val result = app.smsRepository.loadInboxResult(
                        SmsImportRange.lastDays(LocalDate.now(), 30),
                    )
                    val inbox = when (result) {
                        is com.baraa.masroof.sms.SmsInboxLoadResult.Success -> result.messages
                        is com.baraa.masroof.sms.SmsInboxLoadResult.PermissionDenied ->
                            error("SMS_PERMISSION_DENIED")
                        is com.baraa.masroof.sms.SmsInboxLoadResult.Failed ->
                            error("SMS_LOAD_FAILED")
                    }
                    val senderMessages = inbox.filter {
                        SenderNormalizer.normalize(it.sender) == profile?.normalizedSenderKey
                    }
                    val summaries = senderMessages.mapNotNull { PatternDraftFactory.summarize(it) }
                    pickerSummaries = summaries
                    pickerError = if (summaries.isEmpty()) {
                        "لا توجد رسائل مالية حديثة لهذا المرسل لإنشاء نمط منها."
                    } else {
                        null
                    }
                }
            },
        )
    }
}

/** Distinct semantic families discovered, for the success snackbar. */
private fun candidateFamiliesCount(
    patterns: List<com.baraa.masroof.sms.DiscoveredMessagePattern>,
): Int = patterns.count { !it.looksLikeOtpOrMarketing && !it.looksLikeNonFinancial }

@Composable
private fun SummaryValue(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = FinancialTypography.financialTotal)
        Text(label, style = FinancialTypography.metadata)
    }
}

@Composable
private fun ApprovedTemplateCard(
    pattern: MessagePattern,
    onEdit: () -> Unit,
    onDisable: () -> Unit,
) {
    val type = TransactionTypeTaxonomy.parse(pattern.definition.transactionType)
        ?: TransactionType.OTHER_FINANCIAL
    Surface(
        Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            Modifier.padding(Spacing.x3),
            verticalArrangement = Arrangement.spacedBy(Spacing.x2),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(TransactionTypeVisuals.icon(type), contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(
                        pattern.definition.userFriendlyName.ifBlank {
                            TransactionTypeVisuals.label(type)
                        },
                        style = FinancialTypography.merchant,
                    )
                    Text(
                        "${pattern.definition.exampleCount} رسالة مطابقة",
                        style = FinancialTypography.metadata,
                    )
                }
                Text("معتمد", color = MaterialTheme.colorScheme.primary, style = FinancialTypography.metadata)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                SecondaryButton("تعديل", onClick = onEdit)
                SecondaryButton("تعطيل", onClick = onDisable)
            }
        }
    }
}

@Composable
private fun CandidatePatternCard(
    pattern: MessagePattern,
    senderLabel: String,
    onReview: () -> Unit,
    onIgnore: () -> Unit,
    onUseOnce: () -> Unit,
) {
    val def = pattern.definition
    val type = TransactionTypeTaxonomy.parse(def.transactionType) ?: TransactionType.OTHER_FINANCIAL
    val sample = def.templateText.orEmpty().lineSequence().take(3).joinToString("\n").ifBlank { "—" }
    val fields = pattern.fields.take(4).joinToString(" · ") { field ->
        val token = field.placeholderToken.ifBlank { field.canonicalField.name }
        "{${token.trim('{', '}')}}"
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            Modifier.padding(Spacing.x3),
            verticalArrangement = Arrangement.spacedBy(Spacing.x2),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(TransactionTypeVisuals.icon(type), contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(
                        def.userFriendlyName.ifBlank { TransactionTypeVisuals.label(type) },
                        style = FinancialTypography.merchant,
                    )
                    Text(
                        "${def.exampleCount} رسالة مطابقة · يحتاج اعتماد",
                        style = FinancialTypography.metadata,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    if (senderLabel.isNotBlank()) {
                        Text(senderLabel, style = FinancialTypography.metadata)
                    }
                }
            }
            Text(sample, style = FinancialTypography.metadata)
            if (fields.isNotBlank()) {
                Text(fields, style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                PrimaryButton("مراجعة واعتماد", onClick = onReview)
                SecondaryButton("تجاهل", onClick = onIgnore)
            }
            TextButton(onClick = onUseOnce) {
                Text("استخدام مرة واحدة", style = FinancialTypography.metadata)
            }
        }
    }
}

/**
 * Proper financial-message picker: compact summaries only (never raw SMS),
 * with OTP / non-financial / bank-service / maintenance messages excluded
 * upstream by [PatternDraftFactory.summarize].
 */
@Composable
private fun FinancialMessagePickerDialog(
    summaries: List<FinancialMessageSummary>,
    error: String?,
    loadingSmsId: Long?,
    onDismiss: () -> Unit,
    onPick: (FinancialMessageSummary) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء نمط من رسالة") },
        text = {
            Column {
                Text(
                    "اختر رسالة مالية لإنشاء نمط منها. تظهر ملخّص مختصر فقط ولا تُحفظ الرسالة الخام.",
                    style = FinancialTypography.metadata,
                )
                Spacer(Modifier.height(Spacing.x2))
                if (error != null) {
                    Text(error, style = FinancialTypography.metadata)
                } else {
                    summaries.forEach { summary ->
                        Surface(
                            Modifier.fillMaxWidth().clickable { onPick(summary) },
                            shape = FinancialShapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                Modifier.padding(Spacing.x3),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(summary.typeLabel, style = FinancialTypography.merchant)
                                    summary.merchantOrBeneficiary?.let {
                                        Text(it, style = FinancialTypography.metadata)
                                    }
                                    val amountLine = buildString {
                                        summary.amount?.let { append(it) }
                                        summary.currency?.let {
                                            if (isNotEmpty()) append(' ')
                                            append(it)
                                        }
                                    }.ifBlank { null }?.let {
                                        Text(it, style = FinancialTypography.metadata)
                                    }
                                    summary.date?.let { Text(it, style = FinancialTypography.metadata) }
                                    summary.maskedLast4?.let {
                                        Text(it, style = FinancialTypography.metadata)
                                    }
                                    if (summary.isUnclassifiedFinancial) {
                                        Text(
                                            "رسالة مالية غير مصنفة",
                                            style = FinancialTypography.metadata,
                                            color = MaterialTheme.colorScheme.tertiary,
                                        )
                                        summary.fallbackPreview?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = FinancialTypography.metadata)
                                        }
                                    }
                                }
                                if (loadingSmsId == summary.sms.id) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.x1))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

/**
 * Blocking diagnostic shown when discovery produced zero patterns despite
 * financial SMS existing. Surfaces safe counts and the dominant failing
 * stage + exception class (never raw SMS), and offers manual creation.
 */
@Composable
private fun ZeroDiscoveryDialog(
    result: com.baraa.masroof.sms.PatternDiscoveryResult,
    onDismiss: () -> Unit,
    onManualCreate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعذر إنشاء الأنماط من الرسائل المالية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
                val financialCandidates = result.inputMessages -
                    result.skippedOtp - result.skippedNonFinancial - result.skippedBlank
                Text("رسائل مالية محتملة: $financialCandidates", style = FinancialTypography.metadata)
                Text("تمت معالجتها: ${result.processedMessages}", style = FinancialTypography.metadata)
                Text("فشلت (مراحل أساسية): ${result.coreFailedMessages}", style = FinancialTypography.metadata)
                if (result.coreFailedMessages > 0 || result.optionalStageFailureCount > 0) {
                    Spacer(Modifier.height(Spacing.x1))
                    Text("التشخيص:", style = FinancialTypography.merchant)
                    result.failureBreakdown().forEach { b ->
                        val tag = if (b.optional) " (اختياري)" else ""
                        Text(
                            "${b.stage.name} — ${b.count} ${b.exceptionClass}$tag",
                            style = FinancialTypography.metadata,
                        )
                    }
                }
                if (!result.isReconciled()) {
                    Text("⚠ عدم تطابق في العد", style = FinancialTypography.metadata)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onManualCreate) { Text("إنشاء نمط يدوي") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        },
    )
}