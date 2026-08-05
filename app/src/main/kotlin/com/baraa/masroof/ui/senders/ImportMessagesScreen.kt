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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.repository.SmsImportResult
import com.baraa.masroof.sms.SmsImportRange
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SectionHeader
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * SMS import flow:
 *  - Calendar-based date range selection.
 *  - Permission gate inline: when READ_SMS is missing, only the permission UI is shown.
 *  - Atomic import through [com.baraa.masroof.data.repository.SmsImportOrchestrator].
 *  - Completion summary listing actual linked counts and per-account balance impacts.
 */
@Composable
fun ImportMessagesScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var quickId by remember { mutableStateOf(SmsImportRange.QUICK_MONTH_START) }
    var customFrom by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var customTo by remember { mutableStateOf(today) }
    var permissionGranted by remember { mutableStateOf(snapshotReadSms(context)) }
    var permissionPermanentlyDenied by remember {
        mutableStateOf(!permissionGranted && (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == false)
    }
    var scanning by remember { mutableStateOf(false) }
    var scanStage by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SmsImportResult?>(null) }

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
            permissionPermanentlyDenied = activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == false
        }
    }

    androidx.compose.material3.Scaffold(topBar = { MasroofTopAppBar(title = "استيراد رسائل البنك", onBack = onClose) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            PermissionStatePanel(granted = permissionGranted, permanentlyDenied = permissionPermanentlyDenied, onRequest = { launcher.launch(Manifest.permission.READ_SMS) }, onOpenSettings = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            })

            if (!permissionGranted) {
                Text("سيقرأ مصروف الرسائل الموجودة ضمن الفترة المحددة فقط، ولن يرسل أو يحذف أو يعدل أي رسالة.", style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            Text("اختر الفترة التي تريد فحص رسائلها.", style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val currentRangeLabel = remember(quickId, customFrom, customTo, today) { rangeLabel(quickId, customFrom, customTo, today) }
            com.baraa.masroof.ui.theme.ImportSummaryCard(rangeLabel = currentRangeLabel, allowedInstitutionCount = 0)

            Text("خيارات سريعة", style = FinancialTypography.merchant)
            com.baraa.masroof.ui.theme.FilterChipRow(
                chips = listOf(
                    com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_MONTH_START, "من بداية هذا الشهر", selected = quickId == SmsImportRange.QUICK_MONTH_START),
                    com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_LAST_SALARY, "منذ آخر راتب", selected = quickId == SmsImportRange.QUICK_LAST_SALARY),
                    com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_LAST_SEVEN, "آخر 7 أيام", selected = quickId == SmsImportRange.QUICK_LAST_SEVEN),
                    com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_LAST_THIRTY, "آخر 30 يومًا", selected = quickId == SmsImportRange.QUICK_LAST_THIRTY),
                    com.baraa.masroof.ui.theme.FilterChipModel(SmsImportRange.QUICK_CUSTOM, "تحديد فترة", selected = quickId == SmsImportRange.QUICK_CUSTOM),
                ),
                onChipClick = { quickId = it },
            )

            if (quickId == SmsImportRange.QUICK_CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2), modifier = Modifier.fillMaxWidth()) {
                    CalendarDateField(label = "من تاريخ", selected = customFrom, onSelected = { customFrom = it }, isStart = true, rangeEnd = customTo, modifier = Modifier.weight(1f))
                    CalendarDateField(label = "إلى تاريخ", selected = customTo, onSelected = { customTo = it }, isStart = false, rangeStart = customFrom, modifier = Modifier.weight(1f))
                }
                if (customTo.isBefore(customFrom)) {
                    Text("تاريخ النهاية يجب أن يكون بعد البداية.", color = MaterialTheme.colorScheme.error)
                }
            }

            if (scanning) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(scanStage.ifBlank { "جارٍ فحص الرسائل" }, style = FinancialTypography.merchant)
                    SecondaryButton(label = "إلغاء", onClick = { scanning = false })
                }
            } else {
                val range = resolveRange(quickId, today, customFrom, customTo)
                PrimaryButton(label = "فحص الرسائل", enabled = range != null && permissionGranted, onClick = {
                    val resolvedRange = range ?: return@PrimaryButton
                    result = null
                    scanning = true
                    scanStage = "جارٍ فحص الرسائل"
                    scope.launch {
                        runCatching {
                            val trackingStartMs = runCatching { app.financialSetupRepository.load() }.getOrNull()?.trackingStartDate
                            val trackingStartDate = trackingStartMs?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                            scanStage = "جارٍ قراءة الرسائل"
                            val messages = app.smsRepository.loadInbox(resolvedRange)
                            if (messages.isEmpty()) {
                                result = SmsImportResult.Empty.copy(unparsedCount = 0)
                            } else {
                                scanStage = "جارٍ التعرف على البنوك"
                                result = app.importOrchestrator.import(messages, trackingStartDate, permissionGranted = true)
                            }
                        }.onFailure {
                            result = SmsImportResult.Empty.copy(unparsedCount = 0)
                        }
                        scanning = false
                    }
                })
            }

            result?.let { SummaryCard(it) }
        }
    }
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
private fun SummaryCard(r: SmsImportResult) {
    SectionHeader("اكتمل استيراد الرسائل")
    Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            BulletRow("تم فحص", "${r.scannedSmsCount} رسالة")
            BulletRow("تم التعرف على", "${r.recognizedFinancialSmsCount} عملية مالية")
            BulletRow("تم استيراد", "${r.importedTransactionsCount} عملية")
            BulletRow("تم ربط", "${r.linkedTransactionsCount} عملية بالحسابات")
            BulletRow("تحتاج مراجعة", "${r.needsReviewCount} عملية")
            BulletRow("عمليات مكررة", "${r.duplicateCount}")
            BulletRow("رسائل تعذر تحليلها", "${r.unparsedCount}")
            BulletRow("عمليات قبل تاريخ المتابعة", "${r.beforeTrackingStartCount}")
            BulletRow("تم تحديث", "${r.affectedAccountIds.size} حساب")
        }
    }
    if (r.affectedAccounts.isNotEmpty()) {
        SectionHeader("الحسابات المحدّثة")
        r.affectedAccounts.forEach { AffectedAccountCard(it) }
    }
}

@Composable
private fun AffectedAccountCard(a: SmsImportResult.AffectedAccountSummary) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Text(a.accountName, style = FinancialTypography.merchant)
            Text("الرصيد الافتتاحي: ${a.openingBalance.toPlainString()} ر.س", style = FinancialTypography.metadata)
            Text("عمليات دائنة: ${a.totalCredits.toPlainString()}", style = FinancialTypography.metadata)
            Text("عمليات مدينة: ${a.totalDebits.toPlainString()}", style = FinancialTypography.metadata)
            Text("الرصيد بعد الاستيراد: ${a.calculatedBalance.toPlainString()}", style = FinancialTypography.merchant, color = MaterialTheme.colorScheme.primary)
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

private fun rangeLabel(quickId: String, customFrom: LocalDate, customTo: LocalDate, today: LocalDate): String = when (quickId) {
    SmsImportRange.QUICK_MONTH_START -> SmsImportRange.default(today).label
    SmsImportRange.QUICK_LAST_SALARY -> SmsImportRange.sinceLastSalary(today).label
    SmsImportRange.QUICK_LAST_SEVEN -> SmsImportRange.lastDays(today, 7).label
    SmsImportRange.QUICK_LAST_THIRTY -> SmsImportRange.lastDays(today, 30).label
    SmsImportRange.QUICK_CUSTOM -> humanDateRange(customFrom, customTo)
    else -> "حدد نطاقًا"
}

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
