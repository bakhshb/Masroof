package com.baraa.masroof.ui.senders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.repository.ImportItemStatus
import com.baraa.masroof.data.repository.ImportPreview
import com.baraa.masroof.sms.CustomValidationResult
import com.baraa.masroof.sms.SmsImportRange
import com.baraa.masroof.ui.theme.AttentionBanner
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.ImportSummaryCard
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.SectionHeader
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun ImportMessagesScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var quickId by remember { mutableStateOf(SmsImportRange.QUICK_MONTH_START) }
    var customFrom by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var customTo by remember { mutableStateOf(today) }
    var customError by remember { mutableStateOf<String?>(null) }
    var setup by remember { mutableStateOf<com.baraa.masroof.data.repository.FinancialSetup?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanStage by remember { mutableStateOf("جارٍ فحص الرسائل") }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var noMessages by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<DoneSummary?>(null) }

    LaunchedEffect(Unit) { setup = runCatching { app.financialSetupRepository.load() }.getOrNull() }

    val trackingStartLabel by remember {
        derivedStateOf {
            val start = setup?.trackingStartDate ?: return@derivedStateOf null
            val date = java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            "بداية المتابعة: $date"
        }
    }

    androidx.compose.material3.Scaffold(topBar = { MasroofTopAppBar(title = "استيراد رسائل البنك", onBack = onClose) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            Text("يقرأ مصروف الرسائل في الفترة المحددة فقط، ولن يرسل أو يحذف أي رسالة.", style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
            trackingStartLabel?.let { Text(it, style = FinancialTypography.badge, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            val rangeLabel = remember(quickId, customFrom, customTo) {
                when (quickId) {
                    SmsImportRange.QUICK_MONTH_START -> SmsImportRange.default(today).label
                    SmsImportRange.QUICK_LAST_SALARY -> SmsImportRange.sinceLastSalary(today).label
                    SmsImportRange.QUICK_LAST_SEVEN -> SmsImportRange.lastDays(today, 7).label
                    SmsImportRange.QUICK_LAST_THIRTY -> SmsImportRange.lastDays(today, 30).label
                    SmsImportRange.QUICK_CUSTOM -> "${customFrom} → ${customTo}"
                    else -> "حدد نطاقًا"
                }
            }
            ImportSummaryCard(rangeLabel = rangeLabel, allowedInstitutionCount = 0)

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
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    DateInput(label = "من تاريخ", value = customFrom.toString(), onValueChange = { customFrom = parseDate(it) ?: customFrom; customError = null })
                    DateInput(label = "إلى تاريخ", value = customTo.toString(), onValueChange = { customTo = parseDate(it) ?: customTo; customError = null })
                }
                val validation = SmsImportRange.validateCustom(customFrom, customTo, today)
                if (validation != CustomValidationResult.Valid) {
                    Text(
                        when (validation) {
                            CustomValidationResult.Reversed -> "تاريخ البداية يجب أن يكون قبل النهاية"
                            CustomValidationResult.Future -> "تاريخ النهاية لا يمكن أن يكون في المستقبل"
                            CustomValidationResult.Missing -> "اكتب التاريخين"
                            else -> ""
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (scanning) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(scanStage, style = FinancialTypography.merchant)
                    SecondaryButton(label = "إلغاء", onClick = {
                        scanJob?.cancel()
                        scanning = false
                    })
                }
            } else {
                val range = remember(quickId, customFrom, customTo, today) { resolveRange(quickId, today, customFrom, customTo) }
                PrimaryButton(label = "فحص الرسائل", enabled = range != null, onClick = {
                    scanJob = scope.launch {
                        scanning = true
                        scanStage = "جارٍ فحص الرسائل"
                        noMessages = false
                        summary = null
                        val parsedRange = range ?: run { scanning = false; return@launch }
                        val messages = withContext(Dispatchers.IO) { app.smsRepository.loadInbox(parsedRange) }
                        if (messages.isEmpty()) {
                            noMessages = true
                            scanning = false
                            return@launch
                        }
                        scanStage = "جارٍ التعرف على البنوك"
                        val preview = withContext(Dispatchers.IO) { app.importService.preview(messages) }
                        scanStage = "جارٍ مطابقة الحسابات"
                        val trackingStart = setup?.trackingStartDate
                        val done = withContext(Dispatchers.IO) {
                            val counts = groupedByInstitution(preview.items.map { it.sender to it.status })
                            DoneSummary(preview.preview, counts, trackingStart)
                        }
                        scanStage = "جارٍ تحليل العمليات"
                        summary = done
                        scanning = false
                    }
                })
            }

            if (noMessages) EmptyStatus("لا توجد رسائل مالية في الفترة المختارة", "جرّب توسيع نطاق الفحص أو التحقق من إذن قراءة الرسائل.")
            summary?.let { ResultsSection(it) }
        }
    }
}

@Composable
private fun DateInput(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(0.48f))
}

@Composable
private fun EmptyStatus(title: String, body: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(Spacing.x4)) {
            Text(title, style = FinancialTypography.merchant)
            Text(body, style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResultsSection(summary: DoneSummary) {
    SectionHeader("تم فحص ${summary.preview.messagesScanned} رسالة")
    Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            summary.byInstitution.forEach { (institution, counts) ->
                com.baraa.masroof.ui.theme.InstitutionAmountRow(institution = institution, ready = counts.ready, needsReview = counts.review, unparsed = counts.unparsed)
            }
            val unknown = summary.byInstitution["مرسل غير معروف"]
            if (unknown != null && unknown.totalMessages > 0) {
                Spacer(Modifier.height(Spacing.x2))
                AttentionBanner(title = "مرسل غير معروف", description = "حدد المؤسسة مرة واحدة وسيستخدمها مصروف مستقبلًا.", actionLabel = "تحديد البنك", onAction = {})
            }
        }
    }
    SectionHeader("ملخص العملية")
    Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Text("عمليات مرتبطة تلقائيًا: ${summary.preview.parsedSuccessfully}", style = FinancialTypography.metadata)
            Text("تحتاج تأكيدًا: ${summary.byInstitution.values.sumOf { it.review }}", style = FinancialTypography.metadata)
            Text("تحتاج تحديد الحساب: ${summary.byInstitution.values.sumOf { it.unparsed }}", style = FinancialTypography.metadata)
            Text("رسائل تعذر تحليلها: ${summary.preview.unparseable}", style = FinancialTypography.metadata)
            Text("عمليات مكررة محتملة: ${summary.preview.possibleDuplicates}", style = FinancialTypography.metadata)
            if (summary.beforeTrackingCount > 0) {
                Text("عمليات خارج تاريخ المتابعة المالية: ${summary.beforeTrackingCount}", style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun resolveRange(quickId: String, today: LocalDate, customFrom: LocalDate, customTo: LocalDate): SmsImportRange? = when (quickId) {
    SmsImportRange.QUICK_MONTH_START -> SmsImportRange.default(today)
    SmsImportRange.QUICK_LAST_SALARY -> SmsImportRange.sinceLastSalary(today)
    SmsImportRange.QUICK_LAST_SEVEN -> SmsImportRange.lastDays(today, 7)
    SmsImportRange.QUICK_LAST_THIRTY -> SmsImportRange.lastDays(today, 30)
    SmsImportRange.QUICK_CUSTOM -> when (SmsImportRange.validateCustom(customFrom, customTo, today)) {
        CustomValidationResult.Valid -> SmsImportRange.custom(customFrom, customTo)
        else -> null
    }
    else -> null
}

private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

private data class InstitutionCounts(val totalMessages: Int, val ready: Int, val review: Int, val unparsed: Int)

private data class DoneSummary(
    val preview: ImportPreview,
    val byInstitution: Map<String, InstitutionCounts>,
    val trackingStartCount: Long?,
) {
    val beforeTrackingCount: Int get() = trackingStartCount?.toInt() ?: 0
}

private fun groupedByInstitution(pairs: List<Pair<String?, ImportItemStatus>>): Map<String, InstitutionCounts> {
    val grouped = mutableMapOf<String, MutableList<ImportItemStatus>>()
    pairs.forEach { (sender, status) ->
        val key = sender ?: "مرسل غير معروف"
        grouped.getOrPut(key) { mutableListOf() }.add(status)
    }
    return grouped.map { (key, statuses) ->
        key to InstitutionCounts(
            totalMessages = statuses.size,
            ready = statuses.count { it == ImportItemStatus.NEW },
            review = statuses.count { it == ImportItemStatus.POSSIBLE_DUPLICATE },
            unparsed = statuses.count { it == ImportItemStatus.EXACT_DUPLICATE },
        )
    }.toMap()
}
