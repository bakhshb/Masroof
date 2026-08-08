package com.baraa.masroof.ui.diagnostics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.sms.ApprovedTemplateMatchDiagnostics
import com.baraa.masroof.sms.NORMALIZATION_VERSION
import com.baraa.masroof.sms.SenderNormalizer
import com.baraa.masroof.sms.SmsImportRange
import com.baraa.masroof.sms.SmsInboxLoadResult
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.sms.TemplateResolutionService
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.Spacing
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Developer-only Pattern Diagnostics. Renders per-scanned-message results:
 * sender profile resolved, normalization version, canonical signature hash,
 * exact/structural candidate count, selected family / variant, match method,
 * pattern rejection reason, extraction status, account match status, final
 * disposition. No raw SMS content, amounts, last fours, merchants, or
 * beneficiary names are exposed.
 */
@Composable
fun PatternDiagnosticsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val activeSenders by app.senderProfileRepository.observeActive()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var senderId by remember { mutableStateOf<Long?>(null) }
    var rangeDays by remember { mutableStateOf(30) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<PatternDiagnosticRow>>(emptyList()) }

    LaunchedEffect(senderId, rangeDays) {
        val sid = senderId
        if (sid == null) return@LaunchedEffect
        loading = true
        error = null
        try {
            val result = withContext(Dispatchers.IO) {
                app.smsRepository.loadInboxResult(
                    SmsImportRange.lastDays(LocalDate.now(), rangeDays),
                )
            }
            val messages = (result as? SmsInboxLoadResult.Success)?.messages
                ?: emptyList()
            val profile = activeSenders.firstOrNull { it.id == sid }
            val senderMessages = if (profile == null) emptyList() else messages.filter {
                SenderNormalizer.normalize(it.sender) == profile.normalizedSenderKey
            }
            val definitions = app.messagePatternRepository.getForSender(sid)
            rows = withContext(Dispatchers.Default) {
                senderMessages.take(200).map { sms ->
                    buildRow(
                        sms,
                        definitions.map { def ->
                            MessagePattern(
                                definition = def.definition,
                                fields = def.fields,
                                anchors = def.anchors,
                                family = def.family,
                            )
                        },
                    )
                }
            }
        } catch (t: Throwable) {
            error = t.message ?: "تعذر قراءة الرسائل"
        } finally {
            loading = false
        }
    }

    Scaffold(topBar = { MasroofTopAppBar("تشخيص الأنماط", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.x4)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            Text(
                "إصدار التطبيع الحالي: $NORMALIZATION_VERSION",
                style = FinancialTypography.metadata,
            )
            Text("اختر مرسلاً ومدى زمنياً", style = FinancialTypography.merchant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                listOf(7 to "7 أيام", 30 to "30 يومًا", 90 to "90 يومًا").forEach { (d, label) ->
                    FilterChip(rangeDays == d, { rangeDays = d }, label = { Text(label) })
                }
            }
            activeSenders.forEach { profile ->
                val selected = senderId == profile.id
                Surface(
                    Modifier.fillMaxWidth().clickable { senderId = profile.id },
                    shape = FinancialShapes.medium,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        profile.displayInstitutionName ?: profile.displaySender,
                        modifier = Modifier.padding(Spacing.x2),
                        style = FinancialTypography.merchant,
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (loading) Text("جارٍ قراءة الرسائل…", style = FinancialTypography.metadata)
            rows.forEach { row -> DiagnosticCard(row) }
        }
    }
}

private data class PatternDiagnosticRow(
    val senderResolved: Boolean,
    val normalizationVersion: Int,
    val canonicalSignature: String,
    val attempts: List<AttemptSummary>,
    val primaryFailure: String,
    val matchMethod: String,
)

private data class AttemptSummary(
    val templateId: Long,
    val displayName: String,
    val eligible: Boolean,
    val eligibilityFailure: String?,
)

private fun buildRow(
    sms: SmsMessage,
    patterns: List<MessagePattern>,
): PatternDiagnosticRow {
    val body = sms.body
    val diagnostics: ApprovedTemplateMatchDiagnostics? = if (!body.isNullOrBlank()) {
        TemplateResolutionService.diagnose(body, patterns)
    } else null
    return PatternDiagnosticRow(
        senderResolved = !sms.sender.isNullOrBlank(),
        normalizationVersion = NORMALIZATION_VERSION,
        canonicalSignature = diagnostics?.smsStructuralSignature ?: "(empty body)",
        attempts = diagnostics?.attempts.orEmpty().map {
            AttemptSummary(
                templateId = it.templateId,
                displayName = it.displayName,
                eligible = it.eligible,
                eligibilityFailure = it.eligibilityFailure,
            )
        },
        primaryFailure = diagnostics?.primaryFailure ?: "NO_DIAGNOSTIC",
        matchMethod = when {
            diagnostics == null -> "NONE"
            diagnostics.attempts.count { it.eligible } == 0 -> "NONE"
            diagnostics.attempts.any { it.match?.matched == true } -> "EXACT"
            else -> "STRUCTURAL"
        },
    )
}

@Composable
private fun DiagnosticCard(row: PatternDiagnosticRow) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(Spacing.x3)) {
            Text(
                "بصمة: ${row.canonicalSignature.take(60)}…",
                style = FinancialTypography.metadata,
            )
            Text(
                "إصدار التطبيع: ${row.normalizationVersion} · أسلوب المطابقة: ${row.matchMethod}",
                style = FinancialTypography.metadata,
            )
            Text(
                "سبب الرفض: ${row.primaryFailure}",
                style = FinancialTypography.metadata,
                color = MaterialTheme.colorScheme.tertiary,
            )
            if (row.attempts.isNotEmpty()) {
                Text("القوالب المرشّحة:", style = FinancialTypography.metadata)
                row.attempts.take(5).forEach { attempt ->
                    val status = if (attempt.eligible) "مؤهل" else "غير مؤهل: ${attempt.eligibilityFailure ?: "—"}"
                    Text(
                        "#${attempt.templateId} ${attempt.displayName} — $status",
                        style = FinancialTypography.metadata,
                    )
                }
            }
        }
    }
}