package com.baraa.masroof.ui.senders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.repository.SenderProfile
import com.baraa.masroof.sms.SenderNormalizer
import com.baraa.masroof.sms.SmsImportRange
import com.baraa.masroof.sms.SmsInboxLoadResult
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Sender-first entry point. Pattern discovery and approval live in sender detail. */
@Composable
fun BankMessagesScreen(
    onBack: () -> Unit,
    onSenderClick: (Long) -> Unit,
    onReturnToImport: (() -> Unit)? = null,
) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val profiles by app.senderProfileRepository.observeActive()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val unknown by app.messagePatternRepository.observeUnknown()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var stats by remember { mutableStateOf<Map<Long, SenderStats>>(emptyMap()) }
    var status by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(profiles) {
        stats = profiles.associate { profile ->
            val variants = app.messagePatternRepository.getForSender(profile.id)
            profile.id to SenderStats(
                familyCount = variants.map { it.family?.id ?: -it.definition.id }.distinct().size,
                messageCount = variants.sumOf { it.definition.exampleCount },
                approvedFamilyCount = variants.filter {
                    it.definition.status == MessagePatternStatus.APPROVED && it.definition.isActive
                }.map { it.family?.id ?: -it.definition.id }.distinct().size,
            )
        }
    }

    Scaffold(topBar = { MasroofTopAppBar("رسائل البنوك", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.x4)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            Text("المرسلون", style = FinancialTypography.merchant)
            onReturnToImport?.let { callback ->
                SecondaryButton("العودة إلى الاستيراد", onClick = callback, modifier = Modifier.fillMaxWidth())
            }
            Text(
                "${unknown.size} صيغة جديدة تحتاج مراجعة",
                style = FinancialTypography.metadata,
                color = MaterialTheme.colorScheme.tertiary,
            )
            profiles.forEach { profile ->
                val summary = stats[profile.id] ?: SenderStats()
                Row(
                    Modifier.fillMaxWidth().clickable { onSenderClick(profile.id) }
                        .padding(vertical = Spacing.x2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
                ) {
                    Icon(Icons.Filled.AccountBalance, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(profile.displayInstitutionName ?: profile.displaySender,
                            style = FinancialTypography.merchant)
                        Text(
                            "${summary.approvedFamilyCount} نمط معتمد · ${summary.familyCount} نوع · ${summary.messageCount} رسالة",
                            style = FinancialTypography.metadata,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "فتح")
                }
            }
            Text("إضافة مرسل للتدريب", style = FinancialTypography.merchant)
            TrainSenderSection(
                registeredKeys = profiles.map { it.normalizedSenderKey }.toSet(),
                onSaved = { profile ->
                    status = "تمت إضافة المرسل. اكتشف الصيغ ثم اعتمد ما يناسبه."
                    onSenderClick(profile.id)
                },
            )
            status?.let { Text(it, style = FinancialTypography.metadata) }
        }
    }
}

private data class SenderStats(
    val familyCount: Int = 0,
    val messageCount: Int = 0,
    val approvedFamilyCount: Int = 0,
)

@Composable
private fun TrainSenderSection(
    registeredKeys: Set<String>,
    onSaved: (SenderProfile) -> Unit,
) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    var rangeDays by remember { mutableStateOf(30) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var senders by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7 to "7 أيام", 30 to "30 يومًا", 90 to "90 يومًا").forEach { (days, label) ->
            FilterChip(rangeDays == days, { rangeDays = days }, label = { Text(label) })
        }
    }
    SecondaryButton(
        if (loading) "جارٍ قراءة الرسائل…" else "عرض المرسلين",
        enabled = !loading,
        onClick = {
            loading = true
            scope.launch {
                val result = app.smsRepository.loadInboxResult(
                    SmsImportRange.lastDays(LocalDate.now(), rangeDays),
                )
                senders = when (result) {
                    is SmsInboxLoadResult.Success -> {
                        error = null
                        result.messages.mapNotNull { it.sender?.trim()?.takeIf(String::isNotBlank) }
                            .groupingBy { it }.eachCount().entries
                            .filter { SenderNormalizer.normalize(it.key) !in registeredKeys }
                            .sortedByDescending { it.value }.map { it.key to it.value }
                    }
                    is SmsInboxLoadResult.PermissionDenied -> {
                        error = result.messageAr; emptyList()
                    }
                    is SmsInboxLoadResult.Failed -> {
                        error = "تعذر قراءة الرسائل"; emptyList()
                    }
                }
                loading = false
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = FinancialTypography.metadata) }
    senders.take(40).forEach { (sender, count) ->
        PrimaryButton(
            "$sender · $count رسالة",
            onClick = { scope.launch { onSaved(app.senderProfileRepository.upsertFromSmsSender(sender)) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
