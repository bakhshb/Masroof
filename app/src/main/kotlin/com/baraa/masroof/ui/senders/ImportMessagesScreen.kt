package com.baraa.masroof.ui.senders

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.repository.ScanPreview
import com.baraa.masroof.data.repository.SmsImportMode
import com.baraa.masroof.data.repository.SmsImportResult
import com.baraa.masroof.sms.SmsImportRange
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.ui.senders.ImportSessionHints
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.DestructiveButton
import com.baraa.masroof.ui.theme.DestructiveTextButton
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.SectionHeader
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Typed import execution state. Surfaced to the UI in place of the
 *  raw `SmsImportResult` so the user always sees a clear
 *  Idle / Loading / Success / AlreadyImported / Failure signal. */
sealed interface ImportExecutionResult {
    data object Idle : ImportExecutionResult
    data object Loading : ImportExecutionResult
    data class Success(
        val importedCount: Int,
        val linkedCount: Int,
        val postedCount: Int,
        val affectedAccountIds: List<Long>,
        val raw: SmsImportResult,
    ) : ImportExecutionResult
    /** Commit found nothing new because everything was an exact duplicate. */
    data class AlreadyImported(
        val duplicateCount: Int,
        val raw: SmsImportResult,
    ) : ImportExecutionResult
    data class Failure(
        val userMessage: String,
        val technicalMessage: String?,
    ) : ImportExecutionResult
}

/** Button label for the scan → commit CTA. */
internal fun importCommitButtonLabel(preview: ScanPreview): String {
    val readyCount = preview.readyCount
    val reviewCount = preview.needsReviewTransactions
    val importable = readyCount + reviewCount + preview.beforeTrackingStartCount
    return when {
        readyCount > 0 && reviewCount > 0 ->
            "استيراد $readyCount جاهزة و$reviewCount للمراجعة"
        readyCount > 0 -> "استيراد $readyCount عملية"
        reviewCount > 0 -> "استيراد $reviewCount للمراجعة"
        preview.beforeTrackingStartCount > 0 ->
            "استيراد ${preview.beforeTrackingStartCount} كسجل فقط"
        importable == 0 && preview.duplicateTransactions > 0 ->
            "مستوردة سابقًا · ${preview.duplicateTransactions} مكررة"
        else -> "لا توجد عمليات جاهزة"
    }
}

/** Maps orchestrator commit output to a typed UI result. */
internal fun mapImportCommitResult(result: SmsImportResult): ImportExecutionResult = when {
    result.importedTransactions > 0 -> ImportExecutionResult.Success(
        importedCount = result.importedTransactions,
        linkedCount = result.linkedTransactions,
        postedCount = result.postedTransactions,
        affectedAccountIds = result.updatedAccountIds,
        raw = result,
    )
    result.duplicateTransactions > 0 -> ImportExecutionResult.AlreadyImported(
        duplicateCount = result.duplicateTransactions,
        raw = result,
    )
    else -> ImportExecutionResult.Failure(
        userMessage = "لم يتم استيراد أي عملية. تحقق من البيانات وحاول مجدداً.",
        technicalMessage = "commit produced 0 imported transactions",
    )
}

/**
 * The SMS import flow follows a strict scan → review → commit pipeline.
 *
 *   STEP 1 (scan):  user picks a quick option or a custom calendar range
 *                   and presses "فحص الرسائل". The orchestrator parses
 *                   every SMS, classifies, but writes nothing to Room.
 *                   The result is a [ScanPreview] showing the breakdown.
 *
 *   STEP 2 (commit): user presses "استيراد N عملية". The orchestrator
 *                   opens ONE Room `withTransaction` block, inserts
 *                   every transaction, links it, posts the journal +
 *                   its postings, recomputes affected account
 *                   summaries, and returns [ImportExecutionResult].
 *
 * The whole screen is a single scrollable Column so the import button
 * is always reachable. The shared NavController is the one passed by
 * the parent NavHost — we never create a second one here.
 */
@Composable
fun ImportMessagesScreen(
    onClose: () -> Unit,
    onHome: () -> Unit,
    onTransactions: () -> Unit,
    onAccounts: () -> Unit,
    onMore: () -> Unit,
    onShowImportedTransactions: () -> Unit = onTransactions,
    onNavigateToAccounts: () -> Unit = onAccounts,
    onReview: () -> Unit,
    onBankMessages: () -> Unit = onMore,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val registeredSenderCount by produceState(initialValue = 0) {
        value = app.senderProfileRepository.activeOwnedSenderKeys().size
            .coerceAtLeast(app.accountIdentifierRepository.activeOwnedSenderAliases().size)
    }

    val setup by app.financialSetupRepository.observe().collectAsStateWithLifecycle(initialValue = null)
    val accounts by app.financialAccountRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val openingBalanceDate: LocalDate? = remember(setup, accounts) {
        val fromSetup = setup?.trackingStartDate?.takeIf { it > 0L }?.let {
            java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        }
        val fromAccounts = accounts
            .filter { it.systemAccountKey == null && it.openingBalanceDate > 0L }
            .map {
                java.time.Instant.ofEpochMilli(it.openingBalanceDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            }
            .minOrNull()
        listOfNotNull(fromSetup, fromAccounts).minOrNull()
    }

    val initialRange = remember(openingBalanceDate) {
        val hinted = ImportSessionHints.consumePreferredFromDate()
        val start = listOfNotNull(hinted, openingBalanceDate).minOrNull()
        SmsImportRange.preferredDefault(today, start)
    }
    var importMode by remember { mutableStateOf(SmsImportMode.REGISTERED_ACCOUNTS_ONLY) }
    var quickId by remember { mutableStateOf(initialRange.quickId) }
    var customFrom by remember { mutableStateOf(initialRange.start.toLocalDate()) }
    var customTo by remember { mutableStateOf(today) }
    var openingRangeAnchor by remember { mutableStateOf(initialRange.start.toLocalDate()) }
    var permissionGranted by remember { mutableStateOf(snapshotReadSms(context)) }
    var permissionPermanentlyDenied by remember {
        mutableStateOf(!permissionGranted && (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == false)
    }

    var phase by remember { mutableStateOf(ImportPhase.Idle) }
    var scanPreview by remember { mutableStateOf<ScanPreview?>(null) }
    var importResult by remember { mutableStateOf<ImportExecutionResult>(ImportExecutionResult.Idle) }
    var lastLoadedMessages by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
    var showTrackEditDialog by remember { mutableStateOf(false) }
    var showLogOnlyConfirmation by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = snapshotReadSms(context)
                permissionPermanentlyDenied = !permissionGranted && (activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) {
            permissionPermanentlyDenied = (activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS)) == false
        }
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            MasroofTopAppBar(
                title = "استيراد رسائل البنك",
                onBack = onClose,
            )
        },
    ) { padding ->
        val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.x4, vertical = Spacing.x3),
                verticalArrangement = Arrangement.spacedBy(Spacing.x3),
            ) content@{
                PermissionStatePanel(
                    granted = permissionGranted,
                    permanentlyDenied = permissionPermanentlyDenied,
                    onRequest = { launcher.launch(Manifest.permission.READ_SMS) },
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    },
                )

                if (!permissionGranted) {
                    Text("تعذر فحص الرسائل لأن إذن قراءة الرسائل غير ممنوح.", color = MaterialTheme.colorScheme.error, style = FinancialTypography.merchant)
                    return@content
                }

                OpeningBalanceDateCard(
                    openingBalanceDate = openingBalanceDate,
                    onEdit = { showTrackEditDialog = true },
                )

                val showingImportResult = importResult is ImportExecutionResult.Success ||
                    importResult is ImportExecutionResult.AlreadyImported ||
                    importResult is ImportExecutionResult.Failure

                if (showingImportResult) {
                    when (val r = importResult) {
                        is ImportExecutionResult.Success -> {
                            CommitResultCard(
                                result = r.raw,
                                onShowImportedTransactions = onShowImportedTransactions,
                                onNavigateToAccounts = onNavigateToAccounts,
                                onHome = onHome,
                                onReview = onReview,
                                onImportAgain = {
                                    importResult = ImportExecutionResult.Idle
                                    scanPreview = null
                                },
                            )
                        }
                        is ImportExecutionResult.AlreadyImported -> {
                            AlreadyImportedCard(
                                duplicateCount = r.duplicateCount,
                                onNavigateToAccounts = onNavigateToAccounts,
                                onHome = onHome,
                                onImportAgain = {
                                    importResult = ImportExecutionResult.Idle
                                    scanPreview = null
                                },
                            )
                        }
                        is ImportExecutionResult.Failure -> {
                            FailureCard(r)
                            PrimaryButton(
                                label = "إعادة المحاولة",
                                onClick = {
                                    importResult = ImportExecutionResult.Idle
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SecondaryButton(label = "الرئيسية", onClick = onHome, modifier = Modifier.fillMaxWidth())
                        }
                        else -> Unit
                    }
                    return@content
                }

                var showClearImportConfirm by remember { mutableStateOf(false) }
                var clearImportSummary by remember { mutableStateOf<String?>(null) }
                DestructiveButton(
                    label = "مسح العمليات المستوردة",
                    onClick = { showClearImportConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                clearImportSummary?.let {
                    Text(it, style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showClearImportConfirm) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showClearImportConfirm = false },
                        title = { Text("مسح الاستيراد؟") },
                        text = {
                            Text(
                                "يحذف العمليات والقيود فقط. الحسابات وآخر 4 والرصيد الافتتاحي تبقى. ثم أعد الفحص والاستيراد.",
                            )
                        },
                        confirmButton = {
                            DestructiveTextButton(
                                label = "مسح",
                                onClick = {
                                    showClearImportConfirm = false
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            app.importResetService.clearImportedLedger()
                                        }
                                        scanPreview = null
                                        importResult = ImportExecutionResult.Idle
                                        phase = ImportPhase.Idle
                                        clearImportSummary =
                                            "تم: ${result.deletedTransactions} عملية · ${result.deletedJournals} قيد — أعد الفحص الآن"
                                    }
                                },
                            )
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showClearImportConfirm = false }) {
                                Text("إلغاء")
                            }
                        },
                    )
                }

                ImportModeSection(
                    selected = importMode,
                    registeredSenderCount = registeredSenderCount,
                    onSelected = {
                        importMode = it
                        scanPreview = null
                    },
                )

                ImportRangeSection(
                    quickId = quickId,
                    onQuickIdChange = { quickId = it },
                    customFrom = customFrom,
                    customTo = customTo,
                    onCustomFromChange = { customFrom = it },
                    onCustomToChange = { customTo = it },
                    openingBalanceDate = openingRangeAnchor.takeIf {
                        it.isBefore(today.withDayOfMonth(1)) || quickId == SmsImportRange.QUICK_OPENING_BALANCE
                    } ?: openingBalanceDate,
                )

                when (phase) {
                    ImportPhase.Scanning -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("جارٍ فحص الرسائل", style = FinancialTypography.merchant)
                        SecondaryButton(label = "إلغاء", onClick = { phase = ImportPhase.Idle })
                    }
                    ImportPhase.Committing -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("جارٍ استيراد العمليات", style = FinancialTypography.merchant)
                    }
                    ImportPhase.Idle -> {
                        val range = resolveRange(quickId, today, customFrom, customTo, openingRangeAnchor)
                        PrimaryButton(
                            label = "فحص الرسائل",
                            enabled = range != null && permissionGranted,
                            onClick = {
                                val resolvedRange = range ?: return@PrimaryButton
                                scanPreview = null
                                importResult = ImportExecutionResult.Idle
                                phase = ImportPhase.Scanning
                                scope.launch {
                                    runCatching {
                                        val messages = app.smsRepository.loadInbox(resolvedRange)
                                        lastLoadedMessages = messages
                                        scanPreview = app.importOrchestrator.scan(messages, openingBalanceDate, importMode)
                                    }.onFailure {
                                        scanPreview = ScanPreview()
                                        android.util.Log.w("SmsImport", "scan failed", it)
                                    }
                                    phase = ImportPhase.Idle
                                }
                            },
                        )
                    }
                }

                scanPreview?.let { preview ->
                    if (!preview.hasRegisteredSenders && preview.mode == SmsImportMode.REGISTERED_ACCOUNTS_ONLY) {
                        NoRegisteredSenderCard(
                            onAccounts = onNavigateToAccounts,
                            onDiscovery = { importMode = SmsImportMode.DISCOVER_NEW_SENDERS },
                            onTeach = onBankMessages,
                        )
                        return@let
                    }
                    if (preview.mode == SmsImportMode.DISCOVER_NEW_SENDERS) {
                        DiscoveryResultsCard(preview, onAccounts = onNavigateToAccounts)
                        return@let
                    }
                    ScanResultsCard(preview, onAccounts = onNavigateToAccounts)
                    val readyCount = preview.readyCount
                    val reviewCount = preview.needsReviewTransactions
                    val importable = readyCount + reviewCount + preview.beforeTrackingStartCount
                    val beforeTracker = preview.beforeTrackingStartCount > 0
                    if (beforeTracker) {
                        TrackingStartWarningCard(
                            onChangeTrackingStart = { showTrackEditDialog = true },
                            onImportAsLogOnly = { showLogOnlyConfirmation = true },
                        )
                    }
                    if (importable == 0 && preview.duplicateTransactions > 0) {
                        Text(
                            "للتحديث استخدم «إعادة ربط وترحيل المطابق» من شاشة الحسابات — إعادة الاستيراد لا تعيد معالجة الرسائل المستوردة سابقًا.",
                            style = FinancialTypography.metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                        PrimaryButton(
                            label = importCommitButtonLabel(preview),
                            enabled = importable > 0 && phase == ImportPhase.Idle && importResult !is ImportExecutionResult.Loading,
                            onClick = {
                                if (importable <= 0) return@PrimaryButton
                                val snapshot = preview
                                if (snapshot.recognizedTransactions == 0) return@PrimaryButton
                                // Real import action — call the canonical
                                // SmsImportOrchestrator.commit path. Never
                                // re-scan or replace with a fake result.
                                android.util.Log.i(
                                    "SmsImport",
                                    "SMS_IMPORT_BUTTON_CLICKED readyCount=$readyCount phase=$phase importResult=$importResult",
                                )
                                importResult = ImportExecutionResult.Loading
                                phase = ImportPhase.Committing
                                scope.launch {
                                    val outcome = runCatching {
                                        app.importOrchestrator.commit(
                                            scanPreview = snapshot,
                                            trackingStartDate = openingBalanceDate,
                                            importedSms = lastLoadedMessages,
                                        )
                                    }
                                    importResult = outcome.fold(
                                        onSuccess = { result ->
                                            val mapped = mapImportCommitResult(result)
                                            if (mapped is ImportExecutionResult.Success) {
                                                android.util.Log.i(
                                                    "SmsImport",
                                                    "SMS_IMPORT_COMMIT_SUCCESS imported=${result.importedTransactions} linked=${result.linkedTransactions} posted=${result.postedTransactions} affected=${result.updatedAccountIds.size} accounts=${result.affectedAccounts.size}",
                                                )
                                            }
                                            mapped
                                        },
                                        onFailure = { t ->
                                            android.util.Log.e("SmsImport", "SMS_IMPORT_COMMIT_FAILED", t)
                                            ImportExecutionResult.Failure(
                                                userMessage = "تعذر استيراد العمليات. حاول مجدداً.",
                                                technicalMessage = t.message ?: t.javaClass.simpleName,
                                            )
                                        },
                                    )
                                    phase = ImportPhase.Idle
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            label = if (reviewCount > 0) "مراجعة $reviewCount عملية" else "إلغاء نتائج الفحص",
                            enabled = reviewCount > 0 || scanPreview != null,
                            onClick = if (reviewCount > 0) {
                                {
                                    // Review candidates are persisted first. A scan preview is
                                    // intentionally read-only, so routing before this commit
                                    // used to open an empty queue.
                                    val snapshot = preview
                                    phase = ImportPhase.Committing
                                    importResult = ImportExecutionResult.Loading
                                    scope.launch {
                                        val outcome = runCatching {
                                            app.importOrchestrator.commit(snapshot, openingBalanceDate, lastLoadedMessages)
                                        }
                                        importResult = outcome.fold(
                                            onSuccess = { mapImportCommitResult(it) },
                                            onFailure = { error -> ImportExecutionResult.Failure("تعذر تجهيز قائمة المراجعة.", error.message) },
                                        )
                                        phase = ImportPhase.Idle
                                        if (outcome.isSuccess) onReview()
                                    }
                                }
                            } else {
                                {
                                    scanPreview = null
                                    importResult = ImportExecutionResult.Idle
                                    phase = ImportPhase.Idle
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            // Bottom inset so the final buttons are never under the
            // bottom navigation bar.
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.padding(bottom = navBarBottomInset + 24.dp),
            )
        }
    }

    if (showTrackEditDialog) {
        OpeningBalanceEditorDialog(
            initial = openingBalanceDate ?: today,
            onDismiss = { showTrackEditDialog = false },
            onSave = { newDate ->
                scope.launch {
                    val current = setup ?: app.financialSetupRepository.load()
                    val updated = current.copy(trackingStartDate = newDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                    app.financialSetupRepository.save(updated)
                    showTrackEditDialog = false
                }
            },
        )
    }

    if (showLogOnlyConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLogOnlyConfirmation = false },
            title = { Text("استيراد كسجل فقط") },
            text = { Text("سيتم حفظ العمليات السابقة لتاريخ الرصيد الافتتاحي كسجل فقط ولن تُحسب في الرصيد.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showLogOnlyConfirmation = false
                }) { Text("موافق") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showLogOnlyConfirmation = false }) { Text("إلغاء") } },
        )
    }
}

private enum class ImportPhase { Idle, Scanning, Committing }

@Composable
private fun OpeningBalanceDateCard(openingBalanceDate: LocalDate?, onEdit: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Text("تاريخ الرصيد الافتتاحي", style = FinancialTypography.supportingLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("ar"))
            Text("الرصيد الافتتاحي في ${openingBalanceDate?.format(fmt) ?: "—"}", style = FinancialTypography.merchant)
            Text(
                "هو التاريخ الذي يمثّل الرصيد الذي أدخلته للحساب. تُحتسب العمليات اللاحقة له للوصول إلى رصيد اليوم.",
                style = FinancialTypography.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(label = "تعديل", onClick = onEdit)
        }
    }
}

@Composable
private fun ImportRangeSection(
    quickId: String,
    onQuickIdChange: (String) -> Unit,
    customFrom: LocalDate,
    customTo: LocalDate,
    onCustomFromChange: (LocalDate) -> Unit,
    onCustomToChange: (LocalDate) -> Unit,
    openingBalanceDate: LocalDate? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text("فترة الرسائل المطلوب فحصها", style = FinancialTypography.merchant)
        Text(
            "تحدد الرسائل التي سيبحث عنها التطبيق فقط، ولا تغيّر تاريخ الرصيد الافتتاحي.",
            style = FinancialTypography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val today = LocalDate.now()
        val range = resolveRange(quickId, today, customFrom, customTo, openingBalanceDate)
        val (from, to) = rangeDisplay(quickId, customFrom, customTo, today, openingBalanceDate)
        com.baraa.masroof.ui.theme.ImportSummaryCard(
            rangeLabel = humanDateRange(from, to),
            allowedInstitutionCount = 0,
        )
        val dayFmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("ar"))
        Text(
            "يشمل يوم ${from.format(dayFmt)} من الساعة 00:00 حتى نهاية يوم ${to.format(dayFmt)}.",
            style = FinancialTypography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "الرصيد الافتتاحي منفصل: الحركات قبل تاريخه تُحفظ كسجل ولا تغيّر الرصيد. لاستبعاد يوم الرصيد الافتتاحي من الحركات، اضبط المتابعة على اليوم التالي.",
            style = FinancialTypography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val chips = buildList {
            if (openingBalanceDate != null && !openingBalanceDate.isAfter(today)) {
                add(
                    com.baraa.masroof.ui.theme.FilterChipModel(
                        SmsImportRange.QUICK_OPENING_BALANCE,
                        "من تاريخ الرصيد الافتتاحي",
                        selected = quickId == SmsImportRange.QUICK_OPENING_BALANCE,
                    ),
                )
            }
            add(com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_MONTH_START, "من بداية هذا الشهر", selected = quickId == SmsImportRange.QUICK_MONTH_START))
            add(com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_LAST_SALARY, "منذ آخر راتب", selected = quickId == SmsImportRange.QUICK_LAST_SALARY))
            add(com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_LAST_SEVEN, "آخر 7 أيام", selected = quickId == SmsImportRange.QUICK_LAST_SEVEN))
            add(com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_LAST_THIRTY, "آخر 30 يومًا", selected = quickId == SmsImportRange.QUICK_LAST_THIRTY))
            add(com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_CUSTOM, "تحديد فترة", selected = quickId == SmsImportRange.QUICK_CUSTOM))
        }
        com.baraa.masroof.ui.theme.FilterChipRow(
            chips = chips,
            onChipClick = onQuickIdChange,
        )
        if (quickId == SmsImportRange.QUICK_CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2), modifier = Modifier.fillMaxWidth()) {
                CalendarDateField(label = "من تاريخ", selected = customFrom, onSelected = onCustomFromChange, isStart = true, rangeEnd = customTo, modifier = Modifier.weight(1f))
                CalendarDateField(label = "إلى تاريخ", selected = customTo, onSelected = onCustomToChange, isStart = false, rangeStart = customFrom, modifier = Modifier.weight(1f))
            }
            if (customTo.isBefore(customFrom)) {
                Text("تاريخ النهاية يجب أن يكون بعد البداية.", color = MaterialTheme.colorScheme.error)
            }
        }
        if (range == null) Text("حدد فترة صحيحة أولاً", color = MaterialTheme.colorScheme.error)
    }
}

private fun rangeDisplay(
    quickId: String,
    customFrom: LocalDate,
    customTo: LocalDate,
    today: LocalDate,
    openingBalanceDate: LocalDate? = null,
): Pair<LocalDate, LocalDate> = when (quickId) {
    SmsImportRange.QUICK_OPENING_BALANCE -> {
        val from = openingBalanceDate ?: today.withDayOfMonth(1)
        (if (from.isAfter(today)) today else from) to today
    }
    SmsImportRange.QUICK_MONTH_START -> today.withDayOfMonth(1) to today
    SmsImportRange.QUICK_LAST_SALARY -> {
        val r = SmsImportRange.sinceLastSalary(today)
        r.start.toLocalDate() to r.displayEndDate
    }
    SmsImportRange.QUICK_LAST_SEVEN -> today.minusDays(6) to today
    SmsImportRange.QUICK_LAST_THIRTY -> today.minusDays(29) to today
    SmsImportRange.QUICK_CUSTOM -> customFrom to customTo
    else -> today.withDayOfMonth(1) to today
}

@Composable
private fun ScanResultsCard(preview: ScanPreview, onAccounts: () -> Unit) {
    SectionHeader("نتائج الفحص")
    val ready = preview.readyCount
    Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Text("تم فحص الرسائل ضمن الفترة — بهذا الترتيب:", style = FinancialTypography.merchant)
            Text(
                "1) رمز تحقق/OTP يُستبعد فقط إذا وُجد رمز رقمي مع عبارة التحقق (تنويه «لا تشارك الرمز» وحده لا يكفي)\n" +
                    "2) مرسل غير مسجّل على حساباتك → يُتجاهل\n" +
                    "3) مرسل مسجّل بدون مبلغ مستخرج من تسمية معروفة → لا يُستورد\n" +
                    "4) الباقي → عملية مرشّحة للاستيراد/المراجعة",
                style = FinancialTypography.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BulletRow("الرسائل ضمن الفترة", "${preview.scannedMessages}")
            BulletRow("رموز تحقق / تأكيد هوية (مستبعدة)", "${preview.otpOrAuthMessages}")
            BulletRow("رسائل من مرسلين غير مسجلين", "${preview.unregisteredSenderMessages}")
            BulletRow("رسائل المرسلين المسجلين (بعد الاستبعاد)", "${(preview.scannedMessages - preview.unregisteredSenderMessages - preview.otpOrAuthMessages).coerceAtLeast(0)}")
            BulletRow("العمليات المالية المكتشفة", "${preview.recognizedTransactions}")
            BulletRow("تعذّر استخراج المبلغ (لم تُستورد)", "${preview.unparsedMessages}")
            BulletRow("جاهزة للاستيراد", "${ready}")
            BulletRow("تحتاج مراجعة", "${preview.needsReviewTransactions}")
            BulletRow("غير مالية أو غير معروفة", "${preview.nonFinancialMessages}")
            BulletRow("مكررة", "${preview.duplicateTransactions}")
            BulletRow("أقدم من تاريخ الرصيد الافتتاحي", "${preview.beforeTrackingStartCount}")
            if (preview.otpOrAuthMessages > 0) {
                Text(
                    "رموز التحقق لا تُحتسب عملياتًا حتى لو ذكرت المبلغ — إيصال الشراء المنفصل هو ما يُستورد.",
                    style = FinancialTypography.metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (preview.unparsedMessages > 0) {
                Text(
                    "بعض رسائل البنوك لم يُستخرج منها مبلغ — لن تدخل الرصيد. أعد الاستيراد بعد تحديث المحلل أو راجع المرسل.",
                    style = FinancialTypography.metadata,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (preview.unregisteredSenderMessages > 0) {
                Text(
                    "رسائل من مرسلين غير مسجلين على حساباتك تُتجاهل. أضف معرّف المرسل على الحساب أو استخدم اكتشاف المرسلين.",
                    style = FinancialTypography.metadata,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (preview.scannedMessages >= com.baraa.masroof.sms.SmsRepository.DEFAULT_LIMIT) {
                Text(
                    "وصلت لحد قراءة الرسائل (${com.baraa.masroof.sms.SmsRepository.DEFAULT_LIMIT}). قصّر الفترة أو استورد على دفعات حتى لا يُفقد شيء.",
                    style = FinancialTypography.metadata,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    Text(
        "الفحص لا يُعدّل الرصيد. اضغط «استيراد» لتسجيل العمليات المكتشفة فعلاً.",
        style = FinancialTypography.metadata,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (preview.skippedSenders.isNotEmpty() || preview.beforeTrackingStartCount > 0) {
        SkippedMessagesCard(preview, onAccounts = onAccounts)
    }
    if (preview.institutionGroups.isNotEmpty()) {
        SectionHeader("البنوك المعروفة")
        preview.institutionGroups.forEach { group ->
            InstitutionRow(group)
        }
    }
}

@Composable
private fun SkippedMessagesCard(preview: ScanPreview, onAccounts: () -> Unit) {
    val context = LocalContext.current
    SectionHeader("رسائل لم تُستورد")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text(
                "أعلى ${ScanPreview.MAX_SKIPPED_GROUPS} مجموعات حسب العدد. النصوص معروضة بعد إزالة البيانات الحساسة فقط.",
                style = FinancialTypography.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            preview.skippedSenders.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
                    Text("${group.senderDisplay} • ${group.messageCount}", style = FinancialTypography.merchant)
                    Text(group.reasonAr, style = FinancialTypography.metadata)
                    if (!group.redactedSample.isNullOrBlank() &&
                        (group.reason == ScanPreview.SkipReason.NO_AMOUNT ||
                            group.reason == ScanPreview.SkipReason.UNREGISTERED_SENDER ||
                            group.reason == ScanPreview.SkipReason.PATTERN_NOT_SELECTED)
                    ) {
                        Text(
                            group.redactedSample,
                            style = FinancialTypography.metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when (group.reason) {
                        ScanPreview.SkipReason.UNREGISTERED_SENDER -> {
                            SecondaryButton(
                                "ربط «${group.senderDisplay}» بحساب",
                                onClick = {
                                    ImportSessionHints.setPreferredSender(group.senderDisplay)
                                    onAccounts()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        ScanPreview.SkipReason.NO_AMOUNT -> {
                            if (!group.redactedSample.isNullOrBlank()) {
                                SecondaryButton(
                                    "نسخ نموذج للاختبار",
                                    onClick = {
                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                            as android.content.ClipboardManager
                                        cm.setPrimaryClip(
                                            android.content.ClipData.newPlainText(
                                                "masroof-fixture",
                                                group.redactedSample,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        ScanPreview.SkipReason.PATTERN_NOT_SELECTED,
                        ScanPreview.SkipReason.UNKNOWN_PATTERN,
                        ScanPreview.SkipReason.NON_FINANCIAL,
                        ScanPreview.SkipReason.OTP_OR_AUTH -> Unit
                    }
                }
            }
            if (preview.beforeTrackingStartCount > 0) {
                Text(
                    "أقدم من تاريخ الرصيد الافتتاحي: ${preview.beforeTrackingStartCount} (تُحفظ كسجل فقط ولا تغيّر الرصيد)",
                    style = FinancialTypography.metadata,
                )
            }
        }
    }
}

@Composable
private fun InstitutionRow(group: ScanPreview.InstitutionGroup) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Text(group.institutionName, style = FinancialTypography.merchant)
            Text("تم التعرف على ${group.totalRecognized} عملية", style = FinancialTypography.metadata)
            Text("جاهزة للاستيراد: ${group.readyToImport}", style = FinancialTypography.metadata)
            if (group.needsReview > 0) Text("تحتاج مراجعة: ${group.needsReview}", style = FinancialTypography.metadata)
            if (group.unparsed > 0) Text("تعذر تحليلها: ${group.unparsed}", style = FinancialTypography.metadata)
        }
    }
}

@Composable
private fun TrackingStartWarningCard(onChangeTrackingStart: () -> Unit, onImportAsLogOnly: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text("تم العثور على عمليات أقدم من تاريخ الرصيد الافتتاحي. لن تدخل في حساب الرصيد إلا بعد تعديل التاريخ.", style = FinancialTypography.merchant, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                PrimaryButton(label = "تعديل تاريخ الرصيد الافتتاحي", onClick = onChangeTrackingStart)
                SecondaryButton(label = "استيرادها كسجل فقط", onClick = onImportAsLogOnly)
            }
        }
    }
}

@Composable
private fun CommitResultCard(
    result: SmsImportResult,
    onShowImportedTransactions: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onHome: () -> Unit,
    onReview: () -> Unit,
    onImportAgain: () -> Unit,
) {
    SectionHeader("اكتمل الاستيراد")
    Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            BulletRow("تم استيراد", "${result.importedTransactions} عملية")
            BulletRow("تم ربط", "${result.linkedTransactions} عملية")
            BulletRow("تم إنشاء قيود مالية لـ", "${result.postedTransactions} عملية")
            BulletRow("تم تحديث", "${result.updatedAccountIds.size} حسابات")
            BulletRow("تحتاج مراجعة", "${result.needsReviewTransactions} عملية")
        }
    }
    if (result.postedTransactions == 0 && result.needsReviewTransactions > 0) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = FinancialShapes.medium,
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                Text(
                    "لم تُحدَّث الأرصدة بعد",
                    style = FinancialTypography.merchant,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "كل العمليات المستوردة تحتاج مراجعة وربطاً بالحساب. أكمل المراجعة حتى تظهر المخططات ويتغيّر الرصيد.",
                    style = FinancialTypography.metadata,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
    if (result.importedTransactions == 0 && result.scannedMessages > 0 && result.unregisteredSenderMessages == result.scannedMessages) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = FinancialShapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                Text("لم تُطابق أي رسالة مرسلين مسجلين", style = FinancialTypography.merchant)
                Text("تأكد أن اسم المرسل في الربط يطابق مرسل صندوق الوارد، أو أعد ربط الحساب برسالة حديثة.")
                SecondaryButton(label = "الحسابات والربط", onClick = onNavigateToAccounts, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (result.affectedAccounts.isNotEmpty()) {
        SectionHeader("الحسابات المحدّثة")
        result.affectedAccounts.forEach { AffectedAccountCard(it) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2), modifier = Modifier.fillMaxWidth()) {
        if (result.needsReviewTransactions > 0) {
            PrimaryButton(label = "افتح قائمة المراجعة", onClick = onReview, modifier = Modifier.fillMaxWidth())
        } else {
            PrimaryButton(label = "عرض العمليات", onClick = onShowImportedTransactions, modifier = Modifier.fillMaxWidth())
        }
        SecondaryButton(label = "الرئيسية", onClick = onHome, modifier = Modifier.fillMaxWidth())
        SecondaryButton(label = "استيراد فترة أخرى", onClick = onImportAgain, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AlreadyImportedCard(
    duplicateCount: Int,
    onNavigateToAccounts: () -> Unit,
    onHome: () -> Unit,
    onImportAgain: () -> Unit,
) {
    SectionHeader("لا عمليات جديدة")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text(
                "لا عمليات جديدة — الكل مستورد سابقًا ($duplicateCount مكررة).",
                style = FinancialTypography.merchant,
            )
            Text(
                "إعادة الاستيراد لا تعيد معالجة الرسائل القديمة. لتحديث الربط والترحيل استخدم «إعادة ربط وترحيل المطابق» من الحسابات.",
                style = FinancialTypography.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(label = "الحسابات والربط", onClick = onNavigateToAccounts, modifier = Modifier.fillMaxWidth())
            SecondaryButton(label = "الرئيسية", onClick = onHome, modifier = Modifier.fillMaxWidth())
            SecondaryButton(label = "استيراد فترة أخرى", onClick = onImportAgain, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FailureCard(failure: ImportExecutionResult.Failure) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text("تعذر استيراد العمليات", style = FinancialTypography.merchant, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(failure.userMessage, style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onErrorContainer)
            failure.technicalMessage?.let { Text("السبب: $it", style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
private fun AffectedAccountCard(a: SmsImportResult.AffectedAccountSummary) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Text(a.accountName, style = FinancialTypography.merchant)
            val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("ar"))
            BulletRow("الرصيد الافتتاحي في ${a.openingBalanceDate?.format(fmt) ?: "—"}", "${a.openingBalance.toPlainString()} ر.س")
            val isLiability = a.accountNature == com.baraa.masroof.transaction.AccountNature.LIABILITY
            BulletRow(
                if (isLiability) "زيادة المستحق (مشتريات)" else "المبالغ الواردة للحساب",
                "${a.moneyIn.toPlainString()} ر.س",
            )
            BulletRow(
                if (isLiability) "تخفيض المستحق (سداد)" else "المبالغ الصادرة من الحساب",
                "${a.moneyOut.toPlainString()} ر.س",
            )
            val delta = a.moneyIn.subtract(a.moneyOut)
            val deltaLabel = if (isLiability) "تغيّر المستحق" else "صافي الحركة"
            BulletRow(deltaLabel, "${delta.toPlainString()} ر.س")
            BulletRow(
                if (isLiability) "المستحق المحسوب اليوم" else "الرصيد المحسوب اليوم",
                "${a.calculatedBalance.toPlainString()} ر.س",
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun OpeningBalanceEditorDialog(initial: LocalDate, onDismiss: () -> Unit, onSave: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    var picked by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل تاريخ الرصيد الافتتاحي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                Text("اختر التاريخ المرتبط بالرصيد الافتتاحي من التقويم.", style = FinancialTypography.metadata)
                com.baraa.masroof.ui.theme.CalendarDateField(
                    label = "تاريخ الرصيد الافتتاحي",
                    selected = picked,
                    onSelected = { picked = it },
                    maxDate = today,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (!picked.isAfter(today)) onSave(picked) },
            ) { Text("حفظ") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun PermissionStatePanel(granted: Boolean, permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    if (granted) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = FinancialShapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                "إذن قراءة الرسائل مفعّل ✓",
                modifier = Modifier.padding(Spacing.x4),
                style = FinancialTypography.merchant,
            )
        }
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text("مطلوب إذن قراءة الرسائل", style = FinancialTypography.merchant, color = MaterialTheme.colorScheme.onErrorContainer)
            Text("يحتاج التطبيق إلى إذن قراءة الرسائل للتعرف على العمليات البنكية.", style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onErrorContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                PrimaryButton(label = "منح الصلاحية", onClick = onRequest)
                if (permanentlyDenied) SecondaryButton(label = "فتح إعدادات التطبيق", onClick = onOpenSettings)
            }
        }
    }
}

@Composable
private fun BulletRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = FinancialTypography.metadata)
        Text(value, style = FinancialTypography.merchant)
    }
}

private fun snapshotReadSms(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

private fun resolveRange(
    quickId: String,
    today: LocalDate,
    customFrom: LocalDate,
    customTo: LocalDate,
    openingBalanceDate: LocalDate? = null,
): SmsImportRange? = when (quickId) {
    SmsImportRange.QUICK_OPENING_BALANCE -> openingBalanceDate?.let { SmsImportRange.fromOpeningBalance(today, it) }
    SmsImportRange.QUICK_MONTH_START -> SmsImportRange.default(today)
    SmsImportRange.QUICK_LAST_SALARY -> SmsImportRange.sinceLastSalary(today)
    SmsImportRange.QUICK_LAST_SEVEN -> SmsImportRange.lastDays(today, 7)
    SmsImportRange.QUICK_LAST_THIRTY -> SmsImportRange.lastDays(today, 30)
    SmsImportRange.QUICK_CUSTOM -> if (customTo.isBefore(customFrom)) null else SmsImportRange.custom(customFrom, customTo, today)
    else -> null
}

private fun humanDateRange(from: LocalDate, to: LocalDate): String {
    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("ar"))
    return "من ${from.format(fmt)} إلى ${to.format(fmt)}"
}

@Composable
private fun ImportModeSection(selected: SmsImportMode, registeredSenderCount: Int, onSelected: (SmsImportMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text("ما الرسائل التي تريد فحصها؟", style = FinancialTypography.merchant)
        FilterChip(selected = selected == SmsImportMode.REGISTERED_ACCOUNTS_ONLY, onClick = { onSelected(SmsImportMode.REGISTERED_ACCOUNTS_ONLY) }, label = { Text("حساباتي المسجلة") })
        Text("فحص رسائل المرسلين المرتبطين بالحسابات التي أضفتها فقط. سيتم فحص رسائل $registeredSenderCount مرسلين مسجلين.", style = FinancialTypography.metadata)
        FilterChip(selected = selected == SmsImportMode.DISCOVER_NEW_SENDERS, onClick = { onSelected(SmsImportMode.DISCOVER_NEW_SENDERS) }, label = { Text("البحث عن مرسلين جدد") })
        Text("العثور على مرسلين ماليين لم تضفهم بعد، دون استيراد عمليات.", style = FinancialTypography.metadata)
        Text(
            "لتعليم المرسلين وأنماط الرسائل استخدم «رسائل البنوك» من المزيد.",
            style = FinancialTypography.metadata,
        )
    }
}

@Composable
private fun NoRegisteredSenderCard(onAccounts: () -> Unit, onDiscovery: () -> Unit, onTeach: () -> Unit = onDiscovery) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text("لم يتم إضافة مرسلي رسائل للحسابات بعد", style = FinancialTypography.merchant)
            Text("علّم مرسلاً من «رسائل البنوك»، أو اربط مرسل بحساب، أو ابحث عن مرسلين جدد.")
            PrimaryButton("رسائل البنوك", onClick = onTeach, modifier = Modifier.fillMaxWidth())
            SecondaryButton("إضافة مرسل إلى حساب", onClick = onAccounts, modifier = Modifier.fillMaxWidth())
            SecondaryButton("البحث عن مرسلين جدد", onClick = onDiscovery, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DiscoveryResultsCard(preview: ScanPreview, onAccounts: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text("مرسلون ماليون غير مسجلين", style = FinancialTypography.merchant)
            if (preview.discoveredSenders.isEmpty()) Text("لا توجد رسائل من مرسلين جدد ضمن الفترة.")
            preview.discoveredSenders.forEach { sender ->
                Text("${sender.sender} • ${sender.messageCount} رسالة")
                Text(
                    "آخر رسالة: ${java.time.Instant.ofEpochMilli(sender.latestTimestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}",
                    style = FinancialTypography.metadata,
                )
                SecondaryButton(
                    "ربط «${sender.sender}» بحساب",
                    onClick = {
                        ImportSessionHints.setPreferredSender(sender.sender)
                        onAccounts()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Intentionally no automatic import button — discovery must
            // remain a separate, manual flow.
        }
    }
}
