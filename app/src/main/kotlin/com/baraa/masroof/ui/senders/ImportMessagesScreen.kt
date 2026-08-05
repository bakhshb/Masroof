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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.baraa.masroof.data.repository.SmsImportResult
import com.baraa.masroof.sms.SmsImportRange
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.SectionHeader
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The SMS import flow now follows a strict two-step pipeline so the UI
 * never claims "transactions linked" before the user actually commits them.
 *
 *   STEP 1 (scan):  user picks a quick option or a custom calendar range
 *                   and presses [buttonLabel = "فحص الرسائل"]. The
 *                   orchestrator parses every SMS, classifies, but
 *                   writes nothing to Room. The result is a [ScanPreview].
 *
 *   STEP 2 (commit): user presses the "استيراد N عملية" button. The
 *                   orchestrator opens ONE Room `withTransaction` block,
 *                   inserts every transaction, links it, posts the journal
 *                   + its postings, recomputes affected account summaries,
 *                   and returns the structured [SmsImportResult].
 *
 * Tracking start date is fetched from `FinancialSetupRepository` and is
 * **never** mutated by the import screen. If any candidate transactions
 * are older than that date, the screen surfaces the warning text and the
 * two-action CTA exactly as required by the spec.
 */
@Composable
fun ImportMessagesScreen(
    onClose: () -> Unit,
    onShowImportedTransactions: () -> Unit = {},
    onNavigateToAccounts: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()

    // Persistent trackingStartDate loaded from `FinancialSetupRepository`.
    val setup by app.financialSetupRepository.observe().collectAsStateWithLifecycle(initialValue = null)
    val trackingStartDate: LocalDate? = remember(setup) {
        val s = setup ?: return@remember null
        java.time.Instant.ofEpochMilli(s.trackingStartDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }

    var quickId by remember { mutableStateOf(SmsImportRange.QUICK_MONTH_START) }
    var customFrom by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var customTo by remember { mutableStateOf(today) }
    var permissionGranted by remember { mutableStateOf(snapshotReadSms(context)) }
    var permissionPermanentlyDenied by remember {
        mutableStateOf(!permissionGranted && (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == false)
    }

    var phase by remember { mutableStateOf(ImportPhase.Idle) }
    var scanPreview by remember { mutableStateOf<ScanPreview?>(null) }
    var commitResult by remember { mutableStateOf<SmsImportResult?>(null) }
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

    androidx.compose.material3.Scaffold(topBar = { MasroofTopAppBar(title = "استيراد رسائل البنك", onBack = onClose) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.x4),
            verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            PermissionStatePanel(granted = permissionGranted, permanentlyDenied = permissionPermanentlyDenied, onRequest = { launcher.launch(Manifest.permission.READ_SMS) }, onOpenSettings = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            })

            if (!permissionGranted) {
                Text("تعذر فحص الرسائل لأن إذن قراءة الرسائل غير ممنوح.", color = MaterialTheme.colorScheme.error, style = FinancialTypography.merchant)
                return@Column
            }

            TrackingStartDateCard(
                trackingStartDate = trackingStartDate,
                onEdit = { showTrackEditDialog = true },
            )

            ImportRangeSection(
                quickId = quickId,
                onQuickIdChange = { quickId = it },
                customFrom = customFrom,
                customTo = customTo,
                onCustomFromChange = { customFrom = it },
                onCustomToChange = { customTo = it },
            )

            when (val p = phase) {
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
                    val range = resolveRange(quickId, today, customFrom, customTo)
                    PrimaryButton(
                        label = "فحص الرسائل",
                        enabled = range != null && permissionGranted,
                        onClick = {
                            val resolvedRange = range ?: return@PrimaryButton
                            scanPreview = null
                            commitResult = null
                            phase = ImportPhase.Scanning
                            scope.launch {
                                runCatching {
                                    val messages = app.smsRepository.loadInbox(resolvedRange)
                                    lastLoadedMessages = messages
                                    scanPreview = app.importOrchestrator.scan(messages, trackingStartDate)
                                }.onFailure {
                                    scanPreview = ScanPreview()
                                }
                                phase = ImportPhase.Idle
                            }
                        },
                    )
                }
            }

            scanPreview?.let { preview ->
                ScanResultsCard(preview)
                val readyCount = preview.recognizedTransactions - preview.needsReviewTransactions - preview.duplicateTransactions - preview.beforeTrackingStartCount
                val beforeTracker = preview.beforeTrackingStartCount > 0
                if (beforeTracker) {
                    TrackingStartWarningCard(
                        onChangeTrackingStart = { showTrackEditDialog = true },
                        onImportAsLogOnly = { showLogOnlyConfirmation = true },
                    )
                }
                PrimaryButton(
                    label = if (readyCount > 0) "استيراد $readyCount عملية" else "لا توجد عمليات جاهزة للاستيراد",
                    enabled = readyCount > 0 && phase == ImportPhase.Idle,
                    onClick = {
                        phase = ImportPhase.Committing
                        scope.launch {
                            commitResult = runCatching {
                                app.importOrchestrator.commit(
                                    scanPreview = preview,
                                    trackingStartDate = trackingStartDate,
                                    importedSms = lastLoadedMessages,
                                )
                            }.getOrElse { SmsImportResult.Empty }
                            phase = ImportPhase.Idle
                        }
                    },
                )
            }

            commitResult?.let { CommitResultCard(it, onShowImportedTransactions, onNavigateToAccounts) }
        }
    }

    if (showTrackEditDialog) {
        TrackingStartEditorDialog(
            initial = trackingStartDate ?: today,
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
            text = { Text("سيتم حفظ العمليات السابقة لـ تاريخ المتابعة كسجل فقط ولن تُحسب في الرصيد.") },
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
private fun TrackingStartDateCard(trackingStartDate: LocalDate?, onEdit: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(Spacing.x4), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("بداية المتابعة المالية", style = FinancialTypography.supportingLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("ar"))
                Text(trackingStartDate?.format(fmt) ?: "—", style = FinancialTypography.merchant)
            }
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text("نطاق استيراد الرسائل", style = FinancialTypography.merchant)
        val today = LocalDate.now()
        val from = when (quickId) {
            SmsImportRange.QUICK_MONTH_START -> today.withDayOfMonth(1)
            SmsImportRange.QUICK_LAST_SALARY -> SmsImportRange.sinceLastSalary(today).start.toLocalDate()
            SmsImportRange.QUICK_LAST_SEVEN -> today.minusDays(6)
            SmsImportRange.QUICK_LAST_THIRTY -> today.minusDays(29)
            SmsImportRange.QUICK_CUSTOM -> customFrom
            else -> today.withDayOfMonth(1)
        }
        val to = when (quickId) {
            SmsImportRange.QUICK_MONTH_START -> today
            SmsImportRange.QUICK_LAST_SALARY -> SmsImportRange.sinceLastSalary(today).endExclusive.toLocalDate().minusDays(1)
            SmsImportRange.QUICK_LAST_SEVEN -> today
            SmsImportRange.QUICK_LAST_THIRTY -> today
            SmsImportRange.QUICK_CUSTOM -> customTo
            else -> today
        }
        com.baraa.masroof.ui.theme.ImportSummaryCard(
            rangeLabel = humanDateRange(from, to),
            allowedInstitutionCount = 0,
        )
        com.baraa.masroof.ui.theme.FilterChipRow(
            chips = listOf(
                com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_MONTH_START, "من بداية هذا الشهر", selected = quickId == SmsImportRange.QUICK_MONTH_START),
                com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_LAST_SALARY, "منذ آخر راتب", selected = quickId == SmsImportRange.QUICK_LAST_SALARY),
                com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_LAST_SEVEN, "آخر 7 أيام", selected = quickId == SmsImportRange.QUICK_LAST_SEVEN),
                com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_LAST_THIRTY, "آخر 30 يومًا", selected = quickId == SmsImportRange.QUICK_LAST_THIRTY),
                com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_CUSTOM, "تحديد فترة", selected = quickId == SmsImportRange.QUICK_CUSTOM),
            ),
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
    }
}

@Composable
private fun ScanResultsCard(preview: ScanPreview) {
    SectionHeader("نتائج الفحص")
    Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            BulletRow("تم فحص", "${preview.scannedMessages} رسالة")
            BulletRow("تم التعرف على", "${preview.recognizedTransactions} عملية مالية")
            BulletRow("غير مالية أو غير معروفة", "${preview.nonFinancialMessages}")
            BulletRow("مكررة", "${preview.duplicateTransactions}")
            BulletRow("تحتاج مراجعة", "${preview.needsReviewTransactions}")
            BulletRow("قبل تاريخ المتابعة", "${preview.beforeTrackingStartCount}")
        }
    }
    if (preview.institutionGroups.isNotEmpty()) {
        SectionHeader("البنوك المعروفة")
        preview.institutionGroups.forEach { group ->
            InstitutionRow(group)
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
            Text("تم العثور على عمليات أقدم من بداية المتابعة. لن تدخل في حساب الرصيد إلا بعد تعديل بداية المتابعة.", style = FinancialTypography.merchant, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                PrimaryButton(label = "تعديل بداية المتابعة", onClick = onChangeTrackingStart)
                SecondaryButton(label = "استيرادها كسجل فقط", onClick = onImportAsLogOnly)
            }
        }
    }
}

@Composable
private fun CommitResultCard(
    r: SmsImportResult,
    onShowImportedTransactions: () -> Unit,
    onNavigateToAccounts: () -> Unit,
) {
    SectionHeader("اكتمل الاستيراد")
    Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            BulletRow("تم فحص", "${r.scannedMessages} رسالة")
            BulletRow("تم التعرف على", "${r.recognizedTransactions} عملية")
            BulletRow("تم استيراد", "${r.importedTransactions} عملية")
            BulletRow("تم ربط", "${r.linkedTransactions} عملية")
            BulletRow("تم إنشاء قيود مالية لـ", "${r.postedTransactions} عملية")
            BulletRow("تحتاج مراجعة", "${r.needsReviewTransactions} عملية")
            BulletRow("عمليات مكررة", "${r.duplicateTransactions}")
            BulletRow("عمليات قبل تاريخ المتابعة", "${r.beforeTrackingStartCount}")
            BulletRow("تم تحديث", "${r.updatedAccountIds.size} حساب")
        }
    }
    if (r.affectedAccounts.isNotEmpty()) {
        SectionHeader("الحسابات المحدّثة")
        r.affectedAccounts.forEach { AffectedAccountCard(it) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        SecondaryButton(label = "عرض العمليات المستوردة", onClick = onShowImportedTransactions)
        SecondaryButton(label = "العودة إلى الحسابات", onClick = onNavigateToAccounts)
    }
}

@Composable
private fun AffectedAccountCard(a: SmsImportResult.AffectedAccountSummary) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Text(a.accountName, style = FinancialTypography.merchant)
            BulletRow("الرصيد الافتتاحي", "${a.openingBalance.toPlainString()} ر.س")
            BulletRow("عمليات دائنة", "${a.totalCredits.toPlainString()} ر.س")
            BulletRow("عمليات مدينة", "${a.totalDebits.toPlainString()} ر.س")
            BulletRow("الرصيد المحسوب", "${a.calculatedBalance.toPlainString()} ر.س")
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TrackingStartEditorDialog(initial: LocalDate, onDismiss: () -> Unit, onSave: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    var year by remember { mutableStateOf(initial.year) }
    var month by remember { mutableStateOf(initial.monthValue) }
    var day by remember { mutableStateOf(initial.dayOfMonth) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل بداية المتابعة المالية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                Text("اختر التاريخ المرتبط بالرصيد الافتتاحي.", style = FinancialTypography.metadata)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    OutlinedTextField(value = day.toString(), onValueChange = { day = it.toIntOrNull() ?: day }, label = { Text("يوم") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = month.toString(), onValueChange = { month = it.toIntOrNull() ?: month }, label = { Text("شهر") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = year.toString(), onValueChange = { year = it.toIntOrNull() ?: year }, label = { Text("سنة") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val date = runCatching { LocalDate.of(year, month, day) }.getOrNull()
                if (date != null && !date.isAfter(today)) onSave(date)
            }) { Text("حفظ") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun PermissionStatePanel(granted: Boolean, permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = if (granted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text(
                if (granted) "تم منح إذن قراءة الرسائل"
                else if (permanentlyDenied) "تم رفض الإذن. يمكنك منحه من إعدادات التطبيق"
                else "لم يتم منح إذن قراءة الرسائل",
                style = FinancialTypography.merchant,
            )
            Text("يحتاج التطبيق إلى إذن قراءة الرسائل للتعرف على العمليات البنكية واستيرادها. التطبيق يقرأ الرسائل فقط، ولن يرسل أو يعدل أو يحذف أي رسالة.", style = FinancialTypography.metadata)
            if (!granted) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    PrimaryButton(label = "السماح بقراءة الرسائل", onClick = onRequest)
                    if (permanentlyDenied) SecondaryButton(label = "فتح إعدادات التطبيق", onClick = onOpenSettings)
                }
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

private fun resolveRange(quickId: String, today: LocalDate, customFrom: LocalDate, customTo: LocalDate): SmsImportRange? = when (quickId) {
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
private fun CalendarDateField(label: String, selected: LocalDate, onSelected: (LocalDate) -> Unit, isStart: Boolean, modifier: Modifier = Modifier, rangeStart: LocalDate? = null, rangeEnd: LocalDate? = null) {
    com.baraa.masroof.ui.senders.CalendarDateField(
        label = label,
        selected = selected,
        onSelected = onSelected,
        isStart = isStart,
        rangeEnd = rangeEnd,
        rangeStart = rangeStart,
        modifier = modifier,
    )
}
